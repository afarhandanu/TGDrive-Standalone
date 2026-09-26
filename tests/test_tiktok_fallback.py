"""Check TikTok failover and short-link handling without network access."""
import importlib
import json
import sys
import tempfile
import types
import unittest
from pathlib import Path
from unittest.mock import patch

PYTHON = Path(__file__).resolve().parents[1] / 'app/src/main/python'
sys.path.insert(0, str(PYTHON))


class DownloadError(Exception):
    pass


class UnsupportedError(Exception):
    pass


class YoutubeDL:
    last_options = None
    all_options = []

    def __init__(self, options):
        self.options = options
        YoutubeDL.last_options = options
        YoutubeDL.all_options.append(options)

    def __enter__(self):
        return self

    def __exit__(self, *args):
        pass

    def download(self, urls):
        raise DownloadError('Unexpected response from webpage request')


yt_dlp = types.ModuleType('yt_dlp')
yt_dlp.YoutubeDL = YoutubeDL
yt_dlp.utils = types.SimpleNamespace(DownloadError=DownloadError, UnsupportedError=UnsupportedError)
sys.modules['yt_dlp'] = yt_dlp
downloader = importlib.import_module('downloader')


class Callback:
    def onProgress(self, percent, message):
        pass


class FallbackTests(unittest.TestCase):
    def setUp(self):
        YoutubeDL.all_options = []
        self.work = tempfile.TemporaryDirectory()
        self.addCleanup(self.work.cleanup)
        self.root = Path(self.work.name)

    def invoke(self, url):
        return downloader.download(url, str(self.root), 'best', 0,
                                   False, False, False, '', Callback())

    def test_tiktok_fallback_returns_real_media(self):
        def download(url, root, *args):
            self.assertEqual(url, 'https://vt.tiktok.com/ZSb85NbpU/')
            media = root / 'tiktok' / 'creator' / 'videos' / 'clip.mp4'
            media.parent.mkdir(parents=True)
            media.write_bytes(b'video')

        embed = types.SimpleNamespace(download=lambda *args: (_ for _ in ()).throw(RuntimeError('embed blocked')))
        with patch.dict(sys.modules, {'tiktok_embed': embed, 'tiktok_fallback': types.SimpleNamespace(download=download)}):
            result = json.loads(self.invoke('https://vt.tiktok.com/ZSb85NbpU/'))
        self.assertEqual(len(result), 1)
        self.assertEqual(result[0]['name'], 'clip.mp4')

    def test_tiktok_rotates_browser_user_agent_before_fallback(self):
        def embed_download(url, root, *args):
            media = root / 'tiktok' / 'creator' / 'videos' / 'clip.mp4'
            media.parent.mkdir(parents=True)
            media.write_bytes(b'video')

        with patch.dict(sys.modules, {'tiktok_embed': types.SimpleNamespace(download=embed_download)}):
            self.invoke('https://www.tiktok.com/@creator/video/123')
        self.assertEqual(len(YoutubeDL.all_options), 2)
        first, second = YoutubeDL.all_options
        self.assertNotIn('extractor_args', first)
        self.assertIn('OPR/118.0.0.0', first['http_headers']['User-Agent'])
        self.assertIn('Chrome/140.0.0.0', second['http_headers']['User-Agent'])
        self.assertEqual(first['http_headers']['Referer'], 'https://www.tiktok.com/')
        self.assertEqual(first['extractor_retries'], 2)
        template = first['outtmpl'].replace('\\', '/')
        self.assertIn('/tiktok/%(uploader,uploader_id,channel,creator|unknown).80s/videos/', template)
        self.assertIn('/%(id|unknown).100s/', template)

    def test_fallback_error_reports_all_extractors(self):
        embed = types.SimpleNamespace(download=lambda *args: (_ for _ in ()).throw(RuntimeError('embed blocked')))
        alternate = types.SimpleNamespace(download=lambda *args: (_ for _ in ()).throw(RuntimeError('gallery blocked')))
        with patch.dict(sys.modules, {'tiktok_embed': embed, 'tiktok_fallback': alternate}):
            with self.assertRaisesRegex(RuntimeError, 'retry-UA:.*embed: embed blocked.*gallery-dl: gallery blocked'):
                self.invoke('https://www.tiktok.com/@creator/video/123')

    def test_other_sites_do_not_use_tiktok_fallback(self):
        alternate = types.SimpleNamespace(download=lambda *args: self.fail('unexpected TikTok fallback'))
        with patch.dict(sys.modules, {'tiktok_fallback': alternate}):
            with self.assertRaises(DownloadError):
                self.invoke('https://x.com/creator/status/123')

    def test_short_url_resolves_only_to_tiktok(self):
        fake_config = types.SimpleNamespace(clear=lambda: None, set=lambda *args: None)
        fake_job = types.SimpleNamespace(DownloadJob=lambda *args: None)
        fake_gallery = types.ModuleType('gallery_dl')
        fake_gallery.config, fake_gallery.job = fake_config, fake_job
        with patch.dict(sys.modules, {'gallery_dl': fake_gallery}):
            fallback = importlib.import_module('tiktok_fallback')

        class Response:
            url = 'https://www.tiktok.com/@creator/video/123?lang=id'

            def __enter__(self):
                return self

            def __exit__(self, *args):
                pass

        with patch.object(fallback.urllib.request, 'urlopen', return_value=Response()):
            self.assertEqual(fallback._canonical_url('https://vt.tiktok.com/ZSb85NbpU/'), Response.url)
        self.assertEqual(fallback._canonical_url(Response.url), Response.url)
        with patch.object(fallback.urllib.request, 'urlopen', return_value=Response()):
            Response.url = 'https://example.org/video/123'
            with self.assertRaisesRegex(RuntimeError, 'situs lain'):
                fallback._canonical_url('https://vt.tiktok.com/ZSb85NbpU/')

    def test_short_url_uses_tiktok_redirect_user_agent(self):
        fake_config = types.SimpleNamespace(clear=lambda: None, set=lambda *args: None)
        fake_job = types.SimpleNamespace(DownloadJob=lambda *args: None)
        fake_gallery = types.ModuleType('gallery_dl')
        fake_gallery.config, fake_gallery.job = fake_config, fake_job
        with patch.dict(sys.modules, {'gallery_dl': fake_gallery}):
            fallback = importlib.reload(importlib.import_module('tiktok_fallback'))

        class Response:
            url = 'https://www.tiktok.com/@creator/video/123'
            def __enter__(self): return self
            def __exit__(self, *args): pass

        def open_request(request, timeout=0):
            self.assertEqual(request.get_header('User-agent'), 'facebookexternalhit/1.1')
            return Response()

        with patch.object(fallback.urllib.request, 'urlopen', side_effect=open_request):
            self.assertEqual(fallback._canonical_url('https://vm.tiktok.com/ABC123/'), Response.url)

    def test_gallery_nonzero_status_keeps_downloaded_media(self):
        fake_config = types.SimpleNamespace(clear=lambda: None, set=lambda *args: None)

        class Job:
            def __init__(self, url): self.url = url
            def run(self):
                media = self_root / 'tiktok' / 'creator' / 'videos' / '123.mp4'
                media.parent.mkdir(parents=True)
                media.write_bytes(b'video')
                return 8

        self_root = self.root
        fake_gallery = types.ModuleType('gallery_dl')
        fake_gallery.config = fake_config
        fake_gallery.job = types.SimpleNamespace(DownloadJob=Job)
        with patch.dict(sys.modules, {'gallery_dl': fake_gallery}):
            fallback = importlib.reload(importlib.import_module('tiktok_fallback'))
            files = fallback.download('https://www.tiktok.com/@creator/video/123', self.root,
                                      0, '', Callback())
        self.assertEqual([file.name for file in files], ['123.mp4'])

    def test_gallery_download_uses_direct_video_extractor(self):
        settings = {}
        fake_config = types.SimpleNamespace(clear=lambda: None,
                                            set=lambda key, name, value: settings.__setitem__((key, name), value))

        class Job:
            def __init__(self, url):
                self.url = url

            def run(self):
                media = self_root / 'tiktok' / 'creator' / 'videos' / '123.mp4'
                media.parent.mkdir(parents=True)
                media.write_bytes(b'video')
                return 0

        self_root = self.root
        fake_gallery = types.ModuleType('gallery_dl')
        fake_gallery.config = fake_config
        fake_gallery.job = types.SimpleNamespace(DownloadJob=Job)
        with patch.dict(sys.modules, {'gallery_dl': fake_gallery}):
            fallback = importlib.reload(importlib.import_module('tiktok_fallback'))
            files = fallback.download('https://www.tiktok.com/@creator/video/123', self.root,
                                      0, '', Callback())
        self.assertEqual([file.name for file in files], ['123.mp4'])
        self.assertIs(settings[(('extractor', 'tiktok'), 'videos')], True)
        self.assertIs(settings[(('extractor', 'tiktok', 'posts'), 'ytdl')], False)
        self.assertIn('OPR/118.0.0.0', settings[(('extractor',), 'user-agent')])
        self.assertEqual(settings[(('extractor',), 'headers')]['Referer'], 'https://www.tiktok.com/')
        self.assertEqual(settings[(('extractor', 'tiktok'), 'directory')],
                         ['tiktok', 'creator', 'videos', '123'])

    def test_photo_carousel_combine_emits_slideshow_job(self):
        media_dir = self.root / 'tiktok' / 'creator' / 'videos'
        media_dir.mkdir(parents=True)
        for name in ('post_1.jpg', 'post_2.jpg', 'post_10.jpg'):
            (media_dir / name).write_bytes(b'image')
        (media_dir / 'post.mp3').write_bytes(b'audio' * 10)
        items = downloader._tiktok_items(list(media_dir.iterdir()), self.root, 'combine')
        self.assertEqual(len(items), 1)
        self.assertTrue(items[0]['name'].endswith('.slideshow.mp4'))
        self.assertEqual([Path(x).name for x in items[0]['slideshow_images']],
                         ['post_1.jpg', 'post_2.jpg', 'post_10.jpg'])
        self.assertEqual(Path(items[0]['audio_path']).name, 'post.mp3')

    def test_photo_carousel_separate_keeps_images_and_audio(self):
        media_dir = self.root / 'tiktok' / 'creator' / 'videos'
        media_dir.mkdir(parents=True)
        (media_dir / 'post_1.jpg').write_bytes(b'image')
        (media_dir / 'post_2.jpg').write_bytes(b'image')
        (media_dir / 'post.mp3').write_bytes(b'audio')
        items = downloader._tiktok_items(list(media_dir.iterdir()), self.root, 'separate')
        self.assertEqual({item['name'] for item in items}, {'post_1.jpg', 'post_2.jpg', 'post.mp3'})
        self.assertFalse(any('slideshow_images' in item for item in items))

    def test_android_tempfile_uses_writable_cache_sibling(self):
        temp_root = downloader._configure_temp(self.root)
        self.assertEqual(temp_root, self.root.resolve().parent / '.python-tmp')
        self.assertTrue(temp_root.is_dir())
        with tempfile.NamedTemporaryFile() as handle:
            self.assertEqual(Path(handle.name).parent, temp_root)
            handle.write(b'ok')
            handle.flush()

    def test_tiktok_format_selector_switches_watermark(self):
        no_mark = downloader._format_selector('tiktok', 'best', 'without')
        with_mark = downloader._format_selector('tiktok', 'best', 'with')
        self.assertIn('format_id!^=download_addr', no_mark)
        self.assertIn('format_id^=download_addr', with_mark)
        self.assertNotEqual(no_mark, with_mark)

    def test_gallery_fallback_does_not_return_unwatermarked_video_in_watermark_mode(self):
        settings = {}
        fake_config = types.SimpleNamespace(clear=lambda: None,
                                            set=lambda key, name, value: settings.__setitem__((key, name), value))

        class Job:
            def __init__(self, url):
                self.url = url

            def run(self):
                media = self_root / 'tiktok' / 'creator' / 'videos' / '123.jpg'
                media.parent.mkdir(parents=True, exist_ok=True)
                media.write_bytes(b'image')
                return 0

        self_root = self.root
        fake_gallery = types.ModuleType('gallery_dl')
        fake_gallery.config = fake_config
        fake_gallery.job = types.SimpleNamespace(DownloadJob=Job)
        with patch.dict(sys.modules, {'gallery_dl': fake_gallery}):
            fallback = importlib.reload(importlib.import_module('tiktok_fallback'))
            fallback.download('https://www.tiktok.com/@creator/video/123', self.root,
                              0, '', Callback(), watermark='with')
        self.assertIs(settings[(('extractor', 'tiktok'), 'videos')], False)
        self.assertIs(settings[(('extractor', 'tiktok'), 'photos')], True)


if __name__ == '__main__':
    unittest.main()

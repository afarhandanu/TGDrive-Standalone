"""Regression tests for the canonical Local/Drive website hierarchy."""
import importlib.util
import sys
import tempfile
import types
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PYTHON = ROOT / 'app/src/main/python'
sys.path.insert(0, str(PYTHON))

# gallery_download only needs these names at import time for the pure path helper.
fake_gallery = types.ModuleType('gallery_dl')
fake_gallery.config = types.SimpleNamespace(clear=lambda: None, set=lambda *args: None)
fake_gallery.job = types.SimpleNamespace()
sys.modules.setdefault('gallery_dl', fake_gallery)

spec = importlib.util.spec_from_file_location('gallery_download_layout', PYTHON / 'gallery_download.py')
gallery_download = importlib.util.module_from_spec(spec)
spec.loader.exec_module(gallery_download)

# downloader's module-level yt_dlp import is replaced with the smallest test stub.
fake_ytdlp = types.ModuleType('yt_dlp')
fake_ytdlp.YoutubeDL = object
fake_ytdlp.utils = types.SimpleNamespace(UnsupportedError=Exception, DownloadError=Exception)
sys.modules.setdefault('yt_dlp', fake_ytdlp)
import downloader


class StorageLayoutTests(unittest.TestCase):
    def test_gallery_item_is_grouped_under_id(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            file = root / 'x' / 'alice' / 'posts' / '123456789__01 - 123456789.jpg'
            file.parent.mkdir(parents=True)
            file.write_bytes(b'x')
            target = gallery_download._group_post(file, root)
            self.assertEqual(target.relative_to(root).parts[:4],
                             ('x', 'alice', 'posts', '123456789'))
            self.assertTrue(target.exists())

    def test_gallery_missing_prefix_still_gets_stable_id_folder(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            file = root / 'instagram' / 'alice' / 'stories' / 'unknown__01 - 3992573950408138445.jpg'
            file.parent.mkdir(parents=True)
            file.write_bytes(b'x')
            target = gallery_download._group_post(file, root)
            self.assertEqual(target.parent.name, '3992573950408138445')

    def test_tiktok_photo_post_moves_to_carousel_id_folder(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            folder = root / 'tiktok' / 'creator' / 'videos' / '7689436916940328213'
            folder.mkdir(parents=True)
            image = folder / 'slide_01.jpg'
            audio = folder / 'music.mp3'
            image.write_bytes(b'image')
            audio.write_bytes(b'audio')
            paths = downloader._normalize_tiktok_layout([image, audio], root)
            rels = {p.relative_to(root).as_posix() for p in paths}
            self.assertEqual(rels, {
                'tiktok/creator/carousel/7689436916940328213/slide_01.jpg',
                'tiktok/creator/carousel/7689436916940328213/music.mp3',
            })

    def test_direct_url_fallback_has_item_id(self):
        self.assertEqual(downloader._url_media_id('https://cdn.example.com/media/clip-42.mp4?x=1'), 'clip-42')
        self.assertEqual(downloader._url_media_id('https://cdn.example.com/'), 'unknown')


if __name__ == '__main__':
    unittest.main()

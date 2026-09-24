"""Regression for selected Story IDs resolving to the correct media, not the latest video."""
import importlib.util
import io
import json
import sys
import tempfile
import types
import unittest
from pathlib import Path
from unittest.mock import patch


class Config:
    def clear(self):
        pass

    def set(self, *args):
        pass


class Source:
    def __iter__(self):
        return iter([
            (3, 'https://scontent.cdninstagram.com/photo-one.jpg',
             {'media_id': '3992526560270256149', 'extension': 'jpg'}),
            (3, 'https://scontent.cdninstagram.com/photo-two.jpg',
             {'media_id': '3992538577756620245', 'extension': 'jpg'}),
            (3, 'https://scontent.cdninstagram.com/latest-video.mp4',
             {'media_id': '3992573950408138445', 'extension': 'mp4'}),
        ])


gallery_dl = types.ModuleType('gallery_dl')
gallery_dl.config = Config()
gallery_dl.extractor = types.SimpleNamespace(find=lambda url: Source())
sys.modules['gallery_dl'] = gallery_dl
module_path = Path(__file__).resolve().parents[1] / 'app/src/main/python/story_picker.py'
spec = importlib.util.spec_from_file_location('story_picker', module_path)
picker = importlib.util.module_from_spec(spec)
spec.loader.exec_module(picker)


class Response(io.BytesIO):
    def __init__(self, data, content_type):
        super().__init__(data)
        self.headers = types.SimpleNamespace(
            get_content_type=lambda: content_type,
            get=lambda name: str(len(data)) if name == 'Content-Length' else None)


class Callback:
    def onProgress(self, *args):
        pass


class StoryPickerTests(unittest.TestCase):
    def test_each_selection_downloads_only_its_own_media(self):
        with tempfile.TemporaryDirectory() as temp:
            cookie = Path(temp) / 'cookies.txt'
            cookie.write_text('sessionid=test')
            ids = ['3992526560270256149', '3992538577756620245',
                   '3992573950408138445']
            fetched = []

            def fetch(request, timeout):
                media_url = request.full_url
                fetched.append(media_url)
                return Response(media_url.encode(), 'video/mp4' if media_url.endswith('.mp4') else 'image/jpeg')

            with patch.object(picker.urllib.request, 'urlopen', side_effect=fetch):
                for story_id in ids:
                    result = json.loads(picker.download_story(
                        f'https://www.instagram.com/stories/pak_pkw/{story_id}/',
                        str(Path(temp) / story_id), str(cookie), Callback()))
                    self.assertEqual(len(result), 1)
                    output = Path(result[0]['path'])
                    self.assertIn(story_id, output.name)
                    self.assertEqual(output.read_bytes(), fetched[-1].encode())
            self.assertEqual(len(fetched), 3)
            self.assertTrue(fetched[0].endswith('photo-one.jpg'))
            self.assertTrue(fetched[1].endswith('photo-two.jpg'))
            self.assertTrue(fetched[2].endswith('latest-video.mp4'))

    def test_missing_id_fails_without_saving_another_story(self):
        with tempfile.TemporaryDirectory() as temp:
            cookie = Path(temp) / 'cookies.txt'
            cookie.write_text('sessionid=test')
            with patch.object(picker.urllib.request, 'urlopen') as fetch:
                with self.assertRaisesRegex(RuntimeError, 'tidak ditemukan'):
                    picker.download_story(
                        'https://www.instagram.com/stories/pak_pkw/123456789/',
                        str(Path(temp) / 'job'), str(cookie), Callback())
                fetch.assert_not_called()
            self.assertFalse((Path(temp) / 'job').exists())

    def test_wrong_media_type_does_not_finish_successfully(self):
        with tempfile.TemporaryDirectory() as temp:
            cookie = Path(temp) / 'cookies.txt'
            cookie.write_text('sessionid=test')
            with patch.object(picker.urllib.request, 'urlopen',
                              return_value=Response(b'not a jpg', 'text/html')):
                with self.assertRaisesRegex(RuntimeError, 'tidak mengirimkan file media'):
                    picker.download_story(
                        'https://www.instagram.com/stories/pak_pkw/3992526560270256149/',
                        str(Path(temp) / 'job'), str(cookie), Callback())
            self.assertFalse(list((Path(temp) / 'job').rglob('*jpg')))


if __name__ == '__main__':
    unittest.main()

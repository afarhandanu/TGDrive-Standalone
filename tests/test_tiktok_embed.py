import json
import sys
import unittest
from pathlib import Path

PYTHON = Path(__file__).resolve().parents[1] / 'app/src/main/python'
sys.path.insert(0, str(PYTHON))

import tiktok_embed


class TikTokEmbedTests(unittest.TestCase):
    def test_parse_photo_carousel_and_audio(self):
        payload = {
            '__DEFAULT_SCOPE__': {
                'webapp.video-detail': {
                    'itemInfo': {
                        'itemStruct': {
                            'id': '7689436916940328213',
                            'author': {'uniqueId': 'creator'},
                            'imagePost': {
                                'images': [
                                    {'imageURL': {'urlList': ['https://p16.tiktokcdn.com/a.jpg']}},
                                    {'imageURL': {'urlList': ['https://p16.tiktokcdn.com/b.jpg']}},
                                ]
                            },
                            'music': {'playUrl': 'https://sf.tiktokcdn.com/music.mp3'},
                        }
                    }
                }
            }
        }
        item = tiktok_embed._item_struct(payload, '7689436916940328213')
        self.assertEqual(tiktok_embed._image_urls(item), [
            'https://p16.tiktokcdn.com/a.jpg',
            'https://p16.tiktokcdn.com/b.jpg',
        ])
        self.assertEqual(tiktok_embed._audio_urls(item), ['https://sf.tiktokcdn.com/music.mp3'])
        self.assertEqual(tiktok_embed._safe_username(item, 'https://www.tiktok.com/@x/video/1'), 'creator')

    def test_video_watermark_selection(self):
        item = {
            'id': '123',
            'video': {
                'playAddr': 'https://v.tiktokcdn.com/no-watermark.mp4',
                'downloadAddr': 'https://v.tiktokcdn.com/watermark.mp4',
            },
        }
        no_mark = tiktok_embed._video_urls(item, 'without')
        with_mark = tiktok_embed._video_urls(item, 'with')
        self.assertEqual(no_mark[0], 'https://v.tiktokcdn.com/no-watermark.mp4')
        self.assertEqual(with_mark[0], 'https://v.tiktokcdn.com/watermark.mp4')

    def test_universal_data_script_parser(self):
        payload = {'__DEFAULT_SCOPE__': {'webapp.video-detail': {'itemInfo': {'itemStruct': {'id': '123', 'video': {}}}}}}
        page = '<html><script id="__UNIVERSAL_DATA_FOR_REHYDRATION__" type="application/json">' + json.dumps(payload) + '</script></html>'
        parsed = tiktok_embed._load_universal_data(page)
        self.assertEqual(parsed['__DEFAULT_SCOPE__']['webapp.video-detail']['itemInfo']['itemStruct']['id'], '123')

    def test_sigi_state_parser_and_item_module(self):
        payload = {
            'ItemModule': {
                '7689436916940328213': {
                    'id': '7689436916940328213',
                    'imagePost': {'images': []},
                }
            }
        }
        page = '<script id="SIGI_STATE" type="application/json">' + json.dumps(payload) + '</script>'
        parsed = tiktok_embed._load_page_data(page)
        item = tiktok_embed._item_struct(parsed, '7689436916940328213')
        self.assertEqual(item['id'], '7689436916940328213')

    def test_post_id_accepts_photo_and_video(self):
        self.assertEqual(tiktok_embed._post_id('https://www.tiktok.com/@u/video/7689436916940328213'), '7689436916940328213')
        self.assertEqual(tiktok_embed._post_id('https://www.tiktok.com/@u/photo/7689436916940328213'), '7689436916940328213')


if __name__ == '__main__':
    unittest.main()

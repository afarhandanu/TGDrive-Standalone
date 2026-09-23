"""List active Instagram Stories without downloading their media."""
import json
import re

from gallery_dl import config, extractor


def list_stories(username, cookie_header):
    if not re.fullmatch(r'[A-Za-z0-9._]{1,30}', username or ''):
        raise ValueError('Username Instagram tidak valid')
    cookies = {}
    for pair in (cookie_header or '').split(';'):
        key, separator, value = pair.strip().partition('=')
        if separator and re.fullmatch(r'[A-Za-z_][A-Za-z0-9_]*', key):
            cookies[key] = value
    if 'sessionid' not in cookies:
        raise ValueError('Sesi Instagram diperlukan untuk membaca Story')
    config.clear()
    try:
        config.set(('extractor',), 'cookies', cookies)
        config.set(('extractor', 'instagram'), 'api', 'rest')
        url = f'https://www.instagram.com/stories/{username}/'
        source = extractor.find(url)
        if source is None:
            raise ValueError('Tidak dapat membuka Story Instagram')
        result = []
        seen = set()
        for row in source.items():
            if len(row) < 3 or row[0] != 3 or not isinstance(row[2], dict):
                continue
            meta = row[2]
            nested = meta.get('item') if isinstance(meta.get('item'), dict) else {}
            media_id = str(meta.get('media_id') or meta.get('pk') or meta.get('id')
                           or nested.get('pk') or nested.get('id') or '')
            if not media_id.isdecimal() or media_id in seen:
                continue
            seen.add(media_id)
            extension = str(meta.get('extension') or nested.get('extension') or '').lower()
            result.append({'id': media_id,
                           'url': f'https://www.instagram.com/stories/{username}/{media_id}/',
                           'date': str(meta.get('date') or meta.get('post_date') or ''),
                           'video': bool(meta.get('video_url') or nested.get('video_url')
                                         or extension in ('mp4', 'm4v', 'webm', 'mov'))})
        return json.dumps(result, ensure_ascii=False)
    finally:
        config.clear()

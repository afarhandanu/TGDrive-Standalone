"""List active Instagram Stories without downloading their media."""
import json
import re
import urllib.request
from pathlib import Path
from urllib.parse import urlparse

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
        config.set(('extractor', 'instagram'), 'videos', 'merged')
        url = f'https://www.instagram.com/stories/{username}/'
        source = extractor.find(url)
        if source is None:
            raise ValueError('Tidak dapat membuka Story Instagram')
        result = []
        seen = set()
        # gallery-dl initializes cookies, the session and Instagram API in
        # Extractor.__iter__. Calling items() directly skips that setup.
        for row in source:
            if len(row) < 3 or row[0] != 3 or not isinstance(row[2], dict):
                continue
            meta = row[2]
            nested = meta.get('item') if isinstance(meta.get('item'), dict) else {}
            media_id = _media_id(meta)
            if not media_id.isdecimal() or media_id in seen:
                continue
            seen.add(media_id)
            extension = str(meta.get('extension') or nested.get('extension') or '').lower()
            video = bool(meta.get('video_url') or nested.get('video_url')
                         or extension in ('mp4', 'm4v', 'webm', 'mov'))
            preview = (meta.get('thumbnail_url') or meta.get('display_url')
                       or nested.get('thumbnail_url') or nested.get('display_url'))
            post = meta.get('post') if isinstance(meta.get('post'), dict) else {}
            images = (nested.get('image_versions2') or meta.get('image_versions2')
                      or post.get('image_versions2') or {})
            if not preview and isinstance(images, dict):
                candidates = images.get('candidates') or []
                if candidates and isinstance(candidates[0], dict):
                    preview = candidates[0].get('url')
            if not preview and not video and isinstance(row[1], str):
                preview = row[1]
            video_preview = (row[1] if video and _allowed_media_url(row[1]) else
                             meta.get('video_url') if video else '')
            if not _allowed_media_url(video_preview):
                video_preview = ''
            result.append({'id': media_id,
                           'url': f'https://www.instagram.com/stories/{username}/{media_id}/',
                           'date': str(meta.get('date') or meta.get('post_date') or ''),
                           'video': video,
                           'preview': preview if isinstance(preview, str) and preview.startswith('https://') else '',
                           'video_preview': video_preview if video_preview.startswith('https://') else ''})
        return json.dumps(result, ensure_ascii=False)
    finally:
        config.clear()


def download_story(story_url, directory, cookies_file, callback, include_thumbnail=False,
                   include_metadata=False):
    """Resolve and save only the requested Story ID. Never fall back to another Story."""
    match = re.fullmatch(
        r'https://www\.instagram\.com/stories/([A-Za-z0-9._]{1,30})/([0-9]+)/',
        story_url or '')
    if not match:
        raise ValueError('URL Story tidak memiliki username dan ID yang valid')
    username, wanted_id = match.groups()
    cookie_path = Path(cookies_file)
    if not cookie_path.is_file():
        raise ValueError('Sesi Instagram diperlukan untuk mengunduh Story terpilih')
    folder = Path(directory) / 'instagram' / username / 'stories'
    config.clear()
    try:
        config.set(('extractor',), 'cookies', str(cookie_path))
        config.set(('extractor', 'instagram'), 'api', 'rest')
        config.set(('extractor', 'instagram'), 'videos', 'merged')
        source = extractor.find(f'https://www.instagram.com/stories/{username}/')
        if source is None:
            raise RuntimeError('Tidak dapat membaca daftar Story Instagram')
        callback.onProgress(0, 'Mencari Story ID ' + wanted_id)
        chosen = None
        for row in source:
            if len(row) < 3 or row[0] != 3 or not isinstance(row[2], dict):
                continue
            if _media_id(row[2]) == wanted_id:
                chosen = row
                break
        if chosen is None:
            raise RuntimeError('Story ID ' + wanted_id + ' tidak ditemukan atau sudah kedaluwarsa. Muat ulang daftar Story.')

        media_url, meta = chosen[1], chosen[2]
        if not _allowed_media_url(media_url) and str(meta.get('extension') or '').lower() in ('mp4', 'm4v', 'mov', 'webm'):
            # With DASH enabled gallery-dl yields a synthetic ytdl: URL. Its
            # video_url field still points to the MP4 for this exact Story ID.
            media_url = meta.get('video_url')
        if not _allowed_media_url(media_url):
            raise RuntimeError('URL media untuk Story ID ' + wanted_id + ' tidak tersedia')
        ext = str(meta.get('extension') or '').lower()
        if ext not in ('jpg', 'jpeg', 'png', 'webp', 'mp4', 'm4v', 'mov', 'webm'):
            raise RuntimeError('Jenis media Story ID ' + wanted_id + ' tidak didukung: ' + ext)
        folder.mkdir(parents=True, exist_ok=True)
        stem = f'Story_by_{username} [{wanted_id}]'
        media = folder / f'{stem}.{ext}'
        _save_media(media_url, media, callback)
        outputs = [media]

        if include_thumbnail and ext in ('mp4', 'm4v', 'mov', 'webm'):
            post = meta.get('post') if isinstance(meta.get('post'), dict) else {}
            images = meta.get('image_versions2') or post.get('image_versions2') or {}
            candidates = images.get('candidates', []) if isinstance(images, dict) else []
            thumb_url = candidates[0].get('url') if candidates and isinstance(candidates[0], dict) else None
            if _allowed_media_url(thumb_url):
                thumb = folder / f'{stem}.thumbnail.jpg'
                try:
                    _save_media(thumb_url, thumb, callback)
                    outputs.append(thumb)
                except Exception:
                    thumb.unlink(missing_ok=True)  # An expired thumbnail cannot invalidate the media.

        if include_metadata:
            info = folder / f'{stem}.info.json'
            info.write_text(json.dumps({'id': wanted_id, 'username': username,
                                        'source': story_url, 'extension': ext},
                                       ensure_ascii=False, indent=2), encoding='utf-8')
            outputs.append(info)
        callback.onProgress(100, 'Story ID ' + wanted_id + ' selesai')
        return json.dumps([{'path': str(path), 'name': path.name} for path in outputs], ensure_ascii=False)
    finally:
        config.clear()


def _media_id(meta):
    nested = meta.get('item') if isinstance(meta.get('item'), dict) else {}
    return str(meta.get('media_id') or meta.get('pk') or meta.get('id')
               or nested.get('pk') or nested.get('id') or '')


def _allowed_media_url(value):
    if not isinstance(value, str):
        return False
    parsed = urlparse(value)
    host = (parsed.hostname or '').lower()
    return parsed.scheme == 'https' and any(
        host == domain or host.endswith('.' + domain)
        for domain in ('instagram.com', 'cdninstagram.com', 'fbcdn.net'))


def _save_media(url, target, callback):
    request = urllib.request.Request(url, headers={
        'User-Agent': 'Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 Chrome/130.0 Safari/537.36',
        'Referer': 'https://www.instagram.com/'})
    try:
        with urllib.request.urlopen(request, timeout=45) as response:
            content_type = response.headers.get_content_type()
            if content_type in ('text/html', 'application/json'):
                raise RuntimeError('Server Instagram tidak mengirimkan file media')
            expected = 'video/' if target.suffix.lower() in ('.mp4', '.m4v', '.mov', '.webm') else 'image/'
            if not (content_type.startswith(expected) or content_type == 'application/octet-stream'):
                raise RuntimeError('Jenis file Story tidak sesuai dengan ID yang dipilih')
            length = int(response.headers.get('Content-Length') or 0)
            written = 0
            with target.open('wb') as output:
                while True:
                    chunk = response.read(262144)
                    if not chunk:
                        break
                    output.write(chunk)
                    written += len(chunk)
                    callback.onProgress(min(99, int(written * 100 / length)) if length else 0,
                                        'Mengunduh Story')
            if written == 0 or (length and written != length):
                raise RuntimeError('File Story tidak lengkap; silakan coba lagi')
    except Exception:
        target.unlink(missing_ok=True)
        raise

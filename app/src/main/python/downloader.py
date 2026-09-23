"""Media extraction runs inside the Android process. No bot or server is contacted."""
import json
import os
import urllib.request
import urllib.parse
import mimetypes
import re
from pathlib import Path

import yt_dlp


def download(url, directory, quality, max_items, subtitles, thumbnail, metadata, cookies, callback):
    root = Path(directory)
    root.mkdir(parents=True, exist_ok=True)
    site, category = _location(url)
    # yt-dlp may create sidecar files for captions, thumbnails and metadata.
    if quality == 'best':
        best = 'best[ext=mp4]/best'
    elif quality == 'audio':
        best = 'bestaudio[ext=m4a]/bestaudio/best'
    elif quality == 'video':
        best = 'bestvideo[ext=mp4]/bestvideo'
    else:
        best = f'best[height<={int(quality)}][ext=mp4]/best[height<={int(quality)}]/best'
    options = {
        'outtmpl': str(root / site / '%(uploader_id,uploader|unknown).80s' /
                       category / '%(title).160s [%(id)s].%(ext)s'),
        'format': best,
        'noplaylist': False,
        'playlistend': int(max_items) if int(max_items) > 0 else None,
        'ignoreerrors': False,
        'restrictfilenames': True,
        'windowsfilenames': True,
        'socket_timeout': 30,
        'retries': 5,
        'fragment_retries': 5,
        'continuedl': True,
        'progress_hooks': [lambda state: _progress(state, callback)],
        'quiet': True,
        'no_warnings': True,
        'writesubtitles': bool(subtitles),
        'writeautomaticsub': bool(subtitles),
        'writethumbnail': bool(thumbnail),
        'writeinfojson': bool(metadata),
    }
    if cookies and os.path.isfile(cookies):
        options['cookiefile'] = cookies
    video_error = None
    try:
        with yt_dlp.YoutubeDL(options) as dl:
            dl.download([url])
    except (yt_dlp.utils.UnsupportedError, yt_dlp.utils.DownloadError) as exc:
        video_error = exc
        if site != 'instagram':
            if isinstance(exc, yt_dlp.utils.UnsupportedError):
                _download_direct(url, root / site / 'unknown' / category, callback)
            else:
                raise
    # A post can be a photo, video, or mixed carousel. Keep the existing video
    # extractor, then fetch images with gallery-dl without replacing video files.
    parsed_path = urllib.parse.urlparse(url).path.lower()
    if site == 'instagram' and (video_error or parsed_path.startswith('/p/')):
        import gallery_download
        existing = {str(p) for p in root.rglob('*') if p.is_file()}
        try:
            gallery_download.download(url, str(root), max_items, cookies, callback, content='photos',
                                      quality=quality, subtitles=subtitles, thumbnail=thumbnail, metadata=metadata)
            if video_error and not any(p.suffix.lower() in ('.jpg', '.jpeg', '.png', '.webp', '.gif', '.avif', '.heic')
                                       for p in root.rglob('*') if p.is_file()):
                raise RuntimeError('Ekstraktor Instagram tidak menemukan file gambar')
        except Exception as exc:
            if video_error or not existing:
                raise RuntimeError(f'Foto Instagram gagal: {exc}') from exc
    downloaded = [p for p in root.rglob('*') if p.is_file() and p.name != 'cookies.txt'
                  and not p.name.endswith(('.part', '.ytdl'))]
    paths = [_resolve_username(p, root, url) for p in downloaded]
    if not paths:
        raise RuntimeError('Ekstraktor tidak menghasilkan foto atau video. Periksa link atau sesi login.')
    return json.dumps([{'path': str(p), 'name': p.name} for p in paths], ensure_ascii=False)


def _location(url):
    parsed = urllib.parse.urlparse(url)
    host = (parsed.hostname or '').lower().removeprefix('www.')
    path = parsed.path.lower()
    if host.endswith('instagram.com'):
        site = 'instagram'
        category = 'stories' if '/stories/' in path else 'reels' if '/reel/' in path else 'posts'
    elif host in ('x.com', 'twitter.com') or host.endswith('.x.com'):
        site, category = 'x', 'posts'
    elif host.endswith('tiktok.com'):
        site, category = 'tiktok', 'videos'
    elif host.endswith('youtube.com') or host == 'youtu.be':
        site, category = 'youtube', 'shorts' if '/shorts/' in path else 'videos'
    else:
        site, category = host.split('.')[0] if host else 'other', 'media'
    return re.sub(r'[^a-z0-9_-]', '_', site)[:64], category


def _resolve_username(path, root, url):
    parts = path.relative_to(root).parts
    if len(parts) < 4:
        return path
    site = parts[0]
    found = re.search(r'(?:Video|Photo|Story)_by_([A-Za-z0-9._-]+)', path.name, re.I)
    url_parts = urllib.parse.urlparse(url).path.strip('/').split('/')
    if site == 'instagram' and len(url_parts) > 1 and url_parts[0].lower() == 'stories':
        username = url_parts[1]  # The Story URL identifies its owner; yt-dlp may return a numeric user ID.
    elif site == 'x' and len(url_parts) > 1 and url_parts[1].lower() == 'status':
        username = url_parts[0]
    elif site == 'instagram' and url_parts and url_parts[0].lower() not in ('p', 'reel', 'reels', 'tv', 'explore', 'accounts'):
        username = url_parts[0]
    else:
        username = found.group(1) if found else None
    if not username:
        return path
    username = re.sub(r'[^a-zA-Z0-9._-]', '_', username).strip('._')[:80]
    if not username or username.lower() == parts[1].lower():
        return path
    target = root / site / username / Path(*parts[2:])
    target.parent.mkdir(parents=True, exist_ok=True)
    path.rename(target)
    return target


def _download_direct(url, root, callback):
    root.mkdir(parents=True, exist_ok=True)
    request = urllib.request.Request(url, headers={'User-Agent': 'TGDrive/1.1'})
    with urllib.request.urlopen(request, timeout=30) as response:
        mime = response.headers.get_content_type()
        if mime in {'text/html', 'application/xhtml+xml'}:
            raise RuntimeError('Tautan mengarah ke halaman web, bukan file. Coba login lalu ulangi.')
        candidate = urllib.parse.unquote(urllib.parse.urlparse(response.url).path.rsplit('/', 1)[-1])
        name = ''.join(ch if ch.isalnum() or ch in '._- ' else '_' for ch in candidate)[:160]
        if not name: name = 'download' + (mimetypes.guess_extension(mime) or '.bin')
        length = int(response.headers.get('Content-Length') or 0)
        output = root / name
        with output.open('wb') as target:
            done = 0
            while True:
                data = response.read(262144)
                if not data: break
                target.write(data)
                done += len(data)
                callback.onProgress(int(done * 100 / length) if length else -1, 'Direct')


def _progress(state, callback):
    if state.get('status') == 'downloading':
        total = state.get('total_bytes') or state.get('total_bytes_estimate') or 0
        done = state.get('downloaded_bytes') or 0
        callback.onProgress(int(done * 100 / total) if total else -1,
                            str(state.get('_speed_str') or ''))
    elif state.get('status') == 'finished':
        callback.onProgress(100, 'Mengolah file')

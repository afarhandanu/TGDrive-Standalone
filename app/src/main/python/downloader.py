"""Media extraction runs inside the Android process. No bot or server is contacted."""
import json
import os
import urllib.request
import urllib.parse
import mimetypes
import re
from pathlib import Path

import yt_dlp


# TikTok currently changes its web anti-bot challenge frequently. yt-dlp's TikTok
# extractor has an Android app API path, but it is opt-in unless app_info/device_id
# is provided. Prefer that path, then let yt-dlp fall back to the webpage.
_TIKTOK_WEB_UA = (
    'Mozilla/5.0 (Windows NT 10.0; Win64; x64) '
    'AppleWebKit/537.36 (KHTML, like Gecko) '
    'Chrome/145.0.0.0 Safari/537.36'
)


def download(url, directory, quality, max_items, subtitles, thumbnail, metadata, cookies, callback,
             tiktok_photo_mode='combine', tiktok_watermark='without'):
    root = Path(directory)
    root.mkdir(parents=True, exist_ok=True)
    site, category = _location(url)
    # yt-dlp may create sidecar files for captions, thumbnails and metadata.
    best = _format_selector(site, quality, tiktok_watermark)
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
    if site == 'tiktok':
        # app_info=[''] tells yt-dlp to use its maintained default TikTok Android
        # app identity first. This avoids depending solely on the web challenge path.
        options['extractor_args'] = {'tiktok': {'app_info': ['']}}
        options['http_headers'] = {
            'User-Agent': _TIKTOK_WEB_UA,
            'Referer': 'https://www.tiktok.com/',
        }
        options['extractor_retries'] = 3
    if cookies and os.path.isfile(cookies):
        options['cookiefile'] = cookies
    video_error = None
    try:
        with yt_dlp.YoutubeDL(options) as dl:
            dl.download([url])
    except (yt_dlp.utils.UnsupportedError, yt_dlp.utils.DownloadError) as exc:
        video_error = exc
        if site == 'tiktok' and isinstance(exc, yt_dlp.utils.DownloadError):
            # TikTok can return an anti-automation webpage to yt-dlp even for
            # public posts. gallery-dl extracts TikTok photos/audio independently.
            try:
                import tiktok_fallback
                tiktok_fallback.download(url, root, max_items, cookies, callback,
                                         quality, subtitles, thumbnail, metadata, tiktok_watermark)
            except Exception as alternate_error:
                raise RuntimeError(
                    f'TikTok gagal melalui dua ekstraktor. yt-dlp: {exc}; gallery-dl: {alternate_error}'
                ) from alternate_error
        elif site != 'instagram':
            if isinstance(exc, yt_dlp.utils.UnsupportedError):
                _download_direct(url, root / site / 'unknown' / category, callback)
            else:
                raise
    # TikTok photo posts can look successful to yt-dlp while yielding only the
    # soundtrack. In that case, fetch the actual photos with gallery-dl as well.
    if site == 'tiktok':
        current_media = [p for p in root.rglob('*') if p.is_file() and _media_kind(p) in ('image', 'video')]
        if not current_media:
            try:
                import tiktok_fallback
                tiktok_fallback.download(url, root, max_items, cookies, callback,
                                         quality, subtitles, thumbnail, metadata, tiktok_watermark)
            except Exception as alternate_error:
                if video_error is None:
                    raise RuntimeError(f'TikTok tidak menghasilkan gambar/video: {alternate_error}') from alternate_error

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
    if site == 'tiktok':
        if not any(_media_kind(p) in ('image', 'video') for p in paths):
            raise RuntimeError('TikTok hanya mengembalikan audio tanpa gambar/video. Coba ulangi atau ganti opsi watermark.')
        items = _tiktok_items(paths, root, tiktok_photo_mode)
    else:
        items = [{'path': str(p), 'name': p.name} for p in paths]
    return json.dumps(items, ensure_ascii=False)


def _format_selector(site, quality, watermark):
    if quality == 'audio':
        return 'bestaudio[ext=m4a]/bestaudio/best'
    if site == 'tiktok' and watermark == 'with':
        height = '' if quality in ('best', 'video') else f'[height<={int(quality)}]'
        return f'best[format_id^=download_addr]{height}/best[format_id=download]{height}'
    if site == 'tiktok' and watermark != 'with':
        height = '' if quality in ('best', 'video') else f'[height<={int(quality)}]'
        if quality == 'video':
            return 'bestvideo[format_id!^=download_addr][format_id!=download][ext=mp4]/bestvideo[format_id!^=download_addr][format_id!=download]'
        return (f'best[format_id!^=download_addr][format_id!=download]{height}[ext=mp4]/'
                f'best[format_id!^=download_addr][format_id!=download]{height}/'
                f'best[format_id!^=download_addr][format_id!=download]')
    if quality == 'best':
        return 'best[ext=mp4]/best'
    if quality == 'video':
        return 'bestvideo[ext=mp4]/bestvideo'
    return f'best[height<={int(quality)}][ext=mp4]/best[height<={int(quality)}]/best'


def _media_kind(path):
    ext = path.suffix.lower()
    if ext in {'.jpg', '.jpeg', '.png', '.webp', '.bmp', '.gif', '.heic', '.heif', '.avif'}:
        return 'image'
    if ext in {'.mp3', '.m4a', '.aac', '.wav', '.ogg', '.opus', '.flac'}:
        return 'audio'
    if ext in {'.mp4', '.m4v', '.mov', '.webm', '.mkv'}:
        return 'video'
    return 'other'


def _natural_key(value):
    return [int(part) if part.isdigit() else part.lower() for part in re.split(r'(\d+)', value)]


def _tiktok_items(paths, root, photo_mode):
    paths = list(dict.fromkeys(Path(p) for p in paths if Path(p).is_file()))
    images = sorted((p for p in paths if _media_kind(p) == 'image' and 'cover' not in p.name.lower()),
                    key=lambda p: _natural_key(p.name))
    audios = [p for p in paths if _media_kind(p) == 'audio']
    videos = [p for p in paths if _media_kind(p) == 'video']
    if photo_mode == 'combine' and images and audios and not videos:
        audio = max(audios, key=lambda p: p.stat().st_size)
        output = _slideshow_output(images, root)
        keep = [p for p in paths if _media_kind(p) == 'other']
        return ([{'path': str(output), 'name': output.name,
                  'slideshow_images': [str(p) for p in images], 'audio_path': str(audio)}] +
                [{'path': str(p), 'name': p.name} for p in keep])
    return [{'path': str(p), 'name': p.name} for p in paths]


def _slideshow_output(images, root):
    first = images[0]
    stem = re.sub(r'(?i)(?:[-_. ]?(?:photo|image)?[-_. ]?\d+)$', '', first.stem).strip(' ._-')
    if not stem:
        stem = 'TikTok_slideshow'
    output = first.parent / f'{stem}.slideshow.mp4'
    # Do not overwrite an extractor-created file if a future backend uses this name.
    if output.exists():
        output = first.parent / f'{stem}.slideshow_merged.mp4'
    if not output.resolve().is_relative_to(Path(root).resolve()):
        raise RuntimeError('Lokasi slideshow TikTok tidak valid')
    return output


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
    request = urllib.request.Request(url, headers={'User-Agent': 'TheGreatDrive/1.3'})
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

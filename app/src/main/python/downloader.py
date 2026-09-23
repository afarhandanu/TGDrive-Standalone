"""Media extraction runs inside the Android process. No bot or server is contacted."""
import json
import os
import urllib.request
import urllib.parse
import mimetypes
from pathlib import Path

import yt_dlp


def download(url, directory, quality, max_items, subtitles, thumbnail, metadata, cookies, callback):
    root = Path(directory)
    root.mkdir(parents=True, exist_ok=True)
    # yt-dlp may create sidecar files for captions, thumbnails and metadata.
    best = 'best[ext=mp4]/best' if quality == 'best' else (
        'bestaudio[ext=m4a]/bestaudio/best' if quality == 'audio' else
        f'best[height<={int(quality)}][ext=mp4]/best[height<={int(quality)}]/best'
    )
    options = {
        'outtmpl': str(root / '%(uploader|unknown).80s' / '%(title).160s [%(id)s].%(ext)s'),
        'format': best,
        'noplaylist': False,
        'playlistend': max(1, min(int(max_items), 500)),
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
    try:
        with yt_dlp.YoutubeDL(options) as dl:
            dl.download([url])
    except yt_dlp.utils.UnsupportedError:
        _download_direct(url, root, callback)
    paths = [p for p in root.rglob('*') if p.is_file() and p.name != 'cookies.txt'
             and not p.name.endswith(('.part', '.ytdl'))]
    if not paths:
        raise RuntimeError('Ekstraktor tidak menghasilkan file. Periksa link atau sesi login.')
    return json.dumps([{'path': str(p), 'name': p.name} for p in paths], ensure_ascii=False)


def _download_direct(url, root, callback):
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

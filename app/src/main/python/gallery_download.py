"""Opt-in profile/carousel extractor; the normal yt-dlp download path stays intact."""
import json
import re
from pathlib import Path
from urllib.parse import urlparse

from gallery_dl import config, job


def download(url, directory, maximum, cookies, callback, date_after='', date_before='', content='all',
             quality='best', subtitles=False, thumbnail=False, metadata=False):
    root = Path(directory)
    host = (urlparse(url).hostname or '').lower().removeprefix('www.')
    pieces = [p for p in urlparse(url).path.split('/') if p]
    limit = int(maximum)
    if limit < 0: raise ValueError('Maksimum item tidak boleh negatif')
    if host.endswith('instagram.com'):
        platform = 'instagram'
        category = 'stories' if 'stories' in pieces else 'reels' if 'reel' in pieces else 'posts'
        creator = '{username}'
        filename = '{shortcode}__{num:>02} - {media_id}.{extension}'
    elif host in ('x.com', 'twitter.com'):
        platform, category = 'twitter', 'posts'
        creator = '{user[name]}'
        filename = '{num:>02} - {tweet_id}.{extension}'
    elif host.endswith('facebook.com') or host == 'fb.watch':
        platform, category = 'facebook', 'posts'
        creator = _safe(pieces[0] if pieces else 'facebook')
        filename = '{id}__{id}.{extension}'
    else:
        raise ValueError('Mode profil/album tersedia untuk Instagram, X, dan Facebook')

    config.clear()
    try:
        config.set(('extractor',), 'base-directory', str(root))
        if limit: config.set(('extractor',), 'post-range', f'1-{limit}')
        config.set(('extractor',), 'retries', 2)
        config.set(('extractor',), 'sleep-request', '2.0-4.0')
        config.set(('extractor',), 'cookies-update', False)
        config.set(('extractor',), 'path-restrict', 'windows')
        if cookies and Path(cookies).is_file():
            config.set(('extractor',), 'cookies', cookies)
        if date_after:
            config.set(('extractor',), 'date-after', date_after)
        if date_before:
            config.set(('extractor',), 'date-before', date_before)
        section = 'x' if platform == 'twitter' else platform
        config.set(('extractor', platform), 'directory', [section, creator, category])
        config.set(('extractor', platform), 'filename', filename)
        if platform == 'instagram':
            config.set(('extractor', platform), 'api', 'rest' if cookies else 'graphql')
            if content == 'photos':
                config.set(('extractor', platform), 'file-filter',
                           "extension.lower() in ('jpg','jpeg','png','webp','gif','avif','heic')")
        elif platform == 'twitter':
            config.set(('extractor', platform), 'include', 'media')
            config.set(('extractor', platform), 'videos', 'ytdl')
            config.set(('extractor', platform), 'cards', 'ytdl')
        else:
            config.set(('extractor', platform), 'include', ['photos', 'albums'])
            config.set(('extractor', platform), 'videos', 'ytdl' if content != 'photos' else False)
            if content == 'photos':
                config.set(('extractor', platform), 'file-filter',
                           "extension.lower() in ('jpg','jpeg','png','webp','gif','avif','heic')")
            elif content == 'videos':
                config.set(('extractor', platform), 'file-filter',
                           "extension.lower() in ('mp4','webm','m4v','mov')")
        config.set(('downloader', 'ytdl'), 'forward-cookies', True)
        video_format = ('best[ext=mp4]/best' if quality == 'best' else
                        'bestaudio[ext=m4a]/bestaudio/best' if quality == 'audio' else
                        'bestvideo[ext=mp4]/bestvideo' if quality == 'video' else
                        f'best[height<={int(quality)}][ext=mp4]/best[height<={int(quality)}]/best')
        config.set(('downloader', 'ytdl'), 'raw-options', {
            'quiet': True, 'no_warnings': True, 'format': video_format,
            'writesubtitles': bool(subtitles), 'writeautomaticsub': bool(subtitles),
            'writethumbnail': bool(thumbnail), 'writeinfojson': bool(metadata),
        })
        callback.onProgress(0, 'Mengambil profil / album')
        status = job.DownloadJob(url).run()
        files = [p for p in root.rglob('*') if p.is_file() and p.name != 'cookies.txt'
                 and not p.name.endswith(('.part', '.ytdl'))]
        if status not in (None, 0) and not files:
            raise RuntimeError('gallery-dl gagal membaca profil / album; periksa URL atau sesi akun')
        if not files:
            raise RuntimeError('Tidak ada media pada profil / album atau rentang tanggal ini')
        if status not in (None, 0):
            raise RuntimeError('Sebagian item profil gagal diekstrak; coba lagi dengan jumlah lebih kecil')
        if platform in ('instagram', 'facebook'):
            files = [_group_post(p, root) for p in files]
        callback.onProgress(100, f'{len(files)} file ditemukan')
        return json.dumps([{'path': str(p), 'name': p.name} for p in files], ensure_ascii=False)
    finally:
        config.clear()


def _group_post(path, root):
    # Keep gallery-dl's filename marker and put each post's carousel in its own folder.
    prefix = path.name.split('__', 1)[0]
    if not prefix or prefix.lower() in ('none', 'unknown') or not re.fullmatch(r'[\w.-]{1,100}', prefix):
        return path
    target = path.parent / prefix / path.name
    target.parent.mkdir(parents=True, exist_ok=True)
    if target != path:
        path.replace(target)
    return target


def _safe(value):
    return re.sub(r'[^a-zA-Z0-9._-]', '_', value)[:80] or 'unknown'

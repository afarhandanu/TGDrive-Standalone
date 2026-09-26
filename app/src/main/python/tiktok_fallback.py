"""TikTok-only fallback; never route other websites through this extractor."""
import urllib.parse
import urllib.request
from pathlib import Path

from gallery_dl import config, job


def _tiktok_host(url):
    host = (urllib.parse.urlparse(url).hostname or '').lower()
    return host == 'tiktok.com' or host.endswith('.tiktok.com')


def _canonical_url(url):
    if not _tiktok_host(url):
        raise ValueError('Tautan bukan dari TikTok')
    host = (urllib.parse.urlparse(url).hostname or '').lower()
    if host not in ('vt.tiktok.com', 'vm.tiktok.com'):
        return url
    # gallery-dl needs the destination post URL rather than the share redirect.
    request = urllib.request.Request(url, headers={
        'User-Agent': 'Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36',
    })
    with urllib.request.urlopen(request, timeout=20) as response:
        resolved = response.url
    if not _tiktok_host(resolved):
        raise RuntimeError('Tautan pendek TikTok mengarah ke situs lain')
    return resolved


def download(url, root, max_items, cookies, callback, quality='best',
             subtitles=False, thumbnail=False, metadata=False, watermark='without'):
    root = Path(root)
    canonical = _canonical_url(url)
    path = urllib.parse.urlparse(canonical).path
    username = next((p[1:] for p in path.split('/') if p.startswith('@')), 'unknown')
    username = ''.join(c for c in username if c.isalnum() or c in '._-')[:80] or 'unknown'
    limit = int(max_items)
    if limit < 0:
        raise ValueError('Maksimum item tidak boleh negatif')
    config.clear()
    try:
        config.set(('extractor',), 'base-directory', str(root))
        config.set(('extractor',), 'retries', 2)
        config.set(('extractor',), 'path-restrict', 'windows')
        config.set(('extractor',), 'cookies-update', False)
        if limit:
            config.set(('extractor',), 'post-range', f'1-{limit}')
        if cookies and Path(cookies).is_file():
            config.set(('extractor',), 'cookies', cookies)
        config.set(('extractor', 'tiktok'), 'directory', ['tiktok', username, 'videos'])
        # gallery-dl does not expose TikTok's native watermarked video variant.
        # In watermarked mode it is used only to recover photo posts and audio.
        config.set(('extractor', 'tiktok'), 'videos', watermark != 'with')
        config.set(('extractor', 'tiktok'), 'photos', quality != 'video' or watermark == 'with')
        config.set(('extractor', 'tiktok'), 'audio', True)
        config.set(('extractor', 'tiktok', 'posts'), 'ytdl', False)
        config.set(('extractor', 'tiktok'), 'covers', bool(thumbnail))
        config.set(('extractor', 'tiktok'), 'subtitles', bool(subtitles))
        # Direct video URLs are downloaded by gallery-dl, not its ytdl backend.
        callback.onProgress(0, 'Mencoba ekstraktor TikTok lain')
        result = job.DownloadJob(canonical).run()
        files = [p for p in (root / 'tiktok').rglob('*') if p.is_file()
                 and not p.name.endswith(('.part', '.ytdl'))]
        media = [p for p in files if p.suffix.lower() in {'.jpg', '.jpeg', '.png', '.webp', '.gif', '.avif',
                                                          '.mp3', '.m4a', '.aac', '.ogg', '.opus', '.wav',
                                                          '.mp4', '.m4v', '.mov', '.webm', '.mkv'}]
        if result not in (None, 0) or not media:
            raise RuntimeError('TikTok tidak menyediakan media melalui ekstraktor cadangan; periksa log error')
        callback.onProgress(100, f'{len(files)} file ditemukan')
        return files
    finally:
        config.clear()

"""Last-resort TikTok post extractor based on TikTok's public embed/player pages.

This module intentionally handles only individual public TikTok posts. It does not
scrape feeds or profiles and does not rely on an external download service.
"""
import html as html_module
import http.cookiejar
import json
import mimetypes
import re
import urllib.parse
import urllib.request
from pathlib import Path


_UAS = (
    # Current workaround reported for TikTok's September 2026 web challenge.
    'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 '
    '(KHTML, like Gecko) Chrome/145.0.0.0 Safari/537.36 OPR/118.0.0.0',
    # Older UA which is still accepted by the public TikTok page on many edges.
    'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 '
    '(KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36',
)

_SCRIPT_RE = re.compile(
    r'<script[^>]+id=["\']__UNIVERSAL_DATA_FOR_REHYDRATION__["\'][^>]*>(.*?)</script>',
    re.IGNORECASE | re.DOTALL,
)
_SIGI_RE = re.compile(
    r'<script[^>]+id=["\']SIGI_STATE["\'][^>]*>(.*?)</script>',
    re.IGNORECASE | re.DOTALL,
)
_POST_ID_RE = re.compile(r'/(?:video|photo)/(\d{8,24})(?:[/?#]|$)', re.IGNORECASE)


def _is_tiktok_host(host):
    host = (host or '').lower()
    return host == 'tiktok.com' or host.endswith('.tiktok.com')


def _canonical_url(url, opener):
    parsed = urllib.parse.urlparse(url)
    if not _is_tiktok_host(parsed.hostname):
        raise ValueError('Tautan bukan dari TikTok')
    if (parsed.hostname or '').lower() not in ('vt.tiktok.com', 'vm.tiktok.com'):
        return url
    last_error = None
    for ua in ('facebookexternalhit/1.1', *_UAS):
        try:
            request = urllib.request.Request(url, headers={'User-Agent': ua})
            with opener.open(request, timeout=20) as response:
                resolved = response.geturl()
            if not _is_tiktok_host(urllib.parse.urlparse(resolved).hostname):
                raise RuntimeError('Tautan pendek TikTok mengarah ke situs lain')
            return resolved
        except Exception as exc:
            last_error = exc
    raise RuntimeError(f'Gagal membuka tautan pendek TikTok: {last_error}') from last_error


def _post_id(url):
    match = _POST_ID_RE.search(urllib.parse.urlparse(url).path)
    if match:
        return match.group(1)
    # TikTok share redirects occasionally keep the post ID in a query value.
    for value in urllib.parse.parse_qs(urllib.parse.urlparse(url).query).values():
        for item in value:
            match = re.search(r'(?<!\d)(\d{17,20})(?!\d)', item)
            if match:
                return match.group(1)
    raise RuntimeError('ID post TikTok tidak ditemukan dari tautan')


def _cookie_opener(cookies):
    jar = http.cookiejar.CookieJar()
    if cookies and Path(cookies).is_file():
        try:
            moz = http.cookiejar.MozillaCookieJar(str(cookies))
            moz.load(ignore_discard=True, ignore_expires=True)
            jar = moz
        except Exception:
            # A malformed/foreign cookie file must not disable public embed fallback.
            pass
    return urllib.request.build_opener(urllib.request.HTTPCookieProcessor(jar))


def _fetch_text(opener, url, referer=None):
    errors = []
    for ua in _UAS:
        headers = {
            'User-Agent': ua,
            'Accept': 'text/html,application/xhtml+xml,application/json;q=0.9,*/*;q=0.8',
            'Accept-Language': 'en-US,en;q=0.9',
        }
        if referer:
            headers['Referer'] = referer
        try:
            request = urllib.request.Request(url, headers=headers)
            with opener.open(request, timeout=30) as response:
                body = response.read()
                encoding = response.headers.get_content_charset() or 'utf-8'
                text = body.decode(encoding, errors='replace')
                if response.status >= 400:
                    raise RuntimeError(f'HTTP {response.status}')
                if len(text) < 100:
                    raise RuntimeError('respons terlalu pendek')
                return text, ua
        except Exception as exc:
            errors.append(str(exc))
    raise RuntimeError('; '.join(errors[-2:]) or 'gagal mengambil halaman TikTok')


def _load_page_data(page):
    """Load either of the server-rendered JSON containers TikTok has used."""
    errors = []
    for label, regex in (('universal', _SCRIPT_RE), ('SIGI_STATE', _SIGI_RE)):
        match = regex.search(page)
        if not match:
            continue
        raw = html_module.unescape(match.group(1)).strip()
        try:
            return json.loads(raw)
        except json.JSONDecodeError as exc:
            errors.append(f'{label}: {exc}')
    if errors:
        raise RuntimeError('data embed TikTok tidak valid: ' + '; '.join(errors))
    raise RuntimeError('data embed TikTok tidak ditemukan')


def _load_universal_data(page):
    # Backward-compatible helper retained for tests/callers.
    return _load_page_data(page)


def _walk(obj):
    if isinstance(obj, dict):
        yield obj
        for value in obj.values():
            yield from _walk(value)
    elif isinstance(obj, list):
        for value in obj:
            yield from _walk(value)


def _item_struct(data, post_id):
    if isinstance(data, dict):
        item_module = data.get('ItemModule')
        if isinstance(item_module, dict):
            direct = item_module.get(post_id)
            if isinstance(direct, dict):
                return direct
            for value in item_module.values():
                if isinstance(value, dict) and str(value.get('id') or '') == post_id:
                    return value
    scope = data.get('__DEFAULT_SCOPE__') if isinstance(data, dict) else None
    if isinstance(scope, dict):
        for key in ('webapp.video-detail', 'webapp.video-detail-v2'):
            value = scope.get(key)
            if isinstance(value, dict):
                item = value.get('itemInfo', {}).get('itemStruct')
                if isinstance(item, dict) and (str(item.get('id') or '') in ('', post_id)):
                    return item
    # Embed/player structures have changed more than once. Search conservatively for
    # a dict matching this post ID and carrying actual media fields.
    fallback = None
    for value in _walk(data):
        if not ('video' in value or 'imagePost' in value or 'image_post' in value):
            continue
        value_id = str(value.get('id') or value.get('awemeId') or value.get('aweme_id') or '')
        if value_id == post_id:
            return value
        if fallback is None and ('author' in value or 'desc' in value or 'music' in value):
            fallback = value
    if fallback:
        return fallback
    raise RuntimeError('metadata media tidak ditemukan di halaman embed TikTok')


def _strings(value):
    if isinstance(value, str):
        if value.startswith(('https://', 'http://')):
            yield value
        return
    if isinstance(value, list):
        for item in value:
            yield from _strings(item)
        return
    if not isinstance(value, dict):
        return
    # Prefer URL-list fields over URI identifiers.
    for key in ('urlList', 'UrlList', 'url_list', 'url', 'playAddr', 'downloadAddr', 'playUrl'):
        if key in value:
            yield from _strings(value[key])
    for key, item in value.items():
        if key not in {'urlList', 'UrlList', 'url_list', 'url', 'playAddr', 'downloadAddr', 'playUrl'}:
            yield from _strings(item)


def _first_url(value):
    return next(iter(dict.fromkeys(_strings(value))), None)


def _video_urls(item, watermark):
    video = item.get('video') if isinstance(item, dict) else None
    if not isinstance(video, dict):
        return []
    preferred = ('downloadAddr', 'download_addr', 'download') if watermark == 'with' else (
        'playAddr', 'play_addr', 'play', 'bitrateInfo', 'bitrate_info')
    urls = []
    for key in preferred:
        if key in video:
            urls.extend(_strings(video[key]))
    return list(dict.fromkeys(urls))


def _image_urls(item):
    post = item.get('imagePost') or item.get('image_post')
    if not isinstance(post, dict):
        return []
    images = post.get('images') or post.get('imageList') or post.get('image_list') or []
    out = []
    if isinstance(images, list):
        for image in images:
            if not isinstance(image, dict):
                continue
            chosen = None
            for key in ('imageURL', 'imageUrl', 'image_url', 'displayImage', 'display_image', 'thumbnail'):
                if key in image:
                    chosen = _first_url(image[key])
                    if chosen:
                        break
            if not chosen:
                chosen = _first_url(image)
            if chosen and chosen not in out:
                out.append(chosen)
    return out


def _audio_urls(item):
    music = item.get('music') if isinstance(item, dict) else None
    if not isinstance(music, dict):
        return []
    urls = []
    for key in ('playUrl', 'play_url', 'playAddr', 'play_addr'):
        if key in music:
            urls.extend(_strings(music[key]))
    return list(dict.fromkeys(urls))


def _safe_username(item, canonical):
    author = item.get('author') if isinstance(item, dict) else None
    username = None
    if isinstance(author, dict):
        username = author.get('uniqueId') or author.get('unique_id') or author.get('id')
    if not username:
        path = urllib.parse.urlparse(canonical).path
        username = next((part[1:] for part in path.split('/') if part.startswith('@')), 'unknown')
    clean = ''.join(c for c in str(username) if c.isalnum() or c in '._-')[:80]
    return clean or 'unknown'


def _extension(content_type, url, kind):
    content_type = (content_type or '').split(';', 1)[0].strip().lower()
    known = {
        'video/mp4': '.mp4', 'video/webm': '.webm',
        'image/jpeg': '.jpg', 'image/jpg': '.jpg', 'image/webp': '.webp', 'image/png': '.png',
        'audio/mpeg': '.mp3', 'audio/mp4': '.m4a', 'audio/aac': '.aac', 'audio/ogg': '.ogg',
    }
    if content_type in known:
        return known[content_type]
    ext = Path(urllib.parse.urlparse(url).path).suffix.lower()
    if ext and len(ext) <= 6:
        return ext
    guessed = mimetypes.guess_extension(content_type) if content_type else None
    return guessed or {'video': '.mp4', 'image': '.jpg', 'audio': '.mp3'}[kind]


def _download_media(opener, url, dest_base, kind, referer, callback, progress_base=0, progress_span=100):
    parsed = urllib.parse.urlparse(url)
    if parsed.scheme not in ('http', 'https') or not parsed.hostname:
        raise RuntimeError('URL media TikTok tidak valid')
    errors = []
    for ua in _UAS:
        try:
            request = urllib.request.Request(url, headers={
                'User-Agent': ua,
                'Referer': referer,
                'Accept': '*/*',
            })
            with opener.open(request, timeout=45) as response:
                if response.status >= 400:
                    raise RuntimeError(f'HTTP {response.status}')
                ext = _extension(response.headers.get('Content-Type'), response.geturl(), kind)
                output = dest_base.with_suffix(ext)
                total = int(response.headers.get('Content-Length') or 0)
                done = 0
                with output.open('wb') as target:
                    while True:
                        chunk = response.read(262144)
                        if not chunk:
                            break
                        target.write(chunk)
                        done += len(chunk)
                        if total:
                            callback.onProgress(
                                min(99, progress_base + int(progress_span * done / total)),
                                'Mengunduh media TikTok',
                            )
                if output.stat().st_size == 0:
                    output.unlink(missing_ok=True)
                    raise RuntimeError('file kosong')
                return output
        except Exception as exc:
            errors.append(str(exc))
    raise RuntimeError(f'gagal mengunduh media TikTok: {errors[-1] if errors else "unknown"}')


def download(url, root, cookies, callback, watermark='without'):
    root = Path(root)
    opener = _cookie_opener(cookies)
    canonical = _canonical_url(url, opener)
    post_id = _post_id(canonical)

    pages = (
        f'https://www.tiktok.com/player/v1/{post_id}?music_info=1&description=1',
        f'https://www.tiktok.com/embed/v2/{post_id}',
        canonical,
    )
    page_errors = []
    data = None
    for page_url in pages:
        try:
            page, _ua = _fetch_text(opener, page_url, 'https://www.tiktok.com/')
            data = _load_page_data(page)
            item = _item_struct(data, post_id)
            break
        except Exception as exc:
            page_errors.append(f'{urllib.parse.urlparse(page_url).path}: {exc}')
    else:
        raise RuntimeError('embed TikTok gagal: ' + ' | '.join(page_errors[-3:]))

    username = _safe_username(item, canonical)
    images = _image_urls(item)
    audio = _audio_urls(item)
    videos = _video_urls(item, watermark)
    category = 'carousel' if images else 'videos'
    # Keep the same canonical hierarchy used by yt-dlp and Drive publishing.
    dest = root / 'tiktok' / username / category / post_id
    dest.mkdir(parents=True, exist_ok=True)
    callback.onProgress(2, 'Mencoba pemutar resmi TikTok')
    files = []

    # Photo posts: keep every slide plus soundtrack so the caller can either
    # preserve them separately or combine them into one MP4 with FFmpeg.
    if images:
        count = len(images) + (1 if audio else 0)
        for index, media_url in enumerate(images, 1):
            base = dest / f'{post_id}_photo_{index:02d}'
            files.append(_download_media(
                opener, media_url, base, 'image', canonical, callback,
                int((index - 1) * 95 / max(count, 1)), max(1, int(95 / max(count, 1))),
            ))
        if audio:
            base = dest / f'{post_id}_audio'
            files.append(_download_media(
                opener, audio[0], base, 'audio', canonical, callback,
                int(len(images) * 95 / max(count, 1)), max(1, int(95 / max(count, 1))),
            ))
    elif videos:
        last_error = None
        for media_url in videos:
            try:
                files.append(_download_media(opener, media_url, dest / post_id, 'video', canonical, callback))
                last_error = None
                break
            except Exception as exc:
                last_error = exc
        if last_error and not files:
            raise last_error
    else:
        raise RuntimeError('halaman embed TikTok tidak memuat foto atau video')

    callback.onProgress(100, f'{len(files)} media ditemukan')
    return files

"""Import local Instagram JSON or ZIP using the bot's v3.6.12 parser."""
import base64
import json
import shutil
import urllib.request
import urllib.parse
import zipfile
from datetime import date
from pathlib import Path

import instagram_parser as parser


def import_file(source_path, directory, category, maximum, callback, date_from='', date_to='', output_mode='folder'):
    source = Path(source_path)
    root = Path(directory)
    root.mkdir(parents=True, exist_ok=True)
    if source.suffix.lower() == '.zip':
        source_json = parser.extract_instagram_bundle_zip(source, root / 'bundle')
    else:
        source_json = source
    parsed = parser.parse_instagram_json_bytes(source_json.read_bytes())
    start = date.fromisoformat(date_from) if date_from else None
    end = date.fromisoformat(date_to) if date_to else None
    if start and end and start > end:
        raise ValueError('Tanggal awal harus sebelum atau sama dengan tanggal akhir')
    selected = []
    for item in parsed.category_items(category):
        posted = item.post_date
        if start or end:
            if posted is None or (start and posted < start) or (end and posted > end):
                continue
        selected.append(item)
        if len(selected) >= max(1, min(int(maximum), 500)):
            break
    output = []
    for i, item in enumerate(selected):
        kind = item.primary_category.lower()
        section = 'reels' if kind == 'reels' else 'stories' if kind == 'stories' else \
            'tagged' if kind in ('mentions', 'tagged') else 'posts'
        target_dir = root / 'instagram' / _safe(item.username) / section
        target_dir.mkdir(parents=True, exist_ok=True)
        for asset in item.assets:
            kind = parser._media_source_kind(asset.url)
            suffix = _extension(asset.url, asset.kind)
            output_file = target_dir / f'{_safe(item.shortcode)}_{asset.index}{suffix}'
            if kind == 'local':
                relative = parser._normalize_local_media_ref(asset.url)
                existing = (source_json.parent / relative).resolve()
                if not existing.is_relative_to(source_json.parent.resolve()):
                    raise ValueError('Path media keluar dari folder import')
                shutil.copyfile(existing, output_file)
            elif kind == 'embedded':
                parts = parser._data_uri_parts(asset.url)
                if not parts: raise ValueError('Media embedded tidak valid')
                output_file.write_bytes(base64.b64decode(parts[1], validate=True))
            elif kind == 'remote':
                if not asset.url.startswith('https://'):
                    raise ValueError('URL media import harus HTTPS')
                with urllib.request.urlopen(asset.url, timeout=45) as response, output_file.open('wb') as target:
                    shutil.copyfileobj(response, target, 262144)
            else:
                raise ValueError('Jenis media tidak dikenal')
            output.append({'path': str(output_file), 'name': output_file.name})
        callback.onProgress(int((i + 1) * 100 / max(len(selected), 1)), f'{i + 1}/{len(selected)}')
    if not output: raise ValueError('Tidak ada media untuk kategori ini')
    if output_mode == 'zip':
        archive = root / 'instagram' / 'Instagram-import.zip'
        # ZIP_STORED avoids recompressing videos and matches the bot's bundle output.
        with zipfile.ZipFile(archive, 'w', compression=zipfile.ZIP_STORED, allowZip64=True) as zip_out:
            for file in output:
                path = Path(file['path'])
                zip_out.write(path, path.relative_to(root / 'instagram').as_posix())
        return json.dumps([{'path': str(archive), 'name': archive.name}], ensure_ascii=False)
    if output_mode != 'folder':
        raise ValueError('Pilihan output import tidak dikenal')
    return json.dumps(output, ensure_ascii=False)


def _safe(value):
    return ''.join(c if c.isalnum() or c in '_-' else '_' for c in value or 'unknown')[:80]


def _extension(ref, kind):
    embedded = parser._data_uri_parts(ref)
    if embedded:
        return parser._DATA_MEDIA_MIMES[embedded[0]]
    suffix = Path(urllib.parse.urlparse(ref).path).suffix.lower()
    if suffix in {'.jpg', '.jpeg', '.png', '.webp', '.mp4', '.mov', '.m4v', '.m4a'}:
        return suffix
    return '.mp4' if kind == 'video' else '.jpg'

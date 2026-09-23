from __future__ import annotations

import asyncio
import base64
import binascii
import json
import logging
import mimetypes
import re
import shutil
import stat
import subprocess
import time
import zipfile
from dataclasses import dataclass, field
from datetime import UTC, date, datetime
from pathlib import Path, PurePosixPath
from typing import Any, Iterable
from urllib.parse import urlparse
from zoneinfo import ZoneInfo



logger = logging.getLogger(__name__)

WIB = ZoneInfo("Asia/Jakarta")
MAX_JSON_BYTES = 128 * 1024 * 1024
MAX_BUNDLE_BYTES = 2 * 1024 * 1024 * 1024
MAX_BUNDLE_FILES = 10_000
MAX_BUNDLE_UNCOMPRESSED_BYTES = 4 * 1024 * 1024 * 1024
INSTAGRAM_JSON_DOWNLOAD_CONCURRENCY = 4

_LOCAL_MEDIA_SUFFIXES = {
    ".jpg", ".jpeg", ".png", ".webp", ".mp4", ".mov", ".m4v", ".m4a",
}
_DATA_MEDIA_MIMES = {
    "image/jpeg": ".jpg",
    "image/png": ".png",
    "image/webp": ".webp",
    "video/mp4": ".mp4",
    "video/quicktime": ".mov",
    "audio/mp4": ".m4a",
    "audio/aac": ".aac",
    "audio/mpeg": ".mp3",
}
_DATA_URI_RE = re.compile(
    r"^data:(?P<mime>[a-z0-9.+-]+/[a-z0-9.+-]+);base64,(?P<payload>.*)$",
    re.IGNORECASE | re.DOTALL,
)

_SELECTION_LABELS = {
    "all": "Semua media",
    "feed": "Feed foto/carousel",
    "reels": "Reels",
    "stories": "Stories",
    "mentions": "Mention/Tagged",
}

_WRAPPER_KEYS = (
    "items",
    "posts",
    "results",
    "data",
    "stories",
    "reels",
    "mentions",
    "taggedPosts",
    "tagged_posts",
    "edges",
)

_DIRECT_MEDIA_KEYS = (
    "videoUrl",
    "video_url",
    "displayUrl",
    "display_url",
    "imageUrl",
    "image_url",
    "mediaUrl",
    "media_url",
    "thumbnailUrl",
    "thumbnail_url",
    "image",
)


class InstagramJsonError(RuntimeError):
    pass


@dataclass(slots=True)
class InstagramJsonAsset:
    url: str
    kind: str
    index: int
    partial: bool = False
    audio_url: str | None = None
    source_kind: str = "remote"


@dataclass(slots=True)
class InstagramJsonItem:
    item_id: str
    shortcode: str
    username: str
    primary_category: str
    timestamp: datetime | None
    mentioned_or_tagged: bool
    assets: list[InstagramJsonAsset]
    partial: bool = False
    source_url: str = ""

    @property
    def post_date(self) -> date | None:
        return self.timestamp.astimezone(WIB).date() if self.timestamp else None


@dataclass(slots=True)
class InstagramJsonParseResult:
    username: str
    items: list[InstagramJsonItem]
    source_entries: int
    ignored_entries: int
    warnings: list[str] = field(default_factory=list)

    def category_items(self, selection: str) -> list[InstagramJsonItem]:
        if selection == "all":
            return list(self.items)
        if selection == "mentions":
            return [item for item in self.items if item.mentioned_or_tagged]
        return [item for item in self.items if item.primary_category == selection]

    def category_count(self, selection: str) -> int:
        return len(self.category_items(selection))

    @property
    def counts(self) -> dict[str, int]:
        return {
            "all": len(self.items),
            "feed": self.category_count("feed"),
            "reels": self.category_count("reels"),
            "stories": self.category_count("stories"),
            "mentions": self.category_count("mentions"),
            "partial": sum(1 for item in self.items if item.partial),
            "assets": sum(len(item.assets) for item in self.items),
            "full_assets": sum(
                1 for item in self.items for asset in item.assets if not asset.partial
            ),
            "partial_assets": sum(
                1 for item in self.items for asset in item.assets if asset.partial
            ),
            "remote_assets": sum(
                1 for item in self.items for asset in item.assets
                if asset.source_kind == "remote"
            ),
            "local_assets": sum(
                1 for item in self.items for asset in item.assets
                if asset.source_kind == "local"
            ),
            "embedded_assets": sum(
                1 for item in self.items for asset in item.assets
                if asset.source_kind == "embedded"
            ),
        }

    def as_preview(self) -> dict[str, Any]:
        return {
            "username": self.username,
            "source_entries": self.source_entries,
            "ignored_entries": self.ignored_entries,
            "counts": self.counts,
            "warnings": list(self.warnings),
        }


@dataclass(slots=True)
class InstagramJsonBundlePreview:
    parsed: InstagramJsonParseResult
    json_member: str
    archive_files: int
    archive_uncompressed_bytes: int


def selection_label(selection: str) -> str:
    return _SELECTION_LABELS.get(selection, selection.title())


def _first_text(mapping: dict[str, Any], keys: Iterable[str]) -> str:
    for key in keys:
        value = mapping.get(key)
        if value is None:
            continue
        text = str(value).strip()
        if text and text.lower() not in {"none", "null", "n/a", "na"}:
            return text
    return ""


def _username_from_url(value: Any) -> str:
    try:
        parsed = urlparse(str(value or ""))
    except ValueError:
        return ""
    host = (parsed.hostname or "").lower()
    if "instagram.com" not in host:
        return ""
    parts = [part for part in parsed.path.split("/") if part]
    if not parts or parts[0].lower() in {
        "p", "reel", "reels", "stories", "explore", "accounts", "direct"
    }:
        return ""
    return parts[0].lstrip("@").strip()


def _entry_username(entry: dict[str, Any]) -> str:
    direct = _first_text(
        entry,
        (
            "ownerUsername",
            "owner_username",
            "username",
            "profileName",
            "profile_name",
            "authorUsername",
            "author_username",
        ),
    )
    if direct:
        return direct.lstrip("@")
    for key in ("owner", "user", "author", "profile"):
        nested = entry.get(key)
        if isinstance(nested, dict):
            nested_name = _first_text(nested, ("username", "userName", "handle"))
            if nested_name:
                return nested_name.lstrip("@")
    for key in ("inputUrl", "input_url", "profileUrl", "profile_url"):
        from_url = _username_from_url(entry.get(key))
        if from_url:
            return from_url
    return ""


def _parse_timestamp(value: Any) -> datetime | None:
    if value is None or value == "":
        return None
    if isinstance(value, (int, float)):
        raw = float(value)
        if raw > 10_000_000_000:
            raw /= 1000.0
        try:
            return datetime.fromtimestamp(raw, tz=UTC)
        except (OverflowError, OSError, ValueError):
            return None
    text = str(value).strip()
    if not text:
        return None
    if re.fullmatch(r"\d+(?:\.\d+)?", text):
        try:
            return _parse_timestamp(float(text))
        except ValueError:
            return None
    normalized = text.replace("Z", "+00:00")
    try:
        parsed = datetime.fromisoformat(normalized)
    except ValueError:
        for fmt in ("%Y-%m-%d", "%Y%m%d", "%Y-%m-%d %H:%M:%S"):
            try:
                parsed = datetime.strptime(text, fmt)
                break
            except ValueError:
                continue
        else:
            return None
    if parsed.tzinfo is None:
        parsed = parsed.replace(tzinfo=UTC)
    return parsed.astimezone(UTC)


def _entry_timestamp(entry: dict[str, Any]) -> datetime | None:
    for key in (
        "timestamp",
        "takenAt",
        "taken_at",
        "createdAt",
        "created_at",
        "date",
        "publishedAt",
        "published_at",
        "uploadDate",
        "upload_date",
    ):
        parsed = _parse_timestamp(entry.get(key))
        if parsed is not None:
            return parsed
    return None


def _is_direct_media_url(value: Any) -> bool:
    text = str(value or "").strip()
    if not text.startswith(("http://", "https://")):
        return False
    try:
        parsed = urlparse(text)
    except ValueError:
        return False
    host = (parsed.hostname or "").lower()
    path = parsed.path.lower()
    if "instagram.com" in host and not (
        "cdninstagram.com" in host or "fbcdn.net" in host
    ):
        return False
    return bool(
        "cdninstagram.com" in host
        or "fbcdn.net" in host
        or path.endswith((".jpg", ".jpeg", ".png", ".webp", ".mp4", ".mov", ".m4v"))
        or "/v/t" in path
        or "/o1/v/" in path
    )


def _data_uri_parts(value: Any) -> tuple[str, str] | None:
    text = str(value or "").strip()
    match = _DATA_URI_RE.fullmatch(text)
    if not match:
        return None
    mime = match.group("mime").lower()
    if mime not in _DATA_MEDIA_MIMES:
        return None
    return mime, match.group("payload")


def _normalize_local_media_ref(value: Any) -> str:
    text = str(value or "").strip().replace("\\", "/")
    if not text or len(text) > 1024 or "\x00" in text:
        return ""
    if text.startswith(("/", "~")) or re.match(r"^[A-Za-z]:", text):
        return ""
    if re.match(r"^[A-Za-z][A-Za-z0-9+.-]*:", text):
        return ""
    path = PurePosixPath(text)
    if any(part in {"", ".", ".."} for part in path.parts):
        return ""
    if path.suffix.lower() not in _LOCAL_MEDIA_SUFFIXES:
        return ""
    return path.as_posix()


def _media_source_kind(value: str) -> str:
    if _is_direct_media_url(value):
        return "remote"
    if _data_uri_parts(value) is not None:
        return "embedded"
    if _normalize_local_media_ref(value):
        return "local"
    return "invalid"


def _extract_url(value: Any) -> str:
    if isinstance(value, str):
        text = value.strip()
        if _is_direct_media_url(text) or _data_uri_parts(text) is not None:
            return text
        return _normalize_local_media_ref(text)
    if isinstance(value, dict):
        for key in ("url", "src", "displayUrl", "videoUrl", "imageUrl", "path", "file"):
            candidate = value.get(key)
            if isinstance(candidate, str):
                extracted = _extract_url(candidate)
                if extracted:
                    return extracted
    return ""


def _entry_context_candidates(data: Any) -> list[tuple[dict[str, Any], str]]:
    if isinstance(data, list):
        return [(entry, "root") for entry in data if isinstance(entry, dict)]
    if not isinstance(data, dict):
        return []

    candidates: list[tuple[dict[str, Any], str]] = []
    seen_objects: set[int] = set()

    def add_entry(entry: dict[str, Any], context: str) -> None:
        marker = id(entry)
        if marker in seen_objects:
            return
        seen_objects.add(marker)
        candidates.append((entry, context))

    def inspect_wrapper(value: Any, context: str, depth: int = 0) -> None:
        if depth > 2:
            return
        if isinstance(value, list):
            for entry in value:
                if isinstance(entry, dict):
                    add_entry(entry, context)
            return
        if not isinstance(value, dict):
            return
        if _looks_like_entry(value):
            add_entry(value, context)
        for key in _WRAPPER_KEYS:
            nested = value.get(key)
            if isinstance(nested, (list, dict)):
                nested_context = key.lower()
                inspect_wrapper(nested, nested_context, depth + 1)

    inspect_wrapper(data, "root")
    return candidates


def _looks_like_entry(entry: dict[str, Any]) -> bool:
    if any(key in entry for key in ("shortCode", "shortcode", "media_id", "productType")):
        return True
    if any(key in entry for key in _DIRECT_MEDIA_KEYS):
        return True
    url = _first_text(entry, ("url", "postUrl", "post_url"))
    return bool(url and "instagram.com" in url)


def _classify_entry(entry: dict[str, Any], context: str) -> str:
    context_lower = context.lower()
    type_text = _first_text(entry, ("type", "mediaType", "media_type", "productType", "product_type")).lower()
    product_type = _first_text(entry, ("productType", "product_type")).lower()
    source_url = _first_text(entry, ("url", "postUrl", "post_url", "inputUrl", "input_url")).lower()
    if (
        "stor" in context_lower
        or bool(entry.get("isStory") or entry.get("is_story"))
        or "story" in type_text
        or product_type in {"story", "stories"}
        or "/stories/" in source_url
    ):
        return "stories"
    if (
        "reel" in context_lower
        or product_type in {"clips", "clip", "reel", "reels"}
        or type_text == "reel"
        or "/reel/" in source_url
    ):
        return "reels"
    return "feed"


def _has_mentions_or_tags(entry: dict[str, Any], context: str) -> bool:
    if "mention" in context.lower() or "tagged" in context.lower():
        return True
    for key in (
        "mentions",
        "taggedUsers",
        "tagged_users",
        "taggedUser",
        "tagged_user",
        "coauthorProducers",
        "coauthor_producers",
    ):
        value = entry.get(key)
        if isinstance(value, (list, dict)) and bool(value):
            return True
        if isinstance(value, str) and value.strip():
            return True
    return False


def _audio_url(entry: dict[str, Any]) -> str | None:
    for key in ("audioUrl", "audio_url", "musicUrl", "music_url"):
        candidate = _extract_url(entry.get(key))
        if candidate:
            return candidate
    return None


def _append_asset(
    assets: list[InstagramJsonAsset],
    seen: set[str],
    *,
    url: str,
    kind: str,
    partial: bool = False,
    audio_url: str | None = None,
) -> None:
    if not url or url in seen:
        return
    seen.add(url)
    assets.append(
        InstagramJsonAsset(
            url=url,
            kind=kind,
            index=len(assets) + 1,
            partial=partial,
            audio_url=audio_url if kind == "video" else None,
            source_kind=_media_source_kind(url),
        )
    )


def _extract_assets(entry: dict[str, Any]) -> list[InstagramJsonAsset]:
    assets: list[InstagramJsonAsset] = []
    seen: set[str] = set()
    parent_audio = _audio_url(entry)

    child_posts = entry.get("childPosts") or entry.get("child_posts") or entry.get("children")
    if isinstance(child_posts, list):
        for child in child_posts:
            if not isinstance(child, dict):
                continue
            video = _extract_url(child.get("videoUrl") or child.get("video_url"))
            if video:
                _append_asset(
                    assets,
                    seen,
                    url=video,
                    kind="video",
                    audio_url=_audio_url(child) or parent_audio,
                )
                continue
            image = _extract_url(
                child.get("displayUrl")
                or child.get("display_url")
                or child.get("imageUrl")
                or child.get("image_url")
                or child.get("image")
            )
            if image:
                _append_asset(assets, seen, url=image, kind="image")

    video = _extract_url(entry.get("videoUrl") or entry.get("video_url"))
    if video:
        _append_asset(
            assets,
            seen,
            url=video,
            kind="video",
            audio_url=parent_audio,
        )

    images = entry.get("images")
    if isinstance(images, list):
        for raw in images:
            image = _extract_url(raw)
            if image:
                _append_asset(assets, seen, url=image, kind="image")

    display = _extract_url(
        entry.get("displayUrl")
        or entry.get("display_url")
        or entry.get("imageUrl")
        or entry.get("image_url")
        or entry.get("mediaUrl")
        or entry.get("media_url")
    )
    type_text = _first_text(entry, ("type", "mediaType", "media_type")).lower()
    if display and not assets:
        _append_asset(
            assets,
            seen,
            url=display,
            kind="video" if "video" in type_text else "image",
            partial="video" in type_text,
            audio_url=parent_audio,
        )
    elif display and "video" not in type_text:
        _append_asset(assets, seen, url=display, kind="image")

    # Apify restricted/partial records often expose only `image`.
    fallback = _extract_url(entry.get("image"))
    if fallback and not assets:
        _append_asset(assets, seen, url=fallback, kind="image", partial=True)

    return assets


def _item_identifier(entry: dict[str, Any], index: int) -> tuple[str, str]:
    shortcode = _first_text(
        entry,
        ("shortCode", "shortcode", "short_code", "code", "mediaCode", "media_code"),
    )
    item_id = _first_text(
        entry,
        ("id", "media_id", "mediaId", "pk", "storyId", "story_id"),
    )
    if not shortcode:
        source_url = _first_text(entry, ("url", "postUrl", "post_url"))
        match = re.search(r"/(?:p|reel|reels)/([^/?#]+)", source_url)
        if match:
            shortcode = match.group(1)
    if not item_id:
        item_id = shortcode or f"item-{index:04d}"
    if not shortcode:
        shortcode = item_id
    return item_id, shortcode


def parse_instagram_json(data: Any) -> InstagramJsonParseResult:
    candidates = _entry_context_candidates(data)
    if not candidates:
        raise InstagramJsonError("JSON tidak berisi daftar item Instagram yang dikenali")

    items: list[InstagramJsonItem] = []
    usernames: list[str] = []
    ignored = 0
    warnings: list[str] = []
    seen_item_keys: set[tuple[str, str]] = set()

    for index, (entry, context) in enumerate(candidates, start=1):
        assets = _extract_assets(entry)
        if not assets:
            ignored += 1
            continue
        item_id, shortcode = _item_identifier(entry, index)
        category = _classify_entry(entry, context)
        username = _entry_username(entry)
        if username:
            usernames.append(username)
        dedupe_key = (shortcode.lower(), category)
        if dedupe_key in seen_item_keys:
            # Wrapper schemas can expose the same entry in multiple arrays.
            continue
        seen_item_keys.add(dedupe_key)
        source_url = _first_text(entry, ("url", "postUrl", "post_url", "inputUrl", "input_url"))
        partial = any(asset.partial for asset in assets)
        items.append(
            InstagramJsonItem(
                item_id=item_id,
                shortcode=shortcode,
                username=username,
                primary_category=category,
                timestamp=_entry_timestamp(entry),
                mentioned_or_tagged=_has_mentions_or_tags(entry, context),
                assets=assets,
                partial=partial,
                source_url=source_url,
            )
        )

    if not items:
        raise InstagramJsonError(
            "JSON terdeteksi sebagai data Instagram, tetapi tidak memiliki URL media langsung"
        )

    username_counts: dict[str, int] = {}
    for username in usernames:
        key = username.strip().lstrip("@")
        if key:
            username_counts[key] = username_counts.get(key, 0) + 1
    username = max(username_counts, key=username_counts.get) if username_counts else "unknown"
    for item in items:
        if not item.username:
            item.username = username

    if ignored:
        warnings.append(f"{ignored} entri tidak memiliki URL media langsung dan akan dilewati")
    if any(item.partial for item in items):
        warnings.append("Sebagian item hanya menyediakan thumbnail/preview parsial")

    items.sort(
        key=lambda item: (
            item.timestamp is not None,
            item.timestamp or datetime.min.replace(tzinfo=UTC),
            item.shortcode,
        ),
        reverse=True,
    )
    return InstagramJsonParseResult(
        username=username,
        items=items,
        source_entries=len(candidates),
        ignored_entries=ignored,
        warnings=warnings,
    )


def parse_instagram_json_bytes(raw: bytes) -> InstagramJsonParseResult:
    if len(raw) > MAX_JSON_BYTES:
        raise InstagramJsonError(
            f"JSON terlalu besar ({len(raw) / 1024 / 1024:.1f} MiB); "
            f"maksimum {MAX_JSON_BYTES / 1024 / 1024:.0f} MiB"
        )
    try:
        data = json.loads(raw.decode("utf-8-sig"))
    except UnicodeDecodeError as exc:
        raise InstagramJsonError("JSON harus memakai encoding UTF-8") from exc
    except json.JSONDecodeError as exc:
        raise InstagramJsonError(
            f"Format JSON tidak valid pada baris {exc.lineno}, kolom {exc.colno}"
        ) from exc
    return parse_instagram_json(data)


def _safe_zip_member_name(name: str) -> str:
    raw = str(name or "").replace("\\", "/")
    if raw.startswith("/") or re.match(r"^[A-Za-z]:", raw):
        raise InstagramJsonError(f"Path ZIP absolut tidak diizinkan: {name}")
    normalized = raw.rstrip("/")
    if not normalized or "\x00" in normalized:
        raise InstagramJsonError("Bundle ZIP memiliki nama file kosong/tidak valid")
    path = PurePosixPath(normalized)
    if any(part in {"", ".", ".."} for part in path.parts):
        raise InstagramJsonError(f"Path ZIP tidak aman: {name}")
    return path.as_posix()


def _validated_zip_infos(archive: zipfile.ZipFile) -> dict[str, zipfile.ZipInfo]:
    infos: dict[str, zipfile.ZipInfo] = {}
    total_uncompressed = 0
    members = archive.infolist()
    if len(members) > MAX_BUNDLE_FILES:
        raise InstagramJsonError(
            f"Bundle ZIP berisi terlalu banyak entri ({len(members)}); maksimum {MAX_BUNDLE_FILES}"
        )
    for info in members:
        if info.flag_bits & 0x1:
            raise InstagramJsonError("Bundle ZIP terenkripsi tidak didukung")
        name = _safe_zip_member_name(info.filename)
        mode = (info.external_attr >> 16) & 0xFFFF
        if stat.S_ISLNK(mode):
            raise InstagramJsonError(f"Symlink di dalam ZIP tidak diizinkan: {name}")
        if info.is_dir():
            continue
        if name in infos:
            raise InstagramJsonError(f"Nama file duplikat di ZIP: {name}")
        total_uncompressed += int(info.file_size)
        if total_uncompressed > MAX_BUNDLE_UNCOMPRESSED_BYTES:
            raise InstagramJsonError("Ukuran ekstraksi bundle ZIP melewati batas 4 GiB")
        infos[name] = info
    return infos


def _read_zip_member_limited(
    archive: zipfile.ZipFile, info: zipfile.ZipInfo, max_bytes: int
) -> bytes:
    if info.file_size > max_bytes:
        raise InstagramJsonError(
            f"JSON dalam ZIP terlalu besar ({info.file_size / 1024 / 1024:.1f} MiB)"
        )
    with archive.open(info, "r") as source:
        raw = source.read(max_bytes + 1)
    if len(raw) > max_bytes:
        raise InstagramJsonError("JSON dalam ZIP melewati batas ukuran")
    return raw


def _resolve_bundle_member(json_member: str, local_ref: str) -> str:
    json_dir = PurePosixPath(json_member).parent
    return _safe_zip_member_name((json_dir / PurePosixPath(local_ref)).as_posix())


def inspect_instagram_bundle_zip(zip_path: Path) -> InstagramJsonBundlePreview:
    try:
        archive = zipfile.ZipFile(zip_path, "r")
    except (OSError, zipfile.BadZipFile) as exc:
        raise InstagramJsonError(f"ZIP tidak valid: {exc}") from exc
    with archive:
        infos = _validated_zip_infos(archive)
        candidates = [name for name in infos if name.lower().endswith(".json")]
        candidates.sort(
            key=lambda name: (
                0 if name.lower().endswith("_local.json") else 1,
                0 if "bot_compatible" in name.lower() else 1,
                0 if name.lower().endswith("_embedded.json") else 1,
                name.lower(),
            )
        )
        if not candidates:
            raise InstagramJsonError("ZIP tidak berisi file JSON")
        last_error: Exception | None = None
        for json_member in candidates:
            try:
                raw = _read_zip_member_limited(archive, infos[json_member], MAX_JSON_BYTES)
                parsed = parse_instagram_json_bytes(raw)
                for item in parsed.items:
                    for asset in item.assets:
                        refs = [asset.url]
                        if asset.audio_url:
                            refs.append(asset.audio_url)
                        for ref in refs:
                            if _media_source_kind(ref) != "local":
                                continue
                            member = _resolve_bundle_member(json_member, ref)
                            if member not in infos:
                                raise InstagramJsonError(
                                    f"Media lokal tidak ditemukan di ZIP: {member}"
                                )
                return InstagramJsonBundlePreview(
                    parsed=parsed,
                    json_member=json_member,
                    archive_files=len(infos),
                    archive_uncompressed_bytes=sum(info.file_size for info in infos.values()),
                )
            except InstagramJsonError as exc:
                last_error = exc
                continue
        raise InstagramJsonError(
            f"Tidak ada JSON Instagram kompatibel di ZIP: {last_error or 'format tidak dikenali'}"
        )


def extract_instagram_bundle_zip(
    zip_path: Path,
    destination: Path,
    expected_json_member: str | None = None,
) -> Path:
    preview = inspect_instagram_bundle_zip(zip_path)
    if expected_json_member and preview.json_member != expected_json_member:
        raise InstagramJsonError(
            "JSON utama dalam bundle berubah sejak preview; import dihentikan"
        )
    destination.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(zip_path, "r") as archive:
        infos = _validated_zip_infos(archive)
        wanted = {preview.json_member}
        for item in preview.parsed.items:
            for asset in item.assets:
                refs = [asset.url]
                if asset.audio_url:
                    refs.append(asset.audio_url)
                for ref in refs:
                    if _media_source_kind(ref) == "local":
                        wanted.add(_resolve_bundle_member(preview.json_member, ref))
        for member in sorted(wanted):
            info = infos.get(member)
            if info is None:
                raise InstagramJsonError(f"File bundle hilang: {member}")
            target = destination.joinpath(*PurePosixPath(member).parts)
            target.parent.mkdir(parents=True, exist_ok=True)
            with archive.open(info, "r") as source, target.open("wb") as output:
                shutil.copyfileobj(source, output, length=1024 * 1024)
    return destination.joinpath(*PurePosixPath(preview.json_member).parts)


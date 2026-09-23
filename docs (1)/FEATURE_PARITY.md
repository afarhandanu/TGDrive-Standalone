# TgDriveBot v3.6.12-r24 → Android feature audit

This table is an implementation status, not a promise that every extractor works on every site. The Python bot's Linux services cannot simply be copied into the Android APK.

| Bot capability | Android status |
|---|---|
| Direct HTTP(S), yt-dlp URLs, file naming by creator | Implemented with local extraction and direct fallback; site-specific routing requires device tests |
| Quality, audio, subtitles, thumbnail, metadata, bulk count | Implemented for yt-dlp-supported URLs; formats requiring FFmpeg merging are not available |
| Instagram and X login-required posts | WebView cookies passed to yt-dlp; login and private-post compatibility requires device tests |
| Instagram Stories picker/download-all | Not implemented; direct story links depend on yt-dlp support |
| Instagram JSON/ZIP import, categories, embedded/local/remote media | Implemented using the v3.6.12 parser; date filters, selectable output ZIP/folder and video/audio mux remain |
| X profile API backend choices and X date selection | Not implemented |
| Facebook profile bulk and Instagram all-posts safe access modes | Only generic yt-dlp profile/playlist URLs; specialized bot modes not implemented |
| Telegram forwarded files and direct Telegram file fetching | No Telegram bot by design; Android Share link/file and local file picker replace user-initiated file input |
| Torrent/magnet selection via aria2 | Not implemented on Android |
| Gallery destination | Implemented using MediaStore |
| Drive destination and Gallery + Drive | Implemented via on-device Google authorization and resumable upload |
| Drive browse, search, folder creation, rename, trash, public link | Implemented for authorized account; multi-account switching, revoke link, star, move, duplicate policy and pagination beyond 1000 files remain |
| Queue, progress, cancel, retry, persistent jobs | Progress, cancel, retry of the last failed/cancelled request and limited persisted history implemented; durable queue recovery remains |
| Generic website session accounts and manual cookie import | Not implemented |
| Admin, multi-user roles, limits, broadcasts, backup and restore | Bot/server administration is out of scope for a single-user downloader app |

The actual source above must be tested with Instagram, X, Facebook and Drive accounts before any compatibility guarantee. Prefer filing results in `CHANGELOG.md`; do not change README for bug fixes.

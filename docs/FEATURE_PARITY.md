# TgDriveBot v3.6.12-r24 → Android feature audit

This table is an implementation status, not a promise that every extractor works on every site. The Python bot's Linux services cannot simply be copied into the Android APK.

| Bot capability | Android status |
|---|---|
| Direct HTTP(S), yt-dlp URLs, file naming by creator | Implemented with local extraction and direct fallback; site-specific routing requires device tests |
| Quality, audio, video-only, subtitles, thumbnail, metadata, bulk count | Implemented for yt-dlp-supported URLs, including 2160p/1440p/480p; formats requiring FFmpeg merging are not available |
| Instagram and X login-required posts | WebView cookies and encrypted session fallback passed to yt-dlp; login and private-post compatibility requires device tests |
| Instagram Stories picker/download-all | Gallery mode can attempt Story URLs with account cookies and batch limit; preview/picker and reliable all-stories extraction remain unported |
| Instagram JSON/ZIP import, categories, embedded/local/remote media, date filters, folder or ZIP output | Implemented using the v3.6.12 parser; video/audio mux remains unavailable |
| X profile media and date selection | Opt-in gallery-dl mode with post limit and date range; backend selector and account-specific API fallback remain unported |
| Facebook profile bulk and Instagram carousel/profile | Opt-in gallery-dl mode with content choice (Facebook) and per-post folders; Android device verification needed. Bot's anonymous-then-account fallback and Story picker remain unported |
| Telegram forwarded files and direct Telegram file fetching | No Telegram bot by design; Android Share link/file and local file picker replace user-initiated file input |
| Torrent/magnet selection via aria2 | Not implemented on Android |
| Gallery destination | Implemented using MediaStore |
| Drive destination and Gallery + Drive | Implemented via on-device Google authorization and resumable upload |
| Drive browse, search, folder creation, rename, trash, public/revoke link, star, move, skip same-name uploads, upload MD5 check | Implemented; duplicate skip and MD5 verification are opt-in, listing and search are paginated up to 10000 files |
| Queue, progress, cancel, retry, persistent jobs | Added pause between tasks and interrupted-job detection; interrupted URL jobs can be retried after restart. Full automatic queue recovery and retry of local/import files remain |
| Generic website session accounts and manual cookie import | HTTPS login URL, saved site buttons, WebView cookies and Netscape cookies.txt import for the target HTTPS host; some sites block embedded login |
| Backup and restore | Portable history and saved-site backup is implemented. Device-bound login sessions are intentionally excluded. |
| Admin, multi-user roles, broadcasts, dashboard | Bot/server administration has no equivalent in a single-user offline-capable app |

Version 1.2.0-alpha adds optional anonymous access, completed-URL detection, Drive same-name skip, and a separate gallery-dl profile/carousel pipeline. These options default to the prior download behavior. Story preview/picker, profile API choice and server-specific safe retry, magnet/torrent via aria2, and FFmpeg mux still need Android packaging and device verification before they can be marked complete.

The actual source above must be tested with Instagram, X, Facebook and Drive accounts before any compatibility guarantee. Prefer filing results in `CHANGELOG.md`; do not change README for bug fixes.

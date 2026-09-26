# Changelog

## 1.3.13-alpha (21)

- Fixed Android/Chaquopy temporary-file handling so yt-dlp never writes format-check temp files to the read-only `/` filesystem.
- Standardized website media paths for Local and Google Drive as `site/username/category/item-id/media`.
- Added the item-ID directory to yt-dlp, Instagram Stories/imports, TikTok embed/fallback, X, Instagram, and Facebook album downloads.

## 1.3.12-alpha (20)
- Replace the invalid blank TikTok `app_info` workaround. TikTok now retries yt-dlp with two browser identities used for the September 2026 webpage regression, then falls back to TikTok's public player/embed metadata before gallery-dl.
- Add an on-device TikTok post fallback which extracts public video/photo-carousel media and soundtrack from TikTok's own server-rendered metadata; it does not use a third-party download service.
- Update yt-dlp to nightly `2026.9.16.232951.dev0` and give the gallery-dl fallback the compatible TikTok User-Agent and Referer.
- Preserve the 1.3.10 combine/separate, watermark, and FFmpeg picker behavior. GitHub Actions workflow remains unchanged.

## 1.3.11-alpha (19)
- Fixed TikTok `Unexpected response from webpage request`: updated yt-dlp to 2026.08.19 and try TikTok's app API path before the webpage path.
- TikTok fallback now accepts successfully downloaded media even when gallery-dl reports a non-zero side-item status.
- `vt.tiktok.com` / `vm.tiktok.com` short links use a more compatible redirect user-agent and parent-domain TikTok session cookies are exported.
- GitHub Actions workflow is unchanged. The 1.3.10 combine/separate, watermark, and FFmpeg picker features are preserved.


## 1.3.10-alpha (18)

- Add TikTok photo/carousel output controls: combine all downloaded images with the soundtrack into one MP4, or keep the original images and audio as separate files.
- Add TikTok video watermark selection. The no-watermark path avoids TikTok watermarked formats; the watermark path requests TikTok's native watermarked format when the extractor exposes it.
- Extend the on-device FFmpeg tool to accept one or more images plus one audio file in addition to video + audio. Selected media is staged and queued from Files without jumping back to Download.
- Add dedicated media-combine, separate-media, watermark-on, and watermark-off vector icons, plus bilingual UI/status text and regression coverage for the TikTok modes.

## 1.3.9-alpha (17)

- Replace tiny navigation symbols with six distinct vector icons. Increase icon, label, and touch target sizes without changing the existing menus.
- When yt-dlp fails on TikTok, retry the public post with gallery-dl's own media extractor; resolve TikTok short links and use the saved site cookies when available.
- Report both extractor failures if TikTok still blocks the request. Other sites retain their existing download paths.

## 1.3.8-alpha (16)

- Add persistent Bahasa Indonesia and English selection in Options for app screens, dialogs, notifications, and app-authored status messages.
- Provide complete offline Features and Changelog documents in both languages. Preserve raw site/library diagnostics for troubleshooting.
- Redesign Device administration with download summary, queue, account, and storage cards; fix system-bar spacing and action styling. Remove irrelevant bot administration text.
- Restyle About, Features, and Changelog navigation. Switching language preserves the current link, selected files, and download preferences.
- Rewrite the README in English and Bahasa Indonesia with a product introduction, quick start, menu guide, storage details, build instructions, and limitations.

## 1.3.7-alpha (15)

- Fix the adaptive icon background that caused the default Android icon to appear, and add fallback bitmap icons.
- Move app information to Info with offline About, Features, and Changelog sections.
- Add migration from legacy TGDrive folders to The Great Drive in local storage and Drive, with a file count before starting.
- Offer keeping originals or removing them after copy verification. Local storage checks size and SHA-256; Drive checks size and MD5 before moving originals to Trash.
- Reuse identical copies, preserve different content, and update Drive history links before removing sources. Empty source folders remain.
- Show migration progress, cancellation, summaries, and errors. Interrupted migrations can be run again.
- Local migration includes files owned by this installation. Drive searches the old folder in My Drive or the selected parent; Google documents and shortcuts are skipped.

## 1.3.6-alpha (14)

- Rebrand the app as The Great Drive with an Android display name, launcher icon, Download banner, and About information showing the installed version.
- Update project, GitHub Actions workflow, APK, artifact, interface, notification, and setup documentation names.
- Save new downloads in The Great Drive folders locally and in Google Drive. Existing files remain in legacy TGDrive folders.
- Keep com.tgdrive.mobile, the release certificate, OAuth configuration, signing secrets, sessions, settings, and backup format compatible.

## 1.3.5-alpha (13)

- Fix missing video Story media URLs when the extractor returns a synthetic ytdl: DASH URL. The Story picker requests merged video for a direct MP4.
- If DASH is still returned, use video_url from metadata for the same Story ID. Reject media from other IDs and preserve photo and regular download paths.
- Add regression coverage for ytdl: and direct video_url output. Merged MP4 resolution can differ from DASH merged with FFmpeg.

## 1.3.4-alpha (12)

- Fix Story selection downloading the final item even when another ID was selected. Resolve and save media by the selected Story ID.
- Fail with a reload instruction when the ID is unavailable or expired, without substituting the last Story or reporting false completion.
- Name selected photos, videos, and optional JSON metadata with their matching Story ID using the existing queue and local/Drive destinations.
- Add tests for three distinct Stories and an unavailable ID.

## 1.3.3-alpha (11)

- Add photo/video thumbnails to the Story picker. Tap to view a larger image or play video when a URL is available.
- Queue selected Stories as one batch, with separate results for each item.
- Always enqueue explicitly selected Stories even when skipping completed links is enabled. That option still applies to regular URL downloads.
- Allow downloads when previews are unavailable or their URLs have expired.

## 1.3.2-alpha (10)

- Initialize the gallery-dl extractor before reading Stories so Instagram session and API state are available.
- Save Story and download failure details on the device with cookies and tokens redacted. Read or share logs from Activity.
- Show Story failures in a dialog with Open log so messages are not truncated in a toast.

## 1.3.1-alpha (9)

- Split the interface into Download, Options, Accounts, Files, and Activity while preserving preferences, sessions, and queue behavior.
- Rename Gallery to Local because results also include JSON/ZIP. Keep internal values compatible with earlier history and settings.
- Summarize history in tappable job cards with file paths and Drive links. Display legacy history using the Local label too.
- Move queue controls to Activity, backups and administration to Accounts, and add a selected-file indicator.
- Wrap quality and category choices across rows and add system-bar spacing.

## 1.3.0-alpha (8)

- Remember destination, quality, item limit, Drive folder, profile mode, dates, and download options for links received through Android Share.
- Allow an empty item limit for unlimited items; positive values still limit playlists, profiles, and imports.
- Add Instagram photos through gallery-dl when no video format exists, and try photos in mixed posts while preserving the working video/Reel path.
- Add an active Story picker with multiple/all selection using the Instagram session.
- Download magnets, HTTPS .torrent URLs, and local .torrent files on-device with libtorrent4j while preserving file structure.
- Merge imported Instagram audio/video with FFmpeg and offer merging one local MP4 and audio file. The bundled FFmpeg integration requires arm64.
- Add device administration for history, accounts, queue status, pause, cancellation, cache cleanup, and confirmed history deletion.

## 1.2.0-alpha

- Add 2160p, 1440p, 480p, and video-only quality, plus optional gallery-dl mode for Instagram albums/profiles, Facebook profiles, and X timelines. Add Facebook photo/video and date filters.
- Add date filters and an uncompressed single-ZIP output option for Instagram JSON/ZIP imports; folder output remains the default.
- Add Drive starring, public access revocation, moving files, same-name skipping, optional MD5 verification, and paginated listing/search.
- Add cookies.txt import for HTTPS sites, anonymous downloads, and skipping previously completed links.
- Add between-job pausing, interrupted-job recognition and retry, and history/saved-site backups excluding cookies and Google tokens.

## 1.1.4-alpha

- Open the X home page without always restarting login. The Done button checks for an X session cookie before closing.
- Remember up to 24 HTTPS login sites and create buttons to reopen them. Long-press to remove a button; URL queries and fragments are not stored.

## 1.1.3-alpha

- Remember the last Google Drive account and request a fresh token when opening the app, with a reconnect prompt if access expires or is revoked. Tokens are not stored.
- Keep WebView cookies across restarts and a seven-day encrypted session-cookie fallback using Android Keystore. Pass both X and Twitter domains to the downloader.
- Add login for other HTTPS websites for use with supported extractors.

## 1.1.2-alpha

- Use the account name from Instagram Story URLs for folders, including when the extractor returns a numeric user ID.
- Parse Google authorization results before treating account selection as cancelled; display available error codes.
- Document the existing release certificate SHA-1 and Drive OAuth configuration.

## 1.1.1-alpha

- Confirm the selected Drive account, show the connected email, and allow account switching.
- Organize local and Drive output by site, account name, and media type under the then-current TGDrive folder.
- Persist status before refreshing the screen; show 100% only when saving to destinations finishes.
- Fix a duplicate local variable in History.record that prevented Java compilation.

## 1.1.0-alpha

- Run media extraction and storage on the device without requiring a separate application server.
- Add local website sessions, queueing, local media storage, uploads, and Drive browsing.
- Add Instagram JSON/ZIP import with category selection.
- Keep the release signing identity and use unique build artifact names.
- Fix Android SDK setup by using action v4 and explicitly installing the required SDK packages.

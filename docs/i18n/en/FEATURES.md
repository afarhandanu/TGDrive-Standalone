# The Great Drive features

## Download and save
- Paste links, receive links/files from Android Share, or select files from your phone.
- Save locally, to Google Drive, or both, organized by site/account/media type.
- Choose video quality or audio, and include subtitles, thumbnails, and JSON metadata.
- Leave the maximum item count empty for unlimited items. Your last settings are saved.

## Instagram and other websites
- Download videos, Reels, photos, and carousels through supported extractors.
- Choose Instagram Stories using thumbnails, previews, and multiple/all selection.
- Sign in to Instagram, X, or other HTTPS websites. Saved sites get buttons to reopen them.
- Import cookies.txt, and use profile/album mode and date filters where supported.
- Website support depends on the extractor and account access. Some content requires login.
- TikTok posts use a second extractor if yt-dlp fails; TikTok may still block both methods. Photo/carousel posts can be combined with their soundtrack into one MP4 or kept as separate images + audio. Video downloads can request no watermark or TikTok's native watermark when that format is available. The fallback may select a different quality or omit optional JSON metadata.

## Files and imports
- Import Instagram JSON/ZIP with categories, date ranges, and folder or ZIP output.
- Download magnet links, torrent URLs, and local .torrent files.
- Merge a video + audio file, or one/more images + audio into a slideshow MP4, using FFmpeg on arm64 devices. Manual merge selection stays in Files while the job is staged and queued.

## Google Drive
- Upload to a chosen folder with optional same-name skipping and checksum verification.
- Browse and search files, create folders, rename, move, star, and send files to Trash.
- Create or revoke public links from the Drive browser.

## Queue and device data
- Follow progress, cancel jobs, pause the queue, and retry failed downloads.
- Open result details and error logs; export and restore history and saved sites.
- Use Device administration to review job counts, queue, account, and temporary storage.
- Clear old job cache or history with confirmation. Downloaded files are kept.

## Move old folders
- Files includes migration from legacy TGDrive folders to The Great Drive, locally and in Drive.
- Keep originals: copy to the new folder and retain the source files.
- Remove originals: copy and verify content before deleting local originals or moving Drive originals to Trash.
- Preserve subfolders, reuse identical copies, and keep differing content without overwriting it.
- Follow migration progress and results; stop and rerun an interrupted migration.
- Local migration includes files owned by the current installation. Drive includes regular files in TGDrive under the selected parent; Google documents and shortcuts are skipped.
- Empty old folders remain. Files no longer owned by this installation need a file manager to move them.

## Language and app information
- Switch between English and Bahasa Indonesia in Options. The selection is saved on the device.
- App screens, dialogs, notifications, and app-authored status messages use the chosen language.
- Info contains About, Features, and Changelog in both languages, available offline.
- Website content, filenames, URLs, and raw provider diagnostics retain their original text.

## Connection and retry

- **Options → Connection & retry:** automatic recovery is on by default. For pasted links, use **Set retry for each link**; checked links get automatic recovery and unchecked links use manual retry. Each Story also has its own toggle in the picker.
- **Activity → Choose media to retry:** choose failed, cancelled or interrupted source items and set automatic recovery for each. Open a job's details → **Retry selected media** to choose individual unfinished files once extraction has produced an output list. Before extraction finishes, retry operates on the source link/Story; it cannot select undiscovered carousel or playlist entries.
- Story, direct-file and TikTok embed transfers retry temporary connection failures up to three times, using a new connection and 1/2/4-second delays. Extractor-managed downloads use their per-file/fragment retry settings. Provider fallback methods remain available. Persistent permission errors, expired Stories and certificate failures need attention instead of repeated retries.
- Completed output destinations are checkpointed. Retrying a failed Drive upload reuses cached media and does not republish a completed local copy. Clearing the app's cache removes this recovery data; download the source link again if needed.
- **Drive status:** the chip below the banner is green when the account is verified and the network is online, amber while connecting, and red when disconnected/offline. Tap it to open Accounts. It indicates account/network state, not a continuous Drive-server health probe.
- **Accounts → Disconnect Drive:** removes the saved connection from this app without deleting files or revoking Google account permissions. A chunk already accepted by Drive may finish; later chunks stop. Reconnect before retrying unfinished uploads.


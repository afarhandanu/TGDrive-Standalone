# TGDrive Standalone Android

An Android downloader inspired by the capabilities of TgDriveBot v3.6.12-r24. All downloads and uploads in this project run on the phone. The app does not connect to the Telegram bot, CT 105, or the old `/api/v1` server bridge. `Mone-v1.1.1.apk` was used only as a behavioral reference; its code and visual assets are not included.

## Implemented in this source

- Android Share target for links and files, input for up to 50 URLs, and local file picker; a local foreground queue with progress, cancellation, retry of failed URL requests and recent activity.
- `yt-dlp` media extraction inside the APK, direct file fallback, quality selection, profile or playlist item limit, optional subtitles, thumbnails and JSON metadata.
- Local WebView sessions for Instagram and X. Cookies are exported to a private temporary Netscape file for a download, then deleted when the job ends.
- Instagram JSON or ZIP import using the bot's parser, with All/Feed/Reels/Stories/Tagged selection and local, embedded or remote media extraction.
- Three destinations: Gallery (MediaStore), Google Drive (resumable upload), or both. Video, photo and audio appear in their corresponding media collections; other files appear in Downloads/TGDrive.
- Google Drive browser for folders, search, rename, trash and public links.
- Fixed `com.tgdrive.mobile` package ID, release signing through the existing keystore secrets, and unique GitHub Actions artifact names.

## Build and sign

Push the **contents** of this directory to a GitHub repository. Set the four repository secrets described in `docs/SIGNING.md`, then start `Build TGDrive APK` from Actions. Download the artifact named `TGDrive-v<version>-vc<code>-run<run>-<commit>`.

Google Drive requires an Android OAuth client for package `com.tgdrive.mobile` and the **SHA-1 of the existing release signing certificate**. Enable the Drive API, configure the OAuth consent screen, and add your Google account as a test user if the app is in testing. The app requests Drive authorization when Drive is selected; do not put a web client secret in the APK.

The release workflow refuses to sign with a fresh key. `VERSION_CODE` must increase for an installable upgrade. `VERSION_NAME` is the displayed version. The existing release key stays private and outside this source archive.

## Development policy

This README changes only for a feature, setup or architecture change. Bug-fix notes belong in `CHANGELOG.md`. The current feature audit in `docs/FEATURE_PARITY.md` lists capabilities that still need Android implementations. Do not describe this source as complete feature parity until those entries have been implemented and tested on a device.

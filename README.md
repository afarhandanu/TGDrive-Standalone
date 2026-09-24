# The Great Drive

The Great Drive is an Android downloader inspired by the capabilities of TgDriveBot v3.6.12-r24. All downloads and uploads in this project run on the phone. The app does not connect to the Telegram bot, CT 105, or the old `/api/v1` server bridge. `Mone-v1.1.1.apk` was used only as a behavioral reference; its code and visual assets are not included.

## Implemented in this source

- Android Share target for links and files, input for up to 50 URLs, and local file picker; a local foreground queue with progress, cancellation, retry of failed URL requests and recent activity.
- `yt-dlp` media extraction inside the APK, direct file fallback, quality selection, profile or playlist item limit, optional subtitles, thumbnails and JSON metadata.
- Local WebView sessions for Instagram and X. Cookies are exported to a private temporary Netscape file for a download, then deleted when the job ends.
- Instagram JSON or ZIP import using the bot's parser, with All/Feed/Reels/Stories/Tagged selection and local, embedded or remote media extraction.
- Three destinations: local storage (MediaStore), Google Drive (resumable upload), or both. Video, photo and audio appear in their corresponding media collections; other files appear in Downloads/The Great Drive.
- The provided brand icon is the Android launcher icon. The matching banner appears on the download screen and under Info → Tentang. Info also includes Fitur and Changelog, available offline.
- Berkas includes old-folder migration for local storage and Drive. Choose to keep originals or remove them after verified copying; progress, cancellation and error reports are available.
- Google Drive browser for folders, search, rename, trash and public links.
- Fixed `com.tgdrive.mobile` package ID, release signing through the existing keystore secrets, and unique GitHub Actions artifact names.

## Build and sign

Push the **contents** of this directory to a GitHub repository. Set the four repository secrets described in `docs/SIGNING.md`, then start `Build The Great Drive APK` from Actions. Download the artifact named `The-Great-Drive-v<version>-vc<code>-run<run>-<commit>`.

Google Drive requires an Android OAuth client for package `com.tgdrive.mobile` and the **SHA-1 of the existing release signing certificate**. Enable the Drive API, configure the OAuth consent screen, and add your Google account as a test user if the app is in testing. The app requests Drive authorization when Drive is selected; do not put a web client secret in the APK.

The release workflow refuses to sign with a fresh key. `VERSION_CODE` must increase for an installable upgrade. `VERSION_NAME` is the displayed version. The existing release key stays private and outside this source archive.

## Rebrand and existing installs

Version 1.3.6-alpha (versionCode 14) installs as an update to the previous app when signed with the same release key. Keep the Android package `com.tgdrive.mobile`, the existing OAuth Android client and the `TGDRIVE_*` signing secrets. New downloads go into `The Great Drive/site/username/type/` inside the corresponding local media collection or under `My Drive/The Great Drive/`. Earlier files stay in their existing `TGDrive/` folders; this release does not move or delete them. Existing login sessions, saved settings, and backup files stay compatible.

From 1.3.7-alpha (versionCode 15), use **Berkas → Pindahkan folder Lokal / Google Drive** to migrate earlier files. Scan first, then choose **Simpan file lama** to retain originals, or **Hapus file lama setelah verifikasi**. Local copies are checked by size and SHA-256; Drive copies by size and MD5. Drive originals go to Trash, while local originals are deleted. Empty source directories remain. Local migration only sees files owned by this app installation; install the update over the existing app to preserve ownership. Drive migration searches `TGDrive` under My Drive or the parent folder ID currently set under Opsi, and leaves Google documents/shortcuts in place. Conflicting content is preserved as a separate file; a verified existing copy can be reused on retry.

`docs/FEATURES.md` and `CHANGELOG.md` are included in the APK by the `syncAppDocs` Gradle task. Update those documents to update the offline Info menu. The CI workflow also runs the migration deletion-safety checks before publishing the APK artifact.

## Development policy

This README changes only for a feature, setup or architecture change. Bug-fix notes belong in `CHANGELOG.md`. The current feature audit in `docs/FEATURE_PARITY.md` lists capabilities that still need Android implementations. Do not describe this source as complete feature parity until those entries have been implemented and tested on a device.

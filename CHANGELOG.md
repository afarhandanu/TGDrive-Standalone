# Changelog

## 1.2.0-alpha — Bot feature expansion (source)

- Add 2160p/1440p/480p/video-only formats and a separate, opt-in gallery-dl path for Instagram albums/profiles, Facebook profiles and X media timelines. Offer Facebook photo/video selection and date limits on profile batches. The normal yt-dlp path remains the default.
- Add Instagram JSON/ZIP import date range and optional uncompressed ZIP output; folder output remains the default.
- Add Drive star/unstar, revoke public access, browse folders to move items, optional skip for files with the same name and optional MD5 check after uploading. Paginate Drive listing and search.
- Add Netscape cookies.txt import for a selected HTTPS site, optional anonymous download and optional skip for completed links.
- Add between-job queue pause, interrupted-job recognition with retry, and portable history/saved-site backup. Login cookies and Google tokens are excluded from exported files.
- Bot-only Telegram administration, the interactive Story picker, torrent engine and media mux still require separate Android work and on-device tests; see `docs/FEATURE_PARITY.md`.

## 1.1.4-alpha — X session check and saved site buttons

- Open the X home page instead of always starting its login flow. Only close the login screen through the Done button when X's session cookie is visible; otherwise explain what is missing.
- Remember up to 24 HTTPS login sites locally and show a button for each on the main screen. Re-entering a site updates its button; holding a button removes it. URL query and fragment are not stored.


## 1.1.3-alpha — Persistent account sessions and other websites

- Remember the last authorized Google Drive account and silently request a fresh access token when opening the app. Show a reconnect prompt if consent has expired or was revoked. Tokens are never stored.
- Keep WebView site cookies across app restarts and save an encrypted, seven-day fallback in Android Keystore storage for session cookies; X and Twitter domains are both passed to the downloader.
- Let users open an HTTPS login page for another website. Its private WebView cookies are made available when downloading media from the same website, subject to extractor support.

## 1.1.2-alpha — Story owner and Drive authorization

- Use the account name in an Instagram Story URL for its folder, even when the extractor returns a numeric user ID.
- Parse the Google authorization result before treating an account selection as cancelled; show the returned error code when available.
- Document the existing release certificate SHA-1 and required Google Drive OAuth configuration.

## 1.1.1-alpha — Drive account and media folders

- Confirm Drive authorization with the selected account, display the connected email, and allow changing the account.
- Save files into `TGDrive/site/username/type/filename` in Gallery and Google Drive.
- Persist job status before refreshing the screen; show 100% only after destinations finish.
- Fix the duplicate local variable in `History.record` that prevented Java compilation.
- Fix the duplicate local variable in `History.record` that prevented Java compilation.

## 1.1.0-alpha — build workflow fix

- Updated Android SDK setup to v4 and installed the required SDK packages explicitly. This avoids the discontinued `tools` package requested during setup.

## 1.1.0-alpha — Android standalone foundation

- Replaced the former server-dependent Android client with on-device media extraction and output destinations.
- Added local login sessions, queue, Gallery publishing, Drive upload and Drive file browsing.
- Added Instagram JSON/ZIP import with category selection.
- Retained the TGDrive release signing identity and distinct workflow artifact names.

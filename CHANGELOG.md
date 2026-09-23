# Changelog

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

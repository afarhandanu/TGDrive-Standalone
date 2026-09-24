# Validation — 1.3.8-alpha / versionCode 16

## Completed in the editing workspace

- Checked 422 Indonesian/English translation pairs, matching placeholders and resource identifiers, coverage of literal UI translation lookups, and both complete offline documents.
- Passed 10 Java message-display checks, including Story IDs, multiline results, unchanged URLs, and filenames containing Indonesian words or placeholder-like text.
- Compiled and linked all Android resources with aapt2 against Android SDK 36. Inspected compiled values for language labels and intentional whitespace.
- Compiled the localization helpers, localized login/Drive browser/migration activities, migration service, and their available Java dependencies against the Android SDK.
- Reviewed the download-service changes against version 1.3.7: changes are limited to notification presentation and language metadata. Download extraction, queue inputs, saved states, destinations, and account keys retain their existing behavior in source.

## Not yet verified

A full Gradle APK build and on-device UI tests were not completed. The workspace could not retrieve all Java/native dependencies needed for a full build. The source includes GitHub Actions checks for translations, message display, and migration verification alongside the existing signed release build.

Before releasing the APK, run the workflow and the device checks in `docs/LOCALIZATION.md`. In particular, test switching languages with a selected file and an active/paused queue, force-stop/reopen persistence, and administration layout with enlarged text. This validation does not claim live Instagram or Google Drive testing.

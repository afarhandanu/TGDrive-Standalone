# Validation — 1.3.16-alpha / versionCode 24

## Automated checks

- 40 Python tests passed, including ten new transfer regression cases: SSL read failure, fresh request, partial-file cleanup, manual mode, bounded retries, certificate errors, truncated body, cancellation, completed-media preservation and a TikTok carousel slide transfer.
- 12 Java upload-range cases passed, including partial acknowledgement, large offsets and malformed/out-of-bounds ranges.
- Android resource compilation (aapt2 compile) passed.
- All 26 Java sources passed Java 17 syntax parsing.
- 495 paired Indonesian/English translations and offline documents passed the localization gate.
- Compared with the supplied vc23 source: app/build.gradle and .github/workflows/build-apk.yml are byte-for-byte unchanged. Package and signing settings are preserved.

## Build and device verification still required

Full Java/Android linking and APK assembly could not run here: a complete Android SDK and build dependencies were unavailable. Java syntax and resource compilation are not a substitute for a complete Android build. No live Instagram/TikTok account, Drive upload, Android network handover or device UI test was performed.

Build the release using the existing GitHub Actions workflow, then check:

1. Start with a saved Drive account: only the status chip appears; no connection toast. Toggle airplane mode and reconnect. Check green/amber/red state in both languages.
2. Disconnect during authorization and during an upload. Late authorization callbacks must not restore the account. Files already committed remain; subsequent chunks stop. Connect and manually retry.
3. Select several Stories, mixing automatic and manual settings. Interrupt connectivity during a media transfer. Only that media's transfer retries; cancellation during backoff stops promptly.
4. Open Activity and choose more than one failed source. Open a partial job's details and choose one unfinished output file. Other unselected pending files remain pending; already completed destinations are not written again.
5. Verify one Local + Drive task whose local save succeeds but upload fails: retry uses the cache, keeps the exact Story ID and does not make another local copy.
6. Verify TikTok combine/separate, watermark naming, FFmpeg merge, imports, migration and both local/Drive destinations with the existing flows.

## Running checks

```sh
python3 -m unittest discover -s tests -p 'test_*.py'
python3 tests/check_localization.py
mkdir -p /tmp/drive-range-tests
javac -d /tmp/drive-range-tests app/src/main/java/com/tgdrive/mobile/UploadRange.java tests/UploadRangeTest.java
java -cp /tmp/drive-range-tests com.tgdrive.mobile.UploadRangeTest
```

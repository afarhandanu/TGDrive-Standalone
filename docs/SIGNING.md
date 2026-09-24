# Stable release signing

Android treats two APKs with the same `applicationId` but different signatures as different trust chains. The Great Drive therefore never creates a release key inside CI.

Create or keep **one** release keystore forever, then add these GitHub repository secrets:

- `TGDRIVE_KEYSTORE_B64` — base64 of the `.jks` file, one line.
- `TGDRIVE_KEYSTORE_PASSWORD`
- `TGDRIVE_KEY_ALIAS`
- `TGDRIVE_KEY_PASSWORD`

The supplied workflow restores that same key on every run. If a secret is missing, the release build stops before Gradle instead of falling back to a random debug signature.

Use the **existing** `TGDrive-signing-key.jks` and its original credentials. The file name and `TGDRIVE_*` secret names remain unchanged for upgrade compatibility. The workflow verifies the keystore SHA-256 before signing, so another key cannot silently replace it. Do not generate a new release key for this package.

Never commit the keystore or passwords into a public repository. `.gitignore` already excludes common keystore names.

Keep `applicationId 'com.tgdrive.mobile'` unchanged for all future releases. Increase `VERSION_CODE` for every installable release update; update `VERSION_NAME` when you want a new human-facing version.

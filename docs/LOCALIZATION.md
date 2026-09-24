# Localization

The app supports `id` (default) and `en`, selected in Options and stored in the `app_language` preference. Existing download preferences, account sessions, job states, filenames, URLs, package ID, and signing configuration are independent of that setting.

## App text

1. Add or update the Indonesian and English entries in `tools/localization.json`. Canonical keys are stable source messages, not protocol identifiers.
2. Run `python tools/generate_localization.py` and commit the generated resource XML and `TranslationCatalog.java`.
3. Use `t("canonical text")` inside a `LocalizedActivity`. For services use `L10n.text(context, "canonical text")`.
4. Translate stored job messages only when displaying them using `L10n.message`. Templates use numbered placeholders such as `{0}`; preserve the same placeholders in each language. Never translate filenames, URL components, API keys, or stored state codes.
5. Run `python tests/check_localization.py` before building. CI runs this check too.

History and migration records retain canonical messages for compatibility. The display adapter handles exact messages and anchored templates. Captured values are inserted once without translation. Raw provider messages and stack traces remain available for diagnosis.

Switching language rebuilds MainActivity's views in place, restoring saved controls while retaining the entered link, selected files, pending operations, and current tab. It does not request new Drive authorization or clear login sessions.

## Offline documents

Maintain matching complete `FEATURES.md` and `CHANGELOG.md` documents in `docs/i18n/id/` and `docs/i18n/en/`. Keep `CHANGELOG.md` at the repository root identical to the English version. Gradle's `syncAppDocs` packages both directories under `info/` in the APK.

Do not combine two languages in a single document. Brand names, product names, file paths, version labels, and technical identifiers do not need translation.

## Device checks

- Switch languages both ways, force-stop, and reopen; the selection must persist.
- Switch with a pending link or selected local files; preserve the input and download options.
- Check Story selection, login screens, Drive dialogs, migration, toasts, and queue notifications.
- Open all Info sections in both languages, including the oldest release notes.
- Check administration with long labels, enlarged text, gesture navigation, and three-button navigation.
- Inspect a result with a filename containing Indonesian words, braces, or an English label; its path must remain identical.
- Verify that clear-history/cache confirmation and active-queue protection still work.

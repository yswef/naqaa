# Release checklist

## In the repository

- [x] Single module, package `com.naqaa.app`, minimum API 26, compile and target API 36.
- [x] No network client libraries, no analytics, no advertising, no crash reporting.
- [x] R8 with resource shrinking, per-ABI splits for arm64-v8a and armeabi-v7a.
- [x] `allowBackup=false` with backup and data-extraction rules.
- [x] Unit tests for prayer times, streak arithmetic, the DNS filter and the weekly report.
- [x] GitHub Actions workflow that runs the tests, assembles the release build and reports
      the size of each APK.
- [x] Arabic and English resources with identical key sets.
- [x] MIT licence, privacy policy, readme in both languages, changelog, architecture and
      blocking documents.

## Before publishing

- [ ] Run `./gradlew testDebugUnitTest lintDebug assembleRelease` and read the output.
- [ ] Confirm that every release APK stays under 6 MB.
- [ ] Add the signing secrets to the repository (see below) so the published APK is
      signed, or publish the unsigned artifact and say so in the release notes.
- [ ] Walk through the device checks in `docs/BLOCKING.md` on a physical phone, including
      the reboot case with always-on VPN.
- [ ] Confirm the accessibility selectors against the current versions of the watched
      applications.
- [ ] Check the Arabic layout on a right-to-left device and the English layout on a
      left-to-right one.
- [ ] Take the screenshots listed in the readme and add them to `docs/screenshots/`.
- [ ] Tag the version: `git tag v1.0.0 && git push origin v1.0.0`; the workflow publishes
      the APKs to the release.

## Signing material

The release build reads `keystore.properties` in the repository root, or the environment
variables `NAQAA_STORE_FILE`, `NAQAA_STORE_PASSWORD`, `NAQAA_KEY_ALIAS` and
`NAQAA_KEY_PASSWORD`. The workflow can also take the same values for a single run from its
manual dispatch inputs, where the keystore is passed as base64 and never written to the
repository. Without them the build still produces APKs, but they are unsigned and Android
refuses to install them except as a debug-style side load.

## After publishing

- [ ] Download both APKs from the release and install them on a clean device.
- [ ] Confirm the first screen is the setup walk, and that the application still works
      when every optional permission is refused.
- [ ] Confirm the widget, the launcher aliases and the PDF share sheet.
- [ ] Update the changelog and the readme if the release needed any correction.

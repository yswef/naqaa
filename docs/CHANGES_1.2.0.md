# 1.2.0 (code 13)

Nothing here was compiled (no Android SDK in the sandbox); only Kotlin syntax and the string
check were run. The first CI build is the real compile.

## 1. Neon theme
`ui/theme/Theme.kt`: dark neon scheme (`Neon` palette), animated `NeonBackdrop` under every
screen, `NaqaaTheme` no longer follows the system theme. `Common.kt`: glass `SectionCard`
with glowing border. `AppShell.kt`: transparent scaffold. Window colours in `values/colors.xml`,
`styles.xml`. Why: the old pale scheme; how: one backdrop drawn once, screens transparent.

## 2. Accounts and hidden developer
`data/UserStore.kt` (accounts sealed with the Keystore key, PBKDF2 passwords, `Session`
in memory), `ui/screens/AuthScreen.kt`, `MainActivity.Root` shows it after onboarding.
The developer account is `DevAccount`: only a SHA-256 of the name and a salted PBKDF2 hash are in
the source; `tools/make_dev_hash.py` makes new ones. Developer session: no waiting screen
(`DelayActivity`), no PIN, can extend an ended time; never printed on reports. User name is
printed on reports (`ReportPdf`, `ReportScreen`).

## 3. Update without uninstalling / export-import
Cause of "conflicts with an existing package": `build.yml` generated a new throw-away key on
every run when the `NAQAA_*` secrets were missing. Now the run warns loudly, and
`tools/make_keystore.sh` makes the permanent key once. Settings shows the signing
fingerprint to compare two builds. A build signed with a new key still needs one uninstall,
once. `data/Backup.kt`: password protected export/import (PBKDF2 + AES-GCM, format byte,
tolerant reader), via the system file picker (`SettingsExtras.kt`, `AppViewModel`).

## 4. Prayer times and logging
`Preferences.prayerOffsets` (minutes per prayer) applied in `PrayerCalculator.calculate(offsets=)`
at every call site; editing in `PrayerScreen`. Logging: one `PRAYER` record per prayer per day,
`trigger` = prayer key, `note` = `ontime` / `late` (`AppViewModel.logPrayer`). Home button opens the prayer screen.

## 5. Plan map
`PlanScreen.kt`: canvas map (perspective floor, shrinking winding path, glowing points, shaded
walking figure). Bubble (Popup) per day with three tasks; keys `day:b|s|c` in
`Preferences.planTasks`; the third task of today also writes the `PLAN` event. Pseudo 3D, not an engine.

## 6. Audio
`sound/Sounds.kt`, `SoundService.kt` (foreground media service). Files go in
`app/src/main/assets/sounds/`: `adhan.mp3`, optional `adhan_fajr.mp3`, any `hadith*.mp3`.
Prayer alarm plays the adhan, adhkar reminder plays the hadith of the day; if the system refuses
to start playback the notification keeps its own sound. Settings has switches and test buttons.

## 7. Other VPN apps
`guard/VpnApps.kt` (known list + installed apps with "vpn" in package or label). The service
locks them with `block_vpn_message`; when `DnsVpnService.onRevoke` fires, a notice and
`VpnResumeActivity` ask Android for consent again. Limit: a VPN started from a tile or a
notification never shows its app, so it is not seen. Protection screen has "Copy VPN report"
(`util/Diagnostics.kt`): send it if Windscribe still works.

## 8. Several locked apps
`ProtectionScreen.AppPicker`: full screen grid with icons, search, multi-select, one window for all.

## 9. Timed allowance
`guard/UsagePass.kt` (memory only). Short video pass from the protection screen (PIN to start);
a locked app can be opened for 5/15/30/60 minutes. When a pass ends the next block shows
`AppLockActivity` in "ended" mode. Fixed on the way: the old PIN grace did not stop the lock
(lock checks ran before it); the lock now respects the per-app pass.

## 10. Account clean-up
`accessibility/CleanupEngine.kt` + `CleanupScreen.kt`. Presses "Following" then "Unfollow" (or
select / unlike) in Instagram, 7-16 s between actions, a rest after each batch, daily cap, stops
when the screen does not change. Labels in `accessibility-rules.json` ("cleanup"). NOT tested on
the live app: use "Take a screen report" and send the text if a label does not match.

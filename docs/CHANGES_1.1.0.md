# Changes in 1.1.0

Each item: what changed, why, and how the new logic works.

## Crashes and compatibility
- `ui/BiometricUnlock.kt`: `BiometricManager.canAuthenticate(int)` exists only from API 30.
  On Android 10 and 11 the call ended the process (setup step 6, settings, lock screen).
  Now API 30+ uses the mask overload, API 29 uses the no-argument one, and everything is
  wrapped so a failing prompt leaves the PIN pad usable.
- `guard/BootReceiver.kt`: time and time-zone broadcasts are not allowed to start a foreground
  service on Android 12+, and the exception killed the process. The VPN auto-start now runs
  only for BOOT_COMPLETED and MY_PACKAGE_REPLACED, only when it is not already running, and
  every step is guarded and recorded through `CrashLog.note`.
- `prayer/PrayerAlarms.kt`, `notify/Reminders.kt`: `SecurityException` from exact alarms falls
  back to inexact ones. Prayer alarms are skipped when prayer reminders are off.

## PIN
- The unlock pad takes `PinGate.PIN_LENGTH` (6) digits, but setup allowed 6-12, so a longer PIN
  could never be entered. Setup and settings now require exactly 6.
- Setup could finish with a repeat that did not match; the Next button is now disabled on the
  PIN step while the entry is incomplete or mismatched.
- Removing the PIN requires the current PIN.
- PBKDF2 (310k iterations) ran on every recomposition in the settings dialogs and on the main
  thread in the lock screen. It is now computed with `remember(entry)` and, on the lock
  screen, on `Dispatchers.Default`.
- Phones that already saved a PIN longer than 6 digits must clear the app data once.

## Protection
- `accessibility/GuardAccessibilityService.kt`: the watched package list was fixed by the first
  event, so a lock added later was ignored until restart. `refreshWatched()` now builds the list
  from the rules and the locked apps, at connect time and on every settings change (listener of
  `PreferencesStore`).
- `vpn/DnsVpnService.kt`: queries are handled by a `ThreadPoolExecutor` (6 threads, queue 256,
  oldest dropped). The reading loop only copies packets; writes to the tunnel are synchronized.

## Performance
- `data/Vault.kt`: the Keystore key handle is cached; before, each journal row asked the
  Keystore again.
- `ui/AppViewModel.kt`: a filtered name no longer rebuilds the whole state each time; a state
  change rebuilds at once, a counter change at most every 5 seconds.

## Errors no longer shown
- `util/CrashLog.kt` `archive()` and `ui/MainActivity.kt`: a pending failure is moved to
  `previous-error.txt` (still readable from a computer) and the app starts normally.

## Interface
- Insets: setup, lock, app-lock and delay screens use `safeDrawingPadding()` (Android 15+ draws
  edge to edge); the shell adds `imePadding()` and resets scroll per screen.
- `res/values-night`: dark window background and light status-bar icons.
- Theme: rounded shapes, Arabic line heights. Common: `ChipFlow` (wrapping chips) and
  `appLabel()` (app name instead of package name). Home: emergency button under the counter,
  two-column quick log. Journal: wrapping chips, no nested scrolling list.

## Build
- `.github/workflows/build.yml`: a push to `main` publishes release `v<versionName>` when it
  does not exist yet; if it exists the run succeeds without publishing.
- Version 1.1.0 (code 11).

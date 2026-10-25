# Changes in 1.1.1

## Dark-mode text colour
- File: `ui/theme/Theme.kt`, `NaqaaTheme`.
- Cause: the onboarding screen was not inside a `Surface`, so unstyled text kept the default
  black content colour. Why: Material only sets the content colour inside a Surface.
- Now: `NaqaaTheme` wraps its content in a `Surface` using the theme background, so every
  screen, dialog host and activity gets `onBackground` text.

## Record interval
- File: `ui/AppViewModel.kt`, `DOUBLE_TAP_MILLIS` = 60 000 (was 3 000).
- Plain taps of the same button inside one minute are one record. It is kept in memory, so it
  resets when the app process restarts.

## Accessibility when the switch is blocked
- `util/SystemGate.kt`: `canWriteSecureSettings`, `enableAccessibilityDirect`,
  `adbGrantCommand`, `accessibilityReport`.
- `AndroidManifest.xml`: declares `WRITE_SECURE_SETTINGS`. Android never grants it at install;
  the user runs `adb shell pm grant com.naqaa.app android.permission.WRITE_SECURE_SETTINGS`
  once. The button then adds the service to the enabled list itself.
- `ui/screens/Common.kt`: the dialog shows the command and a "Copy report" button.
- The report is a diagnostic only. It changes nothing and is the way to learn what a device
  such as ColorOS reports before changing the flow again.

# Changelog

All notable changes to this project are recorded here. The format follows the usual
sections: added, changed, fixed and removed.

## 1.2.0

Neon theme, accounts + hidden developer session, encrypted backup/transfer, prayer time offsets and on-time/late logging, game-map plan, adhan/hadith audio, other-VPN block, multi-app lock picker, timed allowances, Instagram clean-up. Details: docs/CHANGES_1.2.0.md

## 1.1.1 - 2026-10-25

### Fixed

- Headings and body text on the first screens were black on the dark background in dark mode.
  The theme now sets the content colour for every screen.
- The pause between two saved records of the same button is one minute instead of three seconds.

### Added

- Accessibility can be switched on directly after a one-time `adb shell pm grant` of
  `WRITE_SECURE_SETTINGS`, which avoids the "restricted setting" block that sideloaded apps
  meet on Android 13 and later. The permission is never granted at install.
- "Copy report" in the accessibility dialog: device, Android version, installer, restricted
  setting state and whether the settings pages open. Used to find why a device still refuses.

## 1.1.0 - 2026-10-25

### Added

- Saving a lapse takes one tap. A quiet notice confirms it, a second tap within three
  seconds is not a second record, and a short sheet asks how it felt and what led to it, then
  lists the steps for the rest of the day.
- "My habits today" on the home screen: prayers counted to five, remembrance and the Quran
  portion marked once each, with the state of every row visible.
- The remembrance screen reads one item after another with a large counter to tap, opens on
  the morning or evening set by the hour, and moves on by itself.
- A step-by-step guide for Android 13 and newer, where the system blocks the accessibility
  switch for an app installed by hand until restricted settings are allowed.
- The step of the day from the thirty day plan appears on the home screen widget.
- New launcher icons for the application and for the three neutral entries.

### Fixed

- Fingerprint unlock no longer ends the process on Android 10 and 11: the call that takes an
  authenticator mask exists only from Android 11, and setup, settings and the lock screen
  all asked for it.
- The unlock pad accepts six digits, and setup and settings now require exactly six. A longer
  PIN used to be accepted and then could never be entered again. Removing the PIN now asks
  for the current one, setup refuses a PIN whose repeat does not match, and the slow PIN hash
  no longer runs on every redraw or on the main thread of the lock screen.
- The boot receiver starts the local filter only after a restart of the phone or of the
  application. A clock or time zone change used to attempt a background start that Android 12
  and newer refuse with an exception, which ended the process; every step is now guarded.
- A lock added after the accessibility service started now takes effect at once; the list of
  watched applications follows the settings instead of being fixed by the first event.
- Setup follows the live state (permission rows, chosen applications and the biometric switch
  used to stay stale), keeps its step and typed text across rotation and theme changes, and
  its buttons are no longer hidden behind the navigation bar on Android 15 and newer.
  The lock, delay and application-lock screens respect the system bars and scroll on small
  displays; the keyboard no longer covers text fields on Android 15 and newer.
- A rejected exact-alarm permission falls back to an inexact alarm instead of an exception,
  and prayer alarms are not scheduled while prayer reminders are off.
- The window behind the first frame and the status bar icons follow dark mode.

### Changed

- Name resolution is answered by a small pool of workers, so one slow name server no longer
  stalls every page. The journal key handle is kept for the process, which makes opening a
  long journal much faster, and the screen state is no longer rebuilt for every filtered name.
- An earlier failure is no longer put in front of the user at start; the text is kept in
  `previous-error.txt` next to `last-error.txt` for anyone reading the folder from a computer.
- Setup offers location first; the manual city list opens from a button below it and stays
  hidden once a city was found from the location.
- Interface: rounded cards, larger Arabic text spacing, the emergency button directly under
  the counter, quick log in two columns, wrapping chips, application names instead of package
  names, and each screen opens at its top.
- A push to the main branch now builds and publishes the release `v<versionName>` by itself
  when that version has no release yet.

## 1.0.9 - 2026-10-10

### Fixed

- The local filter no longer loses its session when the underlying network changes its
  name servers, such as when the phone moves between Wi-Fi and mobile data. The worker
  being replaced is given the time to finish before its successor starts, only the active
  worker may end the session, and the restarted session keeps its foreground notice and
  its running state instead of appearing stopped.
- City search now matches Arabic and English city and country names, including Yemen, and
  the location picker requests a fresh one-time fix when no recent fix is cached.
- The setup and prayer screens show clear location progress and fallback messages instead of
  silently leaving the city unchanged; the setup screen now scrolls on shorter displays.
- The crash report labels version, Android and processor information as diagnostic metadata,
  so it is not mistaken for the cause of a failure; fresh launches no longer report themselves
  as interrupted.
- VPN filtering starts after consent, and permission shortcuts use app-specific system pages
  with an app-info fallback. Android 13+ notifications use the runtime permission prompt.
- The release workflow publishes a manual run under the version tag it built instead of a
  branch name, refuses to replace an existing release, and signs with the NAQAA_*
  repository secrets when the manual signing inputs are left empty.
- The device check tests the latest published file when no tag is given, instead of a tag
  that no longer exists.

### Changed

- Button labels and input fields have clearer contrast. The adhkar list is now a step-by-step
  counter that advances through the selected morning or evening set.
- Permission labels and setup copy are shorter; the prayer screen keeps location selection
  optional and city search available as a fallback.

## 1.0.8 - 2026-10-07

### Added

- The failure text is also written to the folder the application owns on the shared storage,
  which a phone shows over a cable. A failure that keeps the interface from appearing can
  therefore be read from a computer, without a debugging tool and without a permission.

### Fixed

- The first state of the interface is built defensively: a failure while reading the journal
  or the settings now leaves a usable screen and records its reason, instead of ending the
  launch before anything is drawn.

## 1.0.7 - 2026-10-07

### Fixed

- The reminder chime was never heard: no channel used the bundled sound, so every reminder
  fell back to the system tone. The prayer reminder now posts to its own channel, which
  carries the chime, and a keep list holds the file in place against the step that removes
  unused resources. The other reminders keep the system sound, and a blocked attempt stays
  silent while the phone is being used.

## 1.0.6 - 2026-10-07

### Fixed

- The domain catalogue was opened under a name that the packaging tool does not keep: a
  gzip asset is expanded and stored without its suffix, so the reader found nothing and the
  filter started without a single rule. It now opens either name and tells the compressed
  and the plain form apart by their magic bytes, and a build that omits the catalogue fails
  the content check instead of shipping a filter that does nothing.
- A catalogue that cannot be read is written to the failure text instead of leaving the
  service to end in silence.

## 1.0.5 - 2026-10-07

### Added

- The build prints what every released file contains: the entry count, the unpacked size of
  the code, the resources, the assets and the sound, the number of classes, the name and
  size of every file the application reads at run time, and whether the components named in
  the manifest are present. A file that lost a component fails the build instead of being
  published.

### Changed

- The release notes answer the question of the file size next to the download: the
  application carries no images, no advertising and no network libraries, the icons and
  drawings are vectors, the blocked list is compressed, and the build removes every part of
  the libraries that is never called.

## 1.0.4 - 2026-10-06

### Added

- A screen that shows why the application stopped the time before. The text is written on
  the device, offered with copy and send buttons, and deleted once it has been read; nothing
  is uploaded anywhere.
- A check in the build that compares every formatted string with the call that formats it,
  so a missing placeholder cannot reach a release again.

### Fixed

- The prayer reminder asked for a value that the Arabic and English texts did not have,
  which stopped the process the first time a reminder was due.
- The weekly report screen printed the placeholder of the count inside the sentence instead
  of the count itself.

### Changed

- A failure inside the accessibility service, or while drawing the home screen widget, is
  recorded and the event is dropped, instead of letting the system restart the component
  until it is switched off.
- A failed read of the encrypted settings no longer prevents the interface from starting;
  the application opens with default settings and reports the failure on the next start.
- Switching the launcher entry no longer writes to the package manager when nothing changed.

## 1.0.3 - 2026-10-06

### Changed

- The release page carries a single file, `Naqaa-<version>.apk`, which installs on 32-bit
  and 64-bit phones. The smaller per-architecture builds remain in the workflow artifacts.

## 1.0.2 - 2026-10-06

### Changed

- Release files carry names that say who they are for, and the universal file is listed
  first: `for-any-phone`, `32bit-phones-only`, `64bit-phones-only`.
- The build asks for all three signing schemes, and the release notes list the schemes that
  were applied to each file together with its certificate.
- The release notes list the architectures, the signature schemes and the sha256 of each
  file, and the install instructions walk through the causes of "App not installed".

## 1.0.1 - 2026-10-06

### Added

- A universal APK that carries both architectures, next to the two per-architecture files,
  so a phone whose architecture cannot be determined still installs with one download.

### Changed

- The build verifies every release file with `apksigner` before publishing, refuses to
  upload a file that is not signed, and writes the architecture and the certificate of each
  file into the release notes.
- The about section shows the version that is actually installed instead of a fixed text.

### Fixed

- Installation on 32-bit phones: those devices refuse the arm64 file with the bare message
  "App not installed", which the install instructions now explain.

### Removed

- Release 1.0.0 was withdrawn: its arm64 file failed on 32-bit phones and its signing key
  was not kept, so it could not be updated over either.

## 1.0.0 - 2026-10-06

First release. Distributed as a per-ABI APK from the releases page.

### Added

- Local DNS filter as a `VpnService`: NXDOMAIN for blocked names, safe-search rewriting
  for Google, YouTube, Bing and DuckDuckGo, encrypted-DNS endpoints in the blocked list,
  a bounded DNS cache and a protected UDP upstream.
- Optional accessibility service that closes Reels and Shorts with a back action, can
  block whole applications such as TikTok, closes a browser address bar that contains a
  chosen keyword, and routes risky settings pages through the waiting screen.
- Guard: ten-minute wait before the filter, the accessibility service or the device
  administrator can be switched off, per-application locks inside a time window, night
  mode from midnight until Fajr, and a boot receiver that restores both the alarms and
  the filter.
- Device administrator with an explanation of what it can and cannot do about
  uninstallation.
- Encrypted journal in SQLite with one-tap logging, later details, separate resisted-urge
  records and a compassionate lapse flow.
- Streak counter with best run and a ninety-day clean view, six badges and private points.
- Emergency screen with the reason, a verse, a 4-7-8 breathing animation, a supplication,
  a fifteen-minute urge timer, behavioural alternatives and a call to the trusted contact.
- Weekly rule-based report with the riskiest two-hour window, trigger, place and feeling,
  three recommendations, a fixed disclaimer and PDF export through `FileProvider`.
- Offline prayer times with six calculation methods, sixty-one built-in cities, a one-time
  location fix and exact alarms rescheduled after a reboot.
- Offline content: thirty-three verses in the Uthmani text, fifteen narrations from
  Bukhari and Muslim, twenty-three supplications from Hisn al-Muslim, thirty motivational
  messages and a thirty-day plan.
- PIN lock with PBKDF2-HMAC-SHA256 and biometric unlock, `FLAG_SECURE`, neutral
  notifications, a neutral home-screen counter widget and four launcher disguises
  (calculator, notes, tasks).
- Setup walk with a reason, city and method, one-time location, step-by-step permission
  enablement, application locks, unlock choice and an optional trusted contact.
- Unit tests for the prayer calculation, the streak arithmetic, the DNS filter and the
  weekly analysis, and a GitHub Actions workflow that runs them and assembles the
  release build.

### Notes

- No server, account, analytics, advertising or crash reporting; the only network
  traffic is the DNS forwarding done by the local tunnel.
- Android cannot prevent an application from being removed, and the interface says so
  where it matters.

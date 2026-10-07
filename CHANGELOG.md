# Changelog

All notable changes to this project are recorded here. The format follows the usual
sections: added, changed, fixed and removed.

## 1.0.5 - 2026-10-07

### Added

- The build prints what every released file contains: the entry count, the unpacked size of
  the code, the resources, the assets and the sound, the number of classes and how many of
  them belong to this application, and whether the components named in the manifest are
  present. A file that lost a component fails the build instead of being published.

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

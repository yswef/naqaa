# Changelog

All notable changes to this project are recorded here. The format follows the usual
sections: added, changed, fixed and removed.

## 1.0.2 - 2026-10-06

### Changed

- Release files carry names that say who they are for, and the universal file is listed
  first: `for-any-phone`, `32bit-phones-only`, `64bit-phones-only`.
- Every release file is signed with the JAR scheme as well as the v2 and v3 schemes, which
  the installers on some vendor builds still require.
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

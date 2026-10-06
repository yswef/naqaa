# Naqaa (نقاء)

Naqaa is an offline Android application that helps a person stop compulsive pornography
use. It filters what the device resolves, closes the short-video loops that carry most of
the temptation, locks the applications that were agreed on, keeps a private journal of
what happened, and offers a screen to open at the moment of an urge. Prayer times,
remembrance and a thirty-day plan sit next to those tools, because the behavioural work
and the spiritual work belong together.

Everything runs on the device. There is no server, no account, no analytics and no
advertising. The interface is Arabic by default and fully translated into English.

## Screenshots

The images below are expected in `docs/screenshots/`; they are not part of the source
release yet, and the layout is verified on device before they are added.

| File | Screen |
| --- | --- |
| `docs/screenshots/01-onboarding.png` | Setup walk, permission step |
| `docs/screenshots/02-home.png` | Today, streak and the next prayer |
| `docs/screenshots/03-protection.png` | Filter state, safe search, locked applications |
| `docs/screenshots/04-journal.png` | Journal with one-tap logging |
| `docs/screenshots/05-emergency.png` | Urge screen with the breathing timer |
| `docs/screenshots/06-report.png` | Weekly report |

## What it does

### Local filtering

A `VpnService` keeps the Domain Name System of the whole device on the phone. Blocked
names are answered with NXDOMAIN, search entry points are rewritten to the vendors'
restricted services (Google, YouTube, Bing, DuckDuckGo), and encrypted-DNS endpoints are
refused so the filter cannot be walked around by a settings change. The tunnel carries DNS
and nothing else: no browsing traffic is proxied or inspected.

A neutral notification keeps the filter alive, and the same card that appears after a
blocked attempt shows a verse and a short encouragement, never a sermon.

### Short video and browser rules

An optional accessibility service watches only the packages listed in
`app/src/main/assets/accessibility-rules.json`. Reels and Shorts are closed with a back
action, whole-application blocking can be switched on for TikTok, and a browser address
bar that contains a chosen keyword is closed with a message. Screen content is compared
in memory and discarded; nothing is stored or transmitted.

### Guard

Uninstalling, disabling the accessibility service, stopping the tunnel or revoking device
administration all pass through a ten-minute wait that names what is about to be lost and
shows one verse. Risky applications can be locked inside a time window, and a night mode
keeps them closed from midnight until Fajr. The application says plainly that Android does
not allow an application to prevent its own removal.

### Journal, streak and report

One tap records what happened, and the details (time, trigger, place, feeling, note) can
be filled in later. Resisted urges are recorded separately and count as an achievement.
Lapses open a compassionate flow: reminder of repentance, the ritual bath, ablution and
prayer with what was missed, the trigger behind it, and a short plan for the rest of the
day. A streak counter shows the current and best run plus the clean days of the last
ninety, badges mark three, seven, fourteen and thirty days, ten resisted urges and a week
of remembrance, and points stay private.

A weekly rule-based report compares the last seven days with the seven before, names the
riskiest two-hour window, trigger, place and feeling, and gives three practical
recommendations with a fixed disclaimer. It can be exported as a PDF and shared through
the FileProvider; the file has no notes, no contact details and no device identifiers.

### Prayer, remembrance and plan

Prayer times are computed on the device from the solar equations, with a choice of
calculation method, a built-in city list or a one-time location fix. Reminders use exact
alarms when the system allows them and are rescheduled after a reboot, a time change or a
time-zone change. Morning and evening remembrance, a short daily Quran reading and a
thirty-day plan with one behavioural task, one spiritual task and one small challenge per
day are bundled offline.

### Emergency screen

The screen for the moment of an urge offers the reason the user wrote during setup, a
verse, a 4-7-8 breathing animation, a supplication, a fifteen-minute timer, behavioural
alternatives and a call to the trusted contact. It closes with the option to record the
urge as resisted.

## Install

Android 8.0 or newer. On the [releases page](https://github.com/yswef/naqaa/releases):

| File | For |
| --- | --- |
| `Naqaa-<version>-for-any-phone.apk` | Every phone, 32-bit and 64-bit alike. Take this one when unsure. |
| `Naqaa-<version>-32bit-phones-only.apk` | 32-bit phones, among them the Galaxy A10 and its generation. Smaller download. |
| `Naqaa-<version>-64bit-phones-only.apk` | 64-bit phones, most devices from 2018 onwards. Smaller download. |

1. Download the file, open it, and allow installation from this source when Android asks.
2. Finish the setup walk: language, reason, city and method, location, permissions,
   applications to lock, PIN or biometrics and an optional trusted contact.
3. Turn on the switches the walk asks for: the VPN consent dialog, the accessibility
   service, device administration, notifications, exact alarms and the battery
   optimization exemption.
4. In Android's VPN settings, open the entry for Naqaa and enable *Always-on VPN*, so the
   filter comes back after a reboot.

### When Android says "App not installed"

Samsung shows one bare sentence for several unrelated problems. Check in this order:

1. **Wrong file for the phone.** A `64bit` file installs only on a 64-bit phone; a 32-bit
   phone (Galaxy A10, A20, J-series of that generation) refuses it with exactly this
   message. Download the `for-any-phone` file.
2. **Incomplete download.** Compare the byte size, or the sha256 listed in the release
   notes, with the file on the phone; a truncated file is refused without explanation.
   Download it again over a stable connection, or from a computer and copy it over.
3. **No free space.** The installer needs the file size again in free storage. Clear a few
   hundred megabytes and retry.
4. **Blocked by Play Protect.** The dialog offers *Install anyway*; choose it, or turn Play
   Protect scanning off temporarily in the Play Store settings.
5. **Installation from this source is blocked.** Allow the browser or the file manager to
   install unknown applications in Android's application settings.
6. **An older copy signed with another key is installed.** A build from an earlier tag
   cannot update over it: uninstall the previous copy first, which also removes its
   journal.

The release notes carry the architecture list, the signature schemes and the sha256 of
every file, so a report of what failed can name the exact file that was used.

## Build

Requirements: JDK 17 and the Android SDK with platform 36 and build tools 36.0.0.

```sh
./gradlew testDebugUnitTest
./gradlew assembleRelease
```

Release signing reads `keystore.properties` in the repository root or the environment
variables `NAQAA_STORE_FILE`, `NAQAA_STORE_PASSWORD`, `NAQAA_KEY_ALIAS` and
`NAQAA_KEY_PASSWORD`; without them the release build stays unsigned. The build produces
one APK per ABI (arm64-v8a and armeabi-v7a) with resource shrinking, obfuscation and no
debug symbols, and the workflow warns when an APK crosses 6 MB.

## Tests

`./gradlew testDebugUnitTest` runs the unit tests that do not need a device, currently
forty-one of them:

| Class | Covers |
| --- | --- |
| `prayer.PrayerCalculatorTest` | Solar noon, twilight order, day length at mid latitude, polar absence, methods, invalid coordinates |
| `data.ProgressTest` | Streak arithmetic, lapses, badges, points, ninety-day window |
| `vpn.DnsFilterTest` | Packet parsing, name encoding, error answers, safe-search rewriting, suffix matching, cache limits |
| `report.ReportAnalyzerTest` | Risk bands, weekly windows, riskiest two-hour window, recommendations, badges |

## Permissions and why

| Permission | Reason |
| --- | --- |
| `INTERNET` | Forward allowed DNS queries from the local tunnel to the resolver of the underlying network. Nothing else leaves the device. |
| `ACCESS_NETWORK_STATE` | Notice when the underlying network changes so the tunnel can restart its upstream socket. |
| `ACCESS_COARSE_LOCATION` | Optional, one-time: pre-select the nearest city so the prayer times are right. Refusing it leaves the city list usable. |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE` | Keep the local filter running with a visible, neutral notification. |
| `POST_NOTIFICATIONS` | Prayer reminders, the morning and evening remembrance reminder and the neutral filter notice. |
| `RECEIVE_BOOT_COMPLETED` | Reschedule the alarms and bring the filter back after a reboot. |
| `SCHEDULE_EXACT_ALARM` | Deliver prayer reminders at the exact minute; without it the reminders are windowed instead. |
| `USE_BIOMETRIC` | Unlock the application with the fingerprint instead of the PIN. |

The accessibility service and the device administrator are separate switches with their
own explanations, both revocable from the same system screens. The launcher entry can be
renamed to a calculator, notes or tasks shortcut.

## Privacy

No account, no server, no analytics, no advertising, no crash reporting, no network
client libraries. The journal is stored in a local SQLite database whose payloads are
sealed with AES-256-GCM under a key that never leaves the Android Keystore; preferences
are encrypted the same way and written atomically. The PIN is hashed with
PBKDF2-HMAC-SHA256 and 310,000 iterations, cloud backup and device transfer are disabled
in the manifest, screen capture is blocked with `FLAG_SECURE`, notifications are private,
and the single destructive action asks twice. See [PRIVACY.md](PRIVACY.md).

## Known limitations

- DNS filtering cannot stop an application that connects to a known IP address, a
  browser that caches a page, or a second VPN, and the port-853 rule is enforced through
  the name list rather than by blocking a port.
- The DNS path implements IPv4 UDP with one uncompressed question: no DNS over TCP, no
  fragmentation and no TCP fallback for truncated answers. When the tunnel cannot start,
  the device falls back to the system resolver instead of losing connectivity.
- The accessibility selectors depend on the interface of each watched application. They
  are conservative and need checking after a major update of that application; the rules
  are data, so correcting them never requires touching the code.
- Android does not let an application forbid its own removal. The wait screen and the
  device administrator make removal slower and more explicit; they do not prevent it.
- The prayer times follow the published angles of the chosen method. Local
  moon-sighting decisions and mosque-specific offsets are not applied.
- No high-latitude rule is invented: when the sun does not reach an angle, the time is
  left empty and the interface says so.
- The application is distributed as an APK because store policies restrict accessibility
  services and VPN-based filtering.

## Content sources

- Quran: the Uthmani text as distributed by the Tanzil project, with the surah name and
  the verse number for every quotation.
- Hadith: Sahih al-Bukhari and Sahih Muslim only, with the collection and the number of
  the narration.
- Remembrance: the standard supplications of *Hisn al-Muslim*, each with its printed
  source.
- Domain list: the porn-only variant of the StevenBlack hosts list (MIT) plus a short
  curated set of encrypted-DNS endpoints.
- Prayer times: the NOAA solar equations.

## License

MIT, see [LICENSE](LICENSE). Copyright (c) 2026 yswef.

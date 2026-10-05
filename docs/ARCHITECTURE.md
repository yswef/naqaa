# Architecture

Naqaa is a single-module Kotlin application for API 26 and newer, compiled against API 36
with Compose Material 3. There is no dependency-injection framework: `NaqaaApplication`
builds the `AppGraph`, and the graph hands out the store, the preferences and the
encryption on demand. The interface is one composable tree with a small navigation state
(`Destination` in `ui/screens/AppShell.kt`), because a navigation library would add a
dependency for a fixed set of screens that never need deep links.

## Packages

| Package | Responsibility |
| --- | --- |
| `data` | Event and preference models, Keystore AES-256-GCM vault, SQLite journal, atomic preference file, PIN hashing, streak and points |
| `vpn` | DNS message parsing and building, IPv4 UDP framing, domain suffix table, safe-search rewriting, bounded cache, upstream resolver, the VPN service |
| `accessibility` | Rule file, bounded node scanning, Reels and Shorts detection, browser keyword rule, risky settings interception |
| `guard` | Waiting gate, lock windows, night window, grace period, protected signals, device administrator, boot receiver |
| `prayer` | Solar calculation, calculation methods, cities, alarm scheduling |
| `content` | Bundled verses, narrations, supplications, motivational messages and the thirty-day plan |
| `report` | Weekly analysis, its wording, and the PDF writer |
| `notify` | Neutral notifications, prayer and remembrance reminders |
| `ui` | Activity, view model, theme, lock screens, persona switching, and the screens |
| `widget` | The home-screen counter in `RemoteViews` |
| `util` | Locale selection, time formatting, and the readers of system switches |

## Storage and key material

The journal keeps opaque row identifiers and timestamps in the clear so range queries stay
cheap, and seals everything else, including the event kind, inside an authenticated
payload. Each payload is bound to its row identifier through additional authenticated
data, which stops a payload from being moved to another row. Preferences are sealed the
same way and written with `AtomicFile`.

The AES-256-GCM key lives in the Android Keystore and is never exported. If the key is
lost, encrypted rows are counted as unreadable and reported rather than silently deleted;
the same rule applies to preferences, which show an error instead of resetting to
defaults.

The PIN is hashed with PBKDF2-HMAC-SHA256, a random sixteen-byte salt and 310,000
iterations, and accepts six to thirty-two digits. Five wrong attempts start a lockout of
one minute that doubles up to fifteen. Biometrics unlock the interface through the
platform prompt; they do not replace the PIN hash.

## Filtering pipeline

An outgoing DNS query arrives as an IPv4 UDP packet on the tunnel interface. The packet
layer (`Ipv4Udp`) frames and unframes datagrams and computes checksums; the message layer
(`DnsMessage`) reads the header, one uncompressed question and the resource records it
needs. The service then applies the rules in this order: the domain suffix table, the
safe-search rewriting map, and the cache. Blocked names are answered with NXDOMAIN and
counted; the count feeds the in-application card, and the notification is throttled so a
burst of blocked attempts does not produce a stream of alerts.

Allowed queries go to the resolver of the underlying network through a protected UDP
socket. A reply is accepted only when its transaction identifier, question name and
question type match the request. Answers enter a cache whose lifetime is clamped between
thirty seconds and fifteen minutes.

## Accessibility pipeline

The service is woken by window events. It checks the foreground package against the rule
file, and only for those packages does it inspect the node tree, up to three hundred nodes,
eight levels deep, with labels capped at sixty-four characters and addresses at
two hundred and fifty-six. Short-video detection needs both a known identifier and a
matching label, so an ordinary feed is not mistaken for a player. A hit performs a global
back action or goes home for a whole-application rule. Address-bar text is compared with
the keyword list and then discarded; nothing is written to storage or to the log.

## Guard behaviour

Lowering the protection passes through `DelayGate`: a conversation screen with a ten
minute countdown, the verse and the sentence that names what is about to be lost. A
satisfied gate grants a grace period so the guard does not immediately re-open. Application
locks are time windows (`data/LockRule`), and the night window runs from midnight to Fajr
with a five o'clock fallback when the prayer time is unavailable. The boot receiver
restores the filter, the alarms and the reminders.

## Prayer calculation

Declination and the equation of time come from the NOAA series in the fractional year. The
published method angles are degrees below the horizon and are turned into signed altitudes
where they are used, while sunrise keeps its own altitude. Umm al-Qura uses ninety minutes
after sunset (one hundred and twenty during Ramadan); the other methods use their
published Fajr and Isha angles. Asr uses the one-shadow factor, or two for the Hanafi
setting. When the sun does not reach an angle, the time is left empty rather than invented,
which is what happens in a polar summer. Times are instants, so Fajr may fall before the
local midnight and Isha after it, and the day's list is the instants in order rather than
the calendar page.

Alarms use `setExactAndAllowWhileIdle` when the system allows exact alarms and a windowed
alarm otherwise, and the schedule is rebuilt at boot, after a time or time-zone change, and
when a reminder fires.

## Weekly analysis

The score starts from the number of lapses in the last seven days (none zero, one one,
two two, three or more three). A week that is worse than the previous one adds a point, a
better week removes one, a lapse with nothing resisted adds a point, and a lapse in the
last two days adds another. Prayer, remembrance and the plan are judged per day: no day at
all adds two points, two days or fewer adds one, and every day of the week removes one. The
bands are low below one, medium from one to three, and high from four. The report names the
two-hour window holding the most lapses, the most frequent trigger, place and feeling, and
three recommendations, and it carries a fixed disclaimer. These are product rules, not a
clinical instrument, and the text says so.

## Tests and verification

The unit tests cover the four engines that can be checked without a device: solar prayer
times, streak and badge arithmetic, DNS packet handling and suffix matching, and the weekly
bands and recommendations. The GitHub Actions workflow runs them and assembles the release
build, reports the size of every APK, and fails when either step fails.

Behaviour that needs hardware remains manual: VPN consent and always-on behaviour,
accessibility selectors against current application versions, exact alarms, biometric
prompts, the widget, the launcher aliases and the PDF share sheet. The release checklist
lists those checks.

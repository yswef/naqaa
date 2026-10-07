# Blocking design and limits

## The tunnel carries DNS only

`DnsVpnService` advertises `10.111.0.2` as the device resolver and routes only that address
through the tunnel; the tunnel itself uses `10.111.0.1`. No application traffic is proxied
or inspected. Allowed queries are sent to the resolver of the underlying network through a
UDP socket that is protected against the tunnel, with a two second timeout, and a reply is
accepted only when the transaction identifier, the question name and the question type
match the request.

The parser handles unfragmented IPv4 UDP with one uncompressed question. There is no DNS
over TCP, no IP fragmentation, no IPv6 transport and no TCP fallback for a truncated
answer. When the tunnel cannot start, the system resolver takes over instead of leaving the
device without connectivity, so the filter is a strong obstacle and not a guarantee.

Always-on VPN is the user's switch in Android settings. Android lockdown ("block
connections without VPN") must stay off for this design: DNS is the only traffic in the
tunnel, and lockdown would cut everything else.

## Domain rules

`app/src/main/assets/blocked-domains.txt.gz` holds 76,864 names: the porn-only variant of
the StevenBlack hosts list, plus a curated set of encrypted-DNS endpoints (DoH and DoT
hosts). The catalogue is decoded once into a single byte table of names in sorted order
with an offset table, which keeps the resident cost close to the raw text instead of one
string object per rule. A lookup binary searches the table and then repeats with each
suffix, so a rule for `example.com` also covers `cdn.example.com` while
`notexample.com` stays untouched. Names are compared in lower case and ASCII only.

The packaging tool expands a gzip asset and stores it without the `.gz` suffix, so the
reader opens `blocked-domains.txt` first, falls back to `blocked-domains.txt.gz`, and tells
the two forms apart by the gzip magic bytes rather than by the name. A build that omits the
catalogue fails the content check in the workflow instead of shipping a filter that does
nothing.

To refresh the list, replace the gzipped asset, keep one name per line without comments,
and rebuild; nothing in the code depends on the count. Keep the encrypted-DNS entries in
place: without them a settings change would move resolution to a resolver the filter
cannot see.

## Safe search

Search entry points are rewritten before the query leaves the device, so the answer comes
from the vendor's restricted service: `forcesafesearch.google.com` for Google (including
country variants), `restrict.youtube.com` for YouTube, `strict.bing.com` for Bing and
`safe.duckduckgo.com` for DuckDuckGo. Only search entry points are rewritten; mail and
other services of the same vendors keep working, because an over-broad rule pushes people
to switch the protection off.

## Short video rules

`app/src/main/assets/accessibility-rules.json` has four sections:

| Section | Meaning |
| --- | --- |
| `shortVideo` | One record per package: view identifiers, visible labels, and whether the whole application is blocked |
| `browsers` | Packages whose address bar is inspected for keywords |
| `keywords` | The words that close a browser page |
| `riskySettings` | Settings screens that are routed through the waiting screen |

A record matches only when a view identifier and a matching label are both present, which
keeps an ordinary feed from being mistaken for a player. A hit presses back; a
`wholeApp` record goes home. An application is never watched unless it appears in the file.

To update the rules for a new version of an application: open the short-video screen on a
test device, list the view identifiers of the player container (the layout inspector or any
view-inspection tool works), add the identifier and one visible label that appears in both
languages you support, and keep the record conservative when unsure. The file is data, so
correcting a selector never needs a code change or a new version of the application
beyond shipping the new asset.

## Risky settings

Uninstalling, disabling the accessibility service, stopping the tunnel and removing the
device administrator each open the waiting screen, which shows the verse, names what is
about to be lost, and only then offers the way to the system page. The countdown is not
persisted: closing the screen starts it again. Android does not allow an application to
forbid these actions, and the interface says so in the same screen.

## Locks and the night window

An application in the lock list is closed while the current time is inside its window:
whole day, evening, night, school hours, or a custom pair of clock times. The window is
evaluated against the local clock and can cross midnight. `NightMode` keeps the locked
applications closed from midnight until Fajr, falling back to five o'clock when the
prayer time is missing. A grace period stops the guard from reopening the lock screen in a
loop right after the user leaves it.

## Verification on a device

- Start the filter, confirm the VPN key appears in the system status bar, and open a
  blocked name in a browser: the answer must be NXDOMAIN and the card must appear once.
- Search on Google, YouTube, Bing and DuckDuckGo and confirm the restricted results page.
- Set a private DNS hostname in Android settings and confirm the card in the protection
  screen and the shortcut that leads to the setting.
- Open Reels, Shorts and the Instagram clips viewer and confirm the back action; open an
  ordinary feed and confirm that nothing happens.
- Try the waiting screen from the uninstall page, from accessibility settings and from the
  administrator entry, and confirm the countdown and the recorded decision.
- Reboot with always-on VPN enabled and confirm that the filter and the alarms return.

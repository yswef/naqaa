# Naqaa privacy

Naqaa has no backend, account system, analytics, advertising, or crash-upload service. Journal entries are not transmitted by the app. There are no network client libraries.

## Local storage

Journal payloads and preferences use AES-256-GCM with a 256-bit key in Android Keystore. SQLite still exposes its schema, opaque event identifiers, and row counts; this is encrypted record storage, not whole-database encryption. The PIN uses PBKDF2-HMAC-SHA256 with a random 16-byte salt and 310,000 iterations. The PIN gates the interface; it does not independently encrypt the database key. Screen capture is disabled through FLAG_SECURE. Rooted devices and compromised operating systems are outside the protection boundary.

Cloud backup and device transfer are excluded through Android backup rules. Manufacturer-specific behavior still needs testing. Losing the Keystore key makes existing encrypted records unreadable; the app does not silently reset them.

## Failure text

If the application stops, the details of that failure are written to a private file in the application's own directory: the version, the Android level, the processor list and the stack trace. Nothing from the journal, the reason, or the contact is included. The next start shows the text with a copy button and a share button, and deletes the file once the user continues. It is never uploaded by the application; sharing it is a decision the user makes, and the text should be read before it is sent anywhere.

## DNS traffic

INTERNET is used by the local VPN to send allowed DNS queries through protected UDP sockets to a DNS server advertised by an underlying network. This is a real network disclosure: the resolver and potentially the network operator can observe requested domain names. DNS transport is not encrypted. No journal entries, PIN, contact number, or personal reason are included in these packets. No browsing history is intentionally stored.

## Accessibility

The optional service checks only configured application packages. It inspects a bounded number of visible interface nodes for identifiers and short labels, without retaining or logging screen text. Users explicitly enable it in system settings and can revoke it there. It is not a general screen monitoring or remote control service.

## Sharing

PDF export is explicitly requested by the user. The file holds aggregate counts, the risk band, the three recommendations (which may name a category of trigger or place from a fixed list of words) and the fixed disclaimer. It never holds free-text notes, the trusted contact, the time of an individual event or any device identifier. The PDF is a plaintext file in app-private cache, exposed temporarily through FileProvider, and a new export removes the previous cached report. A receiving app may store or transmit the shared PDF according to its own policy. Do not share with an untrusted recipient.

## Deletion and current limitations

The erase control requires two confirmations and deletes journal rows, encrypted preferences, and the Keystore key. An exported report may still remain in private cache until replaced or Android clears it; clear app storage to remove all cached copies. Copies already shared outside the app cannot be recalled. Erasing deletes the journal rows, the encrypted preferences and the Keystore key, and refreshes the widget afterwards. Copies already shared outside the application cannot be recalled.

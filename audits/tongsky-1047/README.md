# TongSKY 1.0.47 — no-flight calendar and completion diagnostics

The supplied diagnostic export ends at 2026-11-03 14:00:41 KST and is from 1.0.45, not 1.0.46. The 1.0.45 portion contains 255 unsupported-itinerary records, one parse timeout, one session start and one completion. No new Java crash is recorded for 1.0.45. The preceding process interruption explicitly reports an APK installation (`installPackageLI`). No raw user diagnostic file is published.

## Reproduction and changes

On 2026-11-03, the official public Korean Air award calendar for ICN → CNX, October 2027, displayed 29 `운항편 없음` cells and two disabled dates. All six award filters were checked. The old collector recognized seat/no-seat icons only, so every day remained UNKNOWN and it waited until timeout. No flight details, account login or private endpoints were used.

The parser now classifies `CALENDAR_NO_FLIGHTS` only when every date is either an explicit, visible no-flight cell with the exact verified marker/class/disabled state, or a disabled date, and at least one explicit no-flight cell exists. It never converts these dates to empty seats or overwrites the saved calendar. Unexplained blanks, hidden cells and conflicting marks retain the existing wait/rejection protections. Batch collection records this distinct reason and advances after the normal gap.

The Korean Air final summary used the last region's counts even during all-region collection. It now reports cumulative processed, success and unconfirmed counts. Both carrier services refresh final diagnostic progress before writing COMPLETE/STOPPED. Interrupted runs retain their partial count and stop reason.

## Scope

The 1.0.46 route exclusions remain unchanged. 124 of the 255 unsupported-itinerary records match those exclusions; 131 do not and remain unresolved future itinerary responses, not proof of a closed route. These remaining responses concern ALA, CMB, TLV, BKI, TAG, CRK, CNX and ZRH. No additional destination was blacklisted from these error messages.

No data reset, weekday filtering change, website calendar change, flight-detail capture, or notice-dismissal key change is included.

## Verification

- Shared parser harness: 24 cases / 139 assertions, including 8 new no-flight/blank/hidden/conflicting-result cases.
- Existing JavaScript regression suites: 14 race checks, 39 production-adapter checks, 8 monthly-only checks and the complete filter restoration suite passed.
- CI verifies signed APK and exact adapter equality between source, release APK and mobile test APK, native collection/lifecycle tests, 1,131-month calendar parity, Android 15 WebView execution at 320 CSS px, and signed upgrade/relaunch preserving saved records and the one-time reset marker.
- CI result and public download verification are recorded separately after completion.

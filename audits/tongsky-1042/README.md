# TongSKY 1.0.42

The Android dashboard used the older single-month weekday rule while the website already used reference months. For ICN ↔ MXP over 2026-11-03–2027-10-28, that produced P32/U63 in the app and P12/U43 on the website. Six December dates and four October dates in each direction explain the 20-day difference.

The app now derives its dashboard and calendar views from the same original flags, date boundary, eligible route scope and reference-month rules as the website. Dates through 2026-12-17 retain the original calendar. December 18–31 uses January 2027 in the same direction; other incomplete months use the nearest earlier complete month, excluding December 2026. Unknown dates remain unknown, and a known conflicting date prevents exclusion of that weekday. Original snapshots, observation timestamps and the one-time reset generation are retained.

Milan April 2027 is a complete observed calendar. April 10 and 15 contain only the economy marker in the original data, so the Saturday/Thursday review badges are expected under the strict three-marker rule. Those weekdays are not forcibly removed using another month.

The collector restores unchecked visible seat-filter labels individually and verifies all six before proceeding. Missing, ambiguous, disabled or non-responsive filters still cannot produce saved data. It does not set checkbox state directly or bypass airline access checks.

Both airline services request Android intent redelivery while running. Their durable batch cursors and shared outboxes remain the recovery source. Task removal does not cancel the worker or its WebView. Explicit stop, security failure and platform timeout still stop collection. Android user-initiated force stop is outside automatic recovery.

The website's existing display counts were already correct; its calendar calculation was not changed for this release. Native gray-cell inference captions were removed and explanatory text now describes the reference-month policy.

## Validation

- Website regression suite: 231 passing tests.
- Native regression suite: 62 tests, including original-fact preservation, reference boundaries, stale callbacks, saved cursor recovery and task removal.
- Native/website comparison: 567 saved route-months, 17,249 dates; Milan P12/U43. Both directions of all 61 eligible destinations are covered by boundary and carrier-isolation cases.
- Android 15 mobile adapter: 16 cases, 105 assertions at 320 CSS pixels.
- Signed 10145 → 10146 upgrade and relaunch preserve newly saved KE records and existing OZ records/settings/work. Reset generation remains `KE-20261103-1041`.
- Additional actual-service task-removal instrumentation uses a separately signed debug-only offline fixture build; production has no exported fixture surface.

Stored-data audit is pinned to `9744c8af6196d583c5fa9ad59c394ca9462d792f` (567 KE and 570 OZ records). The subsequent `489d49e3b02de1da17751841a4661b3e857c92a0` snapshot adds one validated ICN-CXR-2026-11 record. Empty regions are reported as uncollected; no schedules are invented. IAD-ICN-2027-10 was not recorded after its filter-stage failure. No live GitHub calendars were rewritten or reset.

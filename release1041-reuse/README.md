# TongSKY 1.0.41 r4 (10145)

KE groups unfinished route-month queries by direction and reuses the successful calendar for the next consecutive month. The native service and adapter both validate reuse. Existing 1.5-second spacing, security checks, monthly mark extraction, reset generation and saved-data namespaces remain unchanged.

A fresh route/month must be visible and all old date cells must be replaced before saving. A missing reuse prerequisite permits one cold fallback before submission. No fallback resubmits an already-requested month. Old navigation/stage callbacks cannot save.

Official visible unavailable-itinerary alerts now produce UNSUPPORTED_ITINERARY for the current batch item instead of waiting for a stage timeout; prior saved data is retained. This does not globally exclude Zurich.

OZ Chiang Mai is excluded from new collection because the official route page at https://flyasiana.com/C/KR/KO/booking/route?airportCd=CNX explicitly reports no Asiana-operated route. The airport catalog and saved searches remain. All 24 reported bidirectional route-month failures are covered by eligibility tests.

## Validation

Live official KE UI on 2026-11-03: ICN-LHR December to January to February navigation used the ordinary next-month button, with no reload/airport re-entry. In the January to February DOM observation, all 31 old date nodes were disconnected and 28 fresh nodes were present. ICN-ZRH December displayed the captured unavailable-itinerary alert. No login or security challenge was bypassed.

19 local JS regression suites passed, including 16 new reuse scenarios / 105 assertions using captured official December and January calendars. Raw dates match the shipped 1.0.35 parser. CI runs native lifecycle, eligibility, scheduling, preservation tests and the same reuse cases in an Android 15 WebView on a 320 CSS px private display. It validates a signed 10144-to-10145 upgrade and relaunch while preserving both databases and the existing one-time reset marker.

The local build is signed with the existing production certificate. hashes.json identifies exact candidate and probe. The workflow restores a zstd patch against r3, verifies hashes and signer, and stages the APK only after all checks succeed. Live UI checks do not measure end-to-end handset or GitHub-upload speed.

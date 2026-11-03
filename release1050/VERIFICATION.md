# TongSKY 1.0.50 verification

## Result
- Optional, independent IN arrival-city and OUT departure-city filters implemented for KE and OZ recommendations in Europe/Americas.
- Blank on either side matches the entire selected region for that leg. Region/carrier changes clear invalid cities; valid selections remain.
- Three existing categories remain: award/award, award/upgrade, upgrade/award.
- Stored calendars contain boolean markers, not seat quantities or passenger-specific inventory. No numeric quantity was fabricated; UI explicitly states the limitation.

## Verification
- 20 focused web tests passed: search/filter matrix, UI selection/reset, notices and calendar integration.
- Native CI: 12 tests, 0 failures/errors/skips.
- Web/native parity: 26 queries, 5,540 exact combination rows across all three categories.
- Original calendar parity: 1,131 route-months / 34,394 dates, inputs preserved.
- Android 15: both recommendation screens, four inputs and three categories; inspected KE/OZ screenshots.
- Upgrade/relaunch preserves KE/OZ records, settings, errors and queued work. Existing one-time reset is not repeated.
- Mobile collector regression: 24 cases / 139 assertions, 320 CSS px.
- Public website: FRA IN + blank OUT = 7 current combinations; FRA IN + AMS OUT = 2; AMS IN + FRA OUT = 3 (one per category). Counts are fixture/current snapshot dependent.
- Public OZ region switch from Europe to Americas resets invalid IN/OUT selections to blank.
- Public desktop screenshot: in-out-result.png. Local managed preview connection was unavailable; public deployed UI was inspected instead. No mobile web visual QA claim is made.

## Publication
- Website gh-pages: 61c991e4a4888d24d56287f2bfa994a8fe03d090; deployment 41599309876 success.
- Website source: 268030b9b149ededf5f872533d4cab24b9e5565a.
- Native source: aa492f77788a6685f90bc3beb74739e939f8c639.
- CI: https://github.com/bong40101-prog/tong-series/actions/runs/41599064708 success.
- APK staging: ea90d3ecdd37e92e6270b08a0a37584bfea58a70.
- Downloads gh-pages: d59c2756b99f8806ededbe673437dd0b8dac398a; deployment 41599980981 success; public page visibly shows 1.0.50.
- APK: 1.0.50 / 10154 / 3,354,625 bytes.
- APK SHA256: 80ae2cfc5188be28410374ef071cc0f9c4e45f6a087c20f50709eb03831a4976.
- Existing signing certificate unchanged.

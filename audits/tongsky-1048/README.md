# TongSKY 1.0.48 regional trip recommendations

Published November 3, 2026. Source and deployment commits are recorded in verification.json.

- Website search-panel header now contains 대한항공 추천일정 and 아시아나 추천일정 between airline tabs and load status.
- Each opens its airline-specific dialog. Inputs are region and duration only. All saved future dates are searched independently of calendar country, city, seat, and date-range filters.
- Three categories: award/award, award/upgrade, upgrade/award. Same-city return and cross-city/country open-jaw combinations stay within the selected region and carrier, with the current base airport on both ends.
- Duration includes outbound departure day; inbound flight departs duration - 1 calendar days later. Ground transfers and return-arrival-day calculations are not included.
- Both legs must have eligible saved business/prestige markers. Unknown, excluded and review days are not recommended. Existing calendar rules, collection, data and dismissed-notice keys are unchanged.
- Indexed date joins retain Cartesian products as compact blocks. Web renders 20 at a time per category; Android ListView materializes visible rows only. No airline searches are triggered.
- Android recommendation selection survives Activity recreation; stale work is discarded when inputs change or the screen closes.

Validation: 250 web tests, 88 native tests, exact parity for 30 real-data queries / 23,573 combinations across all six regions and five durations. Existing calendar parity covers 1,131 route-months / 34,394 dates. Synthetic Asiana tests cover category, direction, same/open-jaw, region and eligibility boundaries.

Android 15 emulator: both recommendation screens open with two inputs and three result types. Signed 1.0.42 -> 1.0.48 in-place upgrade and relaunch preserve a newly collected KE record plus original OZ snapshots, errors, shared outbox, batches, watches and settings. Legacy one-time-reset behavior is tested only in the initial 1.0.42 fixture; this update does not reset records. Device runner's final log label incorrectly says 10146 as source; actual installed baseline is TongSKY-1.0.42.apk.

Unchanged mobile collector adapter: 24 scenarios / 139 assertions. Release signature and compiled-class fingerprint were checked in CI. The first candidate's test subclass failed compilation because Activity was final; final candidate removes final, then all checks pass.

Browser checks: public KE Europe 7 days = 72 combinations (2/36/34); OZ Europe 10 days = 861 (78/284/499) at verification time. Data-dependent counts can change. Region/day dialog has no date fields and does not switch the main calendar airline. Desktop and 390 px preview were visually inspected. The public APK download was read back and its size/SHA256 match the validated artifact.

CI: https://github.com/bong40101-prog/tong-series/actions/runs/41593532210
Website: https://bong40101-prog.github.io/tong-sky-data/
Downloads: https://bong40101-prog.github.io/tong-series/

Screenshots: buttons.png and recommendations.png.

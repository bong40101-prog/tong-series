# TongSKY 1.0.44–1.0.45 verification

## Website and calendar rules

Website commit: `5354a5ef0aa57c64f7a5841c1ac948d10c08d528` in `bong40101-prog/tong-sky-data`, `gh-pages`.

Unresolved weekday comparisons now continue through contiguous complete original months of the same route and direction. They stop at missing/incomplete data or the first definite weekday result. Inferred results are never reused as evidence. Dates through 2026-12-17 preserve original flags; December 18–31 still references January only. No saved-calendar reset or raw-calendar rewrite was performed.

Checked KE 1,131 route-months / 34,394 dates across 102 directions and 12 months, using tree `b8c8ceb26e9dfaa1f0a5fb7ec06b9fbd8a066cdd`. The comparison clears 276 warning dates versus 1.0.43 without changing any seat flags or exclusions. Remaining warnings retain insufficient or conflicting original evidence. OZ 570 route-months / 17,362 dates were unchanged. Missing region data was not invented.

Chicago outbound January: bonus 6 / upgrade 11 / first 0, no review warnings. February: 3 / 22 / 2, no review warnings. Verified on the public website.

Four independent notices retain the existing `tongsky.notice.dismissed.v1.` namespace and all three old IDs. Only `feedback` was added. The live browser with the three prior notices dismissed displayed only the feedback notice. Website tests: 239 passed, zero failures.

## Android versions

1.0.44 / 10148 added identical native calendar rules. CI run 41574630958 passed 63 native tests, parity on 1,054 route-months / 32,054 dates, all 122 eligible catalog directions, and signed Android 15 upgrade 10147 → 10148 with existing KE and OZ records preserved.

1.0.45 / 10149 additionally handles a stalled next-month transition with one fresh search of the same query after the stage deadline. This preserves the original total deadline, rejects stale callbacks, and does not advance the cursor until a verified save or ordinary skip. Security, TLS and access restrictions are not retried. Official unsupported-itinerary responses remain unknown. A separate retry event and successful recovery event are recorded. No adapter guard was relaxed.

The final verification JSON contains CI and public-download evidence. The original user diagnostic attachment is not included in this public audit.

## Reproduce selected web regressions and stored-data audit

Decode `web-and-data-checks.tgz.b64`, extract into a clean directory, run `npm install --prefix web-reset-tests`, then `npm test --prefix web-reset-tests`. This bundle contains the reference-month and notice regressions, their fixtures, the exact deployed website source, and pinned public calendar data.

Run `node release1044/final-audit.mjs` and `node release1044/asiana-audit.mjs` for the saved-calendar comparison. `calendar-audit.json.gz.b64` contains the complete review decision audit. The screenshot files are base64-encoded PNGs.

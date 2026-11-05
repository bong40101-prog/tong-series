# TongSKY 1.0.52 collection recovery

## Changes

- Use Korea 09:00 booking-day opening in KE planning, UI validation and resumed-region eligibility. On 2026-11-06 at 08:59 KST the upper date is 2027-10-31; at 09:00 it becomes 2027-11-01.
- Distinguish a fully validated, stable all-disabled calendar from no seats and no flights. Wait for settling, then preserve prior observations, record an unresolved query and proceed to the next item. Changed/hidden/incomplete calendars retain existing validation.
- Add fresh cache keys to mutable GitHub Contents GETs. Retry lease ownership reads up to three times without reacquiring another owner's lease or writing from a failed ownership check.
- Annotate actual OZ query refusals for July–October 2027 as transfer-target-period refusals, based on official notice CM202610140002530842. Do not infer zero seats, suppress route queries, or rewrite saved carrier observations.
- Preserve the existing offline usage guide, app signing certificate and one-time reset marker. No saved calendar was reset.

## Evidence and limits

The supplied 1.0.50 log has 58 KE all-disabled November 2027 timeouts (35.23 cumulative minutes), 168 OZ July–October refusals, and one OZ regional ownership failure. GitHub history records a new region lease just before the reported ownership failure. A stale subsequent read is consistent with that sequence; the device log did not include the returned SHA or owner, so that mechanism is tested as a regression scenario rather than claimed as a directly captured HTTP response.

The first test run exposed a missing region key in the newly written synthetic service fixture; its setup was corrected. The application APK was unchanged by that test correction. Native collection/storage regression suite: 44 tests passed. Local JVM dependency retrieval was unavailable; native and Android verification run in GitHub Actions.

Android 15 verification passed: 1.0.51 → 1.0.52 signed upgrade and relaunch, retained KE snapshots and OZ snapshots/errors/shared outbox/batches/watches, unchanged one-time reset marker, both recommendation screens, no fatal crash. The WebView harness passed 28 scenarios / 159 assertions at 320 CSS px, including stable-disabled, late-arriving marks and table replacement.

Validation run: https://github.com/bong40101-prog/tong-series/actions/runs/42131797027
Validated APK staging commit: 01145658d36e1a6767863af97234251713ea0d66
Website publication commit: 7952c3ca07bc063eff9918087112d5c607dd3dfb
Pages deployment: https://github.com/bong40101-prog/tong-series/actions/runs/42132322422 (success)
Public index links the new APK. Downloaded public APK exactly matches the validated artifact, SHA256 1f6dcce5dae3994f116a31a8b25fe86dd4774c5663e238afce7e07d70b314df6. Other app listings are unchanged.

Live airline queries and a live user collection were not run during verification. Provider refusals cannot be converted into available seats by this patch.

Official references:
- https://www.koreanair.com/contents/booking/reservation-guide/how-to-book/booking-guide
- https://flyasiana.com/C/KR/KO/customer/notice/detail?force=true&id=CM202610140002530842

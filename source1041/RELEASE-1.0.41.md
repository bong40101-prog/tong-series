# TongSKY 1.0.41 r2 (10143)

Restores Korean Air monthly-calendar collection based on the exact calendar adapter extracted from the shipped 1.0.35 APK. The native collector now validates and saves the monthly result directly.

- Removed per-flight detail capture, date selection/confirmation, and mandatory separate weekly schedule collection.
- Retains route, month, complete date coverage, visible marker, source-time, filter and access-denial checks.
- Preserves the whole raw monthly calendar for weekday inference. No flight-detail observations or schedule records are created by the collector.
- Europe and Americas (including Canada): infer exclusion only when every occurrence of that weekday has economy award, prestige award and prestige upgrade marks. A differing or unknown date leaves that weekday in orange review state with its raw marks retained.
- Other regions save and display monthly calendar marks without adding the regional triplet rule.
- Same package and signer; 1.5-second query interval, collection horizon, cache freshness and Asiana collector remain unchanged.
- Existing one-time reset generation KE-20261103-1041 and its completion preference are unchanged. Updating from 10142 and relaunching must preserve newly collected KE data and all OZ data. No server data reset is part of this correction.
- KE namespace remains data/mobile/v103; OZ remains data/mobile/v101.

Build with tong-sky/scripts/build-local.py, Android SDK 35 and the private signing key stored separately. Historical test reports in this source archive describe their original releases; consult the accompanying 10143 verification record for current validation.

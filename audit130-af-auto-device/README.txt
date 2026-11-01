TongSKY 130 automatic Air France search after login — offline actual Android probe

This harness exercises production WebView secure configuration, document-start
capture, automatic navigation/search handling, result matching, SQLite saving,
acknowledgement and queue progression. It removes INTERNET permission and supplies
local synthetic responses through a WebViewClient wrapper. No airline is queried.

The instrumentation substitutes an Activity subclass only to wrap configure()
before the first production load. Normal production Activity callbacks remain
delegated. It does not directly inject normalized observations, invoke import or
manually press the search button for positive automatic cases. The only user
operation simulated during the initial workflow is a synthetic login button.
The initial synthetic login returns to an ordinary locale home page. Production
must restore the original task search URL exactly once before any query starts.
The resulting search document issues its own fetch in the HTML head, before page
finish, proving that production document-start capture sees the first request.

Other cases cover prefilled visible form selection, login expiry mid-queue,
403/429/verification challenge stops, a paused interval beyond the 10-second
query gap, stop during a delayed response, and completed queue recreation.
Observation exports must exclude synthetic credential/account fields. KE/OZ
collectors and databases remain unchanged.

Fixtures use the observed public search URL grammar and AF/KLM result field
interoperability contract. Dated CITY roundtrip with open return combines public
grammars and remains a candidate until authenticated production validation. This
offline test does not prove the current website supports that combined URL or
that a real Flying Blue account can sign in in this environment.

Build after final source freeze:
  python3 build-probe.py --source /path/to/source --sdk /path/to/sdk --out /path/to/out

Use Java17 and Android SDK35/build-tools35.0.0. The source builder supplies the
AndroidX WebKit dependency used by the production DocumentStart helper. A fresh
temporary debug key is generated and destroyed; never pass the release key.

The GitHub workflow expects audit130-af-auto-device/TongSKY-AF-auto-offline-probe.apk.
It runs only on an emulator and saves progressive JSON, logcat, process exit and
fixture-only screenshots for internal inspection. The probe reports each actual
request count and effective runtime heap. Any unsupported document-start feature,
timeout, unexpected navigation, extra request or false completion fails the run.

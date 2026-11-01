TongSKY 1.0.31 bounded Air France result scrolling — offline actual Android probe

This harness exercises the production WebView, document-start capture, foreground
controller, bounded normal scrolling, result matching, SQLite storage and queue
progression. INTERNET permission is removed. Every page/resource response is
synthetic and supplied by a WebViewClient wrapper. No airline is queried.

The instrumentation substitutes an Activity subclass only to wrap configure()
before the first production load. Normal production callbacks remain delegated.
It does not inject normalized observations or manually press search/import.

Cases:
1. Retained login-only→public-home→single original URL restoration, initial head
   fetch capture, three automatic tasks and real SQLite saving/acknowledgement.
2. A normal result document with small passive reCAPTCHA badge and offscreen
   iframe, initial response with multiple below-fold flights, automatic document
   scrolling and complete first-response quote storage.
3. A nested visible results scroller whose normal scroll listener starts a delayed
   same-task REWARD response. No save/advance while pending; both snapshots remain
   in history, while current data reflects the latest response without a union.
4. True HTTP403/429 or an active modal during collection stops scrolling and the
   queue. No partial storage, retry or next-date request during an 11-second watch.
5. Foreground pause and user stop mid-scroll, late response handling, completed
   progress recreation, export privacy, KE/OZ database and collector isolation.

The fixture uses the public search URL and candidate AF result schema. Dated CITY
roundtrip/open return remains an interoperability candidate. These synthetic tests
do not establish authenticated compatibility with the current production website.

Build only after final source freeze:
  python3 build-probe.py --source /path/to/source --sdk /path/to/sdk --out /path/to/out

Use Java17, AndroidSDK35/build-tools35.0.0 and pinned source AndroidX libraries.
A disposable debug signing key is generated and destroyed; no release key is used.
The workflow expects audit131-af-scroll-device/TongSKY-AF-scroll-offline-probe.apk,
on audit/tongsky-131-af-scroll and runs only on an API35 emulator. Progressive JSON,
logs and synthetic-only screenshots are for internal QA. Runtime cap is 300s.

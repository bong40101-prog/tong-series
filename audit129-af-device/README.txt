TongSKY 1.0.29 / Air France offline Android 35 probe

This debug-only probe runs real foreground Android WebView, production
configure/install capture code and af-capture.js, evaluateJavascript drain,
explicit native import/ack, real AirFranceDb SQLite storage, and saved-results
UI. It does not log into Air France, contact a provider, or establish current
authenticated production response compatibility.

Build:
  python3 build-probe.py --source /path/to/129/source --sdk /path/to/sdk \
    --out /path/to/af-probe-build
Requires Java 17, Android SDK 35 / build-tools 35.0.0. The script copies source
into a temporary directory, replaces debug LabInstrumentation only, removes
INTERNET permission, uses a temporary test signing key, and deletes that key.
The release signing identity and production source are not modified.
input-af-sha256.json records the exact AF source/asset hashes copied for build.

Publish the APK as audit129-af-device/TongSKY-AF-offline-probe.apk and workflow
as .github/workflows/tongsky129-af-device.yml (local af-device.yml). The workflow
runs on audit/tongsky-129-af and uses pinned checkout/emulator/upload actions.
It preserves configured heap256M but reports the actual runtime maximum.

Five cases, no repeat of the unchanged 128 large-cache/KE/OZ engine suite:
1. User action in a local search fixture performs one real WebView fetch.
   Production response wrapping normalizes a DAY MILES calendar response,
   non-destructive drain is imported through the production Activity and real
   SQLite, successful capture is acknowledged, and saved UI distinguishes
   calendar reference pricing from unknown fare-seat count.
2. Real WebView XMLHttpRequest delivers an availableOffers response containing
   priceInMiles currencyMILES/displayTypeFARE_MILES, activeConnection.segments,
   and two distinct flightProducts/connections fare quotes. Explicit fare-seat
   count2 stays2; missing count remains null and '알 수 없음'. It does not label
   the quoted fare's count as total seats remaining on an aircraft.
3. Cash flow, malformed JSON, and authentication response fixtures produce no
   stored seat observations, preserve prior rows, and leak no fixed test auth
   fields into normalized packets. Import does not issue additional requests.
4. The authentication-page fixture remains a standard browser with no capture
   hook, even for reward-shaped fixture traffic. Import preserves prior data.
5. Stored observations and saved-results UI survive Activity recreation. Back
   exits the saved panel and then the Activity; opening/recreating saved data
   sends no provider request or KE/OZ collection and changes no KE/OZ DB file.

Fixture HTML is served at a local intercepted official HTTPS base origin.
The harness constructs an ordinary Android WebView, calls the production
configure method, attaches it to the Activity and installs an interceptor
before openOfficial can load any URL. A debug WebViewClient delegates
production page lifecycle callbacks while intercepting every HTTP resource
with in-memory fixture bytes. INTERNET is
absent and asserted, so no real provider request is possible. The production
capture script and parser are used unchanged, not a normalized direct feed.
Fetch/XHR response counts verify that capture and import add no HTTP request.
Fixture response fields, login inputs and screenshots contain only conspicuous
fixed fake test values, never a user's account, cookies, token or password.

Two PNGs (af-probe-browser.png and af-probe-saved.png) are internal visual QA
artifacts from these fictitious pages. Progressive phase JSON, instrumentation
markers, logcat, process-exit metadata and WebView version are also exported.
A fail/timeout is not a successful test. Activity starts and main callbacks use
bounded waits, and file IO avoids unsupported Files.writeString/readString.

Offline Android 35 device probe

This is debug-only instrumentation for the actual Service / VirtualDisplay /
Presentation / Android WebView path and the shared-cache codec. It is not a
live airline query or proof that the user's particular device crash was fixed.

Build from the exact release source you wish to inspect:
  python3 build-probe.py --source /path/to/tong-sky-source --sdk /path/to/sdk \
    --out /path/to/probe-build --cache-fixture /path/to/public-asiana-cache.json

The SDK must contain Android platform 35 and build-tools 35.0.0; Java 17 and
keytool are required. No Gradle or production signing key is used. The script
creates a disposable source copy, substitutes LabInstrumentation.java, removes
INTERNET permission, adds the cache fixture only inside this temporary copy,
builds a debuggable APK with a temporary test key and deletes the private key.
The production source tree and release APK assets are not changed.

--cache-fixture is required. Supply the existing gzip-base64 JSON envelope
from the public Asiana shared-cache data, containing at least 1000 records.
Do not supply credentials or account data. Preserve records exactly. The
reference fixture contains only schema/origin/region/records/updatedAt inside
the envelope; it has no lease owner or installation metadata. Do not replace
it with a fabricated empty/minimal cache to make a memory test pass.

Run the resulting TongSKY-offline-probe.apk on an empty emulator using
run-device.sh. The workflow expects it as audit128-device/TongSKY-offline-probe.apk.
Use an actual 256 MB runtime heap limit and verify reported maxHeapBytes.

Eight required scenarios:
1. Asiana local official-style form: native input, real private display and
   WebView, date validation, and actual calendar persistence.
2. Korean Air browser then Asiana form: separate native displays and stopping
   Korean Air without destroying the running Asiana browser.
3. AsianaActivity full-route button through worker preparation, metadata DB,
   CollectActivity and real service/browser creation.
4. Korean Air full-route button followed by Asiana full-route button, keeping
   both native browser/display instances alive.
5. Actual large Asiana shared-cache envelope: production unpack, carrier/data
   validation, pack and unpack, revalidation and canonical record digest.

6. Production SharedCache staged local reuse of a fresh one-record local
   observation into the actual complete public cache, releasing inspection
   Decision before checkpoint and inspecting again. Verifies all unrelated
   records using a canonical digest, record count, and preserved success time.
   This executes the fixed data preparation order against a compressed disk
   backend; it does not run the whole real airline region loop. A controlled
   next-day test clock makes existing remote records expired and a separate
   one-record local observation fresh. The downloaded fixture is unchanged.
7. Exact Air France test-tab navigation from KE and OZ dashboards, independent
   placeholder screen, no browser or collector, and return to original screen.
8. Atomic bounded crash evidence writer on Android files, original handler
   chaining, message privacy, durable APP/JAVA_CRASH SQLite import, duplicate
   import suppression, and preservation of KE/OZ history. The uncaught handler
   is invoked directly with a test previous handler; no real process is killed.

Scenario 5 never builds a full JSON string for comparison. It hashes sorted
record keys and scalar values incrementally, releases the first decoded cache
body before unpacking the generated envelope, and checks record count and
canonical digest internally. Reports contain only outcome/timing and runtime
heap samples; no cache records, tokens, headers or account information.

Each scenario and launch/cleanup stage writes a progressive JSON report and
TongDeviceProbe logcat marker. Activity launches use a fresh task with bounded
resume observation, rather than startActivitySync (which can wait indefinitely
when Android simply brings an existing task to the front). Main callbacks also
have a 10-second timeout; genuine main-thread hangs fail the probe.

The button scenarios inject local fixture/shared=false extras through the
debug instrumentation ActivityMonitor. All fixture browsers also block
network loading. INTERNET permission is denied and asserted before any case.
No provider or GitHub requests are made. On failure, inspect the last phase
and partial cases; a timed-out harness is not a successful app/device test.

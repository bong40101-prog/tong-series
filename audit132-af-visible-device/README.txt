TongSKY 1.0.32 — offline visible-card and AF process-containment probes

No airline/GitHub request is made. Both APKs omit INTERNET. AF documents and raw
provider-schema responses are synthetic local WebView interceptions. KE/OZ use
existing DEBUG fixture HTML, block network loads and run shared=false. Query batch
metadata is seeded; real services parse their actual WebViews and save SQLite.
No positive AF case injects normalized packets, calls Data.save, or presses manual
search/import. The user's BlueStacks, mouse and keyboard are never accessed.

Evidence levels are deliberately different:
* Functional APK explicitly overrides AF back to the main process for direct
  instrumentation observations. This proves data/controller behavior, not process
  isolation. Eight groups cover calendar-only visible Business fares, updated
  visible/history snapshots, query-stripped SPA ledger proof, body-only canonical
  preference, invalid date/cabin/miles/header/auth/URL rejection, true restrictions,
  pause/stop, and both actual KE/OZ workers progressing while normal AF UI is open.
* Process APK preserves :airfrance and uses a DEBUG manifest alias from the normal
  AirFranceActivity component to RemoteAFProbeActivity in that same child process.
  The wrapper inherits the production AF controller and only supplies local HTTP
  fixtures plus a bounded JSON file reporter. Main instrumentation uses files and
  actual SQLite, never remote Java object references. It verifies distinct PID,
  default/AF profile directories, same main worker/browser/display identities,
  actual OS foreground status and fresh KE/OZ saves while AF is open.

The process smoke then requests an intentional DEBUG-only remote RuntimeException
through a fixed private file with a generated nonce. Production CrashCapture must
record the expected child PID/role/exception. Both real main workers must save
again, with unchanged main PID and no new SESSION_INTERRUPTED records. This expected
child FATAL is distinguished by nonce/PID/type; unexpected main failures are errors.
No package force-stop occurs during survival assertions. Script cleanup/force-stop
is only between completely finished instrumentation runs.

The main photo-shaped fixture contains only a matching calendar response, an
unrecognized successful operation whose body must be ignored, selected-date proof,
and per-card Business/MILES/one-adult evidence. The second fare is inserted only by
normal automatic scrolling. Header0miles/account0/flightcount2 are not prices or
seats. DOM records retain local times and unknown flight identity as
visible_offers/DOM_PRICE, separately from canonical network offers. No invented
flight number, timezone, arrival date or full-roundtrip price is accepted.
Saved-card screenshots wait for native text, two frames and measured card layout.
All screenshots are synthetic internal QA; runtime tests are not authenticated
production AF compatibility evidence or diagnosis of the user's unknown crash.

Build after production freeze:
  python3 build-probe.py --source /path/to/source --sdk /path/to/sdk --out /path/to/out
Java17, SDK35/build-tools35.0.0 and pinned source libraries are required. Both APKs
share one disposable debug key that is destroyed after building; no release key is
used. input-source-sha256.json records all production main Java and AF assets.
probe-build-manifest.json plus both XML manifests document every test override.

Initial workflow run41355090713/commit75bd6e6c ran API35 functional8groups plus
process containment, and API33 process containment in parallel. Safe JSON results
and provenance are in initial-runtime/. Original input maps/workflow/build metadata
are retained as initial-* files. The original process APK is unchanged.

Final follow-up changes only production AirFranceActivity.java and af-auto.js;
all54other recorded production inputs, including process/profile/crash bootstrap,
are unchanged. final-format-production-delta.json records both hashes. The final
functional suite has10groups: the original8 plus recognized200 unsupported-schema
fallback with strict witness/DOM proof (no-witness and explicit response-route
contradiction remain failures), and canonical offers-empty proof priority. Empty
offers complete only a NO_MATCHING_QUOTES observation without changing prior
availability/history; calendar-empty alone cannot complete detailed availability.

Current workflow: audit/tongsky-132-af-visible,
.github/workflows/tongsky132-af-visible-device.yml (template af-device.yml),
API35/functionalmode only. Process containment is not rerun for this small final
controller/driver delta; do not describe the old process APK as rebuilt finalcode.
Build only the final variant with --variant functional. Its fresh debug signer is
different from the original process APK. To rerun both variants later, rebuild
with --variant both so both receive the same disposable signer.
Expected APKs: TongSKY-AF-visible-functional-probe.apk and
TongSKY-AF-visible-process-probe.apk in this directory. Reports are separated by
functional/process. Logs, expected AF crash marker and progressive case JSON are
retained. Runtime caps: functional300s, process180s.

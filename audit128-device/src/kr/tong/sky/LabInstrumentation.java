package kr.tong.sky;

import android.app.*;import android.content.*;import android.content.pm.PackageManager;import android.os.*;import android.view.*;import android.webkit.*;import android.widget.*;import org.json.*;import java.time.*;import java.time.format.*;import java.nio.charset.StandardCharsets;import java.util.concurrent.*;

/** Offline, debug-only real Android startup/VirtualDisplay/WebView probe. */
public final class LabInstrumentation extends Instrumentation {
 Context ctx;final JSONArray cases=new JSONArray();JSONArray cacheMemory=new JSONArray();int intercepted=0;volatile String phase="created";int launchCounter=0;final ConcurrentHashMap<String,Activity> resumedActivities=new ConcurrentHashMap<>();
 final String waiting="<!doctype html><meta name='viewport' content='width=device-width,initial-scale=1'><p>Offline startup fixture</p>";
 @Override public void onCreate(Bundle args){super.onCreate(args);start();}
 @Override public void onStart(){ctx=getTargetContext();Bundle result=new Bundle();try{
  checkpoint("probe.start");
  check(BuildConfig.DEBUG,"probe requires debug build");
  check(ctx.checkSelfPermission(android.Manifest.permission.INTERNET)==PackageManager.PERMISSION_DENIED,"probe APK must have no INTERNET permission");
  ctx.getSharedPreferences("settings",0).edit().putBoolean("testOffline",true).commit();
  ctx.getSharedPreferences("settings-asiana",0).edit().putBoolean("testOffline",true).commit();
  runFullForm(false);stopBoth();runFullForm(true);stopBoth();runButtonStartup(true);stopBoth();runButtonStartup(false);stopBoth();runLargeCacheRoundTrip();runStagedCacheReuse();runAirFranceTabs();runDurableCrashRecord();
  JSONObject report=new JSONObject().put("success",true).put("cases",cases).put("interceptedCollectLaunches",intercepted).put("appVersion",BuildConfig.VERSION_NAME).put("api",Build.VERSION.SDK_INT).put("webViewPackage",WebView.getCurrentWebViewPackage()==null?"none":WebView.getCurrentWebViewPackage().packageName).put("networkPermission",false).put("runtimeMaxHeapBytes",Runtime.getRuntime().maxMemory());
  phase="probe.complete";report.put("phase",phase);save(report);android.util.Log.i("TongDeviceProbe","OFFLINE_DEVICE_PROBE_PASS "+report);result.putString("stream","OFFLINE_DEVICE_PROBE_PASS "+report+"\n");finish(Activity.RESULT_OK,result);
 }catch(Throwable failure){failure.printStackTrace();android.util.Log.e("TongDeviceProbe","OFFLINE_DEVICE_PROBE_FAIL phase="+phase,failure);try{save(progress().put("status","failed").put("success",false).put("error",failure.toString()));}catch(Exception ignored){}result.putString("stream","OFFLINE_DEVICE_PROBE_FAIL phase="+phase+" "+failure+"\n");finish(Activity.RESULT_CANCELED,result);}}
 JSONObject progress()throws Exception{return new JSONObject().put("status","running").put("phase",phase).put("cases",cases).put("interceptedCollectLaunches",intercepted).put("appVersion",BuildConfig.VERSION_NAME).put("api",Build.VERSION.SDK_INT).put("networkPermission",false).put("cacheMemory",cacheMemory).put("runtimeMaxHeapBytes",Runtime.getRuntime().maxMemory());}
 void checkpoint(String value)throws Exception{phase=value;String message="DEVICE_STAGE "+value+" cases="+cases.length();android.util.Log.i("TongDeviceProbe",message);System.out.println(message);save(progress());}
 void save(JSONObject report)throws Exception{java.io.File temp=new java.io.File(ctx.getFilesDir(),"device-startup-report.tmp");try(java.io.FileOutputStream out=new java.io.FileOutputStream(temp)){out.write(report.toString(2).getBytes(StandardCharsets.UTF_8));}check(temp.renameTo(new java.io.File(ctx.getFilesDir(),"device-startup-report.json")),"cannot commit progressive device report");}
 @Override public void callActivityOnResume(Activity activity){super.callActivityOnResume(activity);String id=activity.getIntent().getStringExtra("__device_probe_launch");if(id!=null){resumedActivities.put(id,activity);android.util.Log.i("TongDeviceProbe","ACTIVITY_RESUMED "+activity.getClass().getSimpleName()+" "+id);}}
 @Override public void callActivityOnDestroy(Activity activity){super.callActivityOnDestroy(activity);String id=activity.getIntent().getStringExtra("__device_probe_launch");if(id!=null)resumedActivities.remove(id,activity);}
 Activity launchActivity(Class<? extends Activity> type)throws Exception{
  String id="probe-"+(++launchCounter);checkpoint("launch."+type.getSimpleName()+"."+id);
  // NEW_TASK alone can reuse an existing task and never create an Activity.
  // startActivitySync waits indefinitely for creation in that case. Observe a
  // fresh, explicitly separate task asynchronously with a bounded resume wait.
  Intent intent=new Intent(ctx,type).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_MULTIPLE_TASK).putExtra("__device_probe_launch",id);
  onMain(()->ctx.startActivity(intent));await("Activity did not resume: "+type.getSimpleName(),12000,()->resumedActivities.containsKey(id));Activity activity=resumedActivities.remove(id);check(activity!=null,"resumed Activity disappeared: "+id);checkpoint("resumed."+type.getSimpleName()+"."+id);return activity;
 }
 static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 interface Condition {boolean get()throws Exception;}
 void await(String message,long millis,Condition condition)throws Exception{long end=SystemClock.elapsedRealtime()+millis;while(SystemClock.elapsedRealtime()<end){if(condition.get())return;Thread.sleep(100);}throw new AssertionError(message+" KE="+state(false)+" OZ="+state(true));}
 JSONObject state(boolean oz)throws Exception{return new JSONObject(ctx.getSharedPreferences(oz?"collection-oz":"collection",0).getString("state","{}"));}
 void onMain(Runnable task){
  if(Looper.myLooper()==Looper.getMainLooper()){task.run();return;}
  CountDownLatch done=new CountDownLatch(1);Throwable[] error={null};new Handler(Looper.getMainLooper()).post(()->{try{task.run();}catch(Throwable failure){error[0]=failure;}finally{done.countDown();}});
  try{check(done.await(10,TimeUnit.SECONDS),"main callback timed out at "+phase);}catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new AssertionError("probe interrupted",interrupted);}
  if(error[0]!=null)throw new AssertionError("main callback failed at "+phase,error[0]);
 }
 boolean browserReady(boolean oz){boolean[] ready={false};onMain(()->{if(oz){AsianaCollectionService s=AsianaCollectionService.activeOz;ready[0]=s!=null&&s.web!=null&&s.web.getWidth()>0&&s.presentation!=null&&s.presentation.isShowing()&&s.display!=null&&s.display.getDisplay().isValid()&&s.wake!=null&&s.wake.isHeld();}else{CollectionService s=CollectionService.active;ready[0]=s!=null&&s.web!=null&&s.web.getWidth()>0&&s.presentation!=null&&s.presentation.isShowing()&&s.display!=null&&s.display.getDisplay().isValid()&&s.wake!=null&&s.wake.isHeld();}});return ready[0];}
 Activity launcher()throws Exception{return launchActivity(LabActivity.class);}
 void startKeWaiting()throws Exception{Activity a=launcher();onMain(()->ctx.startForegroundService(new Intent(ctx,CollectionService.class).putExtra("liveProbe",true).putExtra("__fixture",waiting)));await("KE private browser did not start",15000,()->browserReady(false));onMain(a::finish);}
 void assertSeparateBrowsers(){onMain(()->{CollectionService ke=CollectionService.active;AsianaCollectionService oz=AsianaCollectionService.activeOz;check(ke!=null&&oz!=null,"both collectors must be alive");check(ke.web!=oz.web,"independent browsers");check(ke.display.getDisplay().getDisplayId()!=oz.display.getDisplay().getDisplayId(),"independent private displays");check(ke.web.getSettings().getBlockNetworkLoads()&&oz.web.getSettings().getBlockNetworkLoads(),"all fixture browsers block network loads");});}
 void stopBoth()throws Exception{checkpoint("services.stop.begin");onMain(()->{if(CollectionService.active!=null)CollectionService.active.cancel("오프라인 장치 검사 정리");if(AsianaCollectionService.activeOz!=null)AsianaCollectionService.activeOz.cancel("오프라인 장치 검사 정리");});await("services did not stop",10000,()->CollectionService.active==null&&AsianaCollectionService.activeOz==null);Thread.sleep(150);checkpoint("services.stop.complete");}
 void runFullForm(boolean withKe)throws Exception{
  long start=SystemClock.elapsedRealtime();checkpoint(withKe?"OZ_FORM_WITH_KE.START":"OZ_FORM_SINGLE.START");ctx.getSharedPreferences("collection-oz",0).edit().clear().commit();if(withKe)startKeWaiting();
  Activity a=launcher();String html=form();Intent i=new Intent(ctx,AsianaCollectionService.class).putExtra("liveProbe",true).putExtra("probeDestination","NRT").putExtra("__fixture",html).putExtra("__formFixture",true);
  onMain(()->ctx.startForegroundService(i));await("OZ real private browser did not start",15000,()->browserReady(true));checkpoint("OZ_FORM.BROWSER_READY");
  onMain(a::finish);
  if(withKe){assertSeparateBrowsers();onMain(()->CollectionService.active.cancel("동시 브라우저 시작 검사 완료"));await("KE stop did not complete",10000,()->CollectionService.active==null);check(browserReady(true),"stopping KE must preserve OZ browser");}
  await("OZ form did not complete",65000,()->state(true).has("running")&&!state(true).optBoolean("running",true));
  checkpoint("OZ_FORM.STATE_FINISHED");JSONObject s=state(true);check(s.optString("title").startsWith("조회 완료"),"OZ failed before calendar save: "+s);check(s.optInt("done")==1,"one fixture month must complete: "+s);
  String key="ICN-NRT-"+YearMonth.from(Search.today().plusMonths(1));try(SkyDb db=new SkyDb(ctx,true)){check(db.data(key)!=null,"real service did not save "+key);}
  await("completed OZ service did not release resources",10000,()->AsianaCollectionService.activeOz==null);
  cases.put(new JSONObject().put("name",withKe?"KE then OZ real form and independent stop":"OZ only real form calendar save").put("passed",true).put("elapsedMs",SystemClock.elapsedRealtime()-start).put("savedKey",key));checkpoint(withKe?"OZ_FORM_WITH_KE.PASS":"OZ_FORM_SINGLE.PASS");
 }
 Button fullScan(View view){if(view instanceof Button b&&"전체 노선 검색".contentEquals(b.getText()))return b;if(view instanceof ViewGroup g)for(int n=0;n<g.getChildCount();n++){Button found=fullScan(g.getChildAt(n));if(found!=null)return found;}return null;}
 void pressFullScan(boolean oz)throws Exception{
  Activity a=launchActivity(oz?AsianaActivity.class:MainActivity.class);
  await("full-route button missing",10000,()->{boolean[] found={false};onMain(()->found[0]=fullScan(a.getWindow().getDecorView())!=null);return found[0];});
  checkpoint(oz?"OZ_BUTTON.CLICK":"KE_BUTTON.CLICK");onMain(()->{Button b=fullScan(a.getWindow().getDecorView());check(b!=null&&b.isEnabled(),"full-route button not enabled");check(b.performClick(),"full-route button did not handle click");});
  await("button flow did not reach real private browser",30000,()->browserReady(oz));checkpoint(oz?"OZ_BUTTON.BROWSER_READY":"KE_BUTTON.BROWSER_READY");
 }
 void runButtonStartup(boolean onlyOz)throws Exception{
  long start=SystemClock.elapsedRealtime();checkpoint(onlyOz?"OZ_BUTTON_SINGLE.START":"DUAL_BUTTON.START");ActivityMonitor monitor=new ActivityMonitor(){@Override public ActivityResult onStartActivity(Intent intent){if(intent.getComponent()!=null&&CollectActivity.class.getName().equals(intent.getComponent().getClassName())&&intent.hasExtra("batchKey")){intent.putExtra("__fixture",waiting).putExtra("shared",false);intercepted++;android.util.Log.i("TongDeviceProbe","COLLECT_INTENT_FIXTURE "+intercepted);}return null;}};addMonitor(monitor);
  try{if(!onlyOz)pressFullScan(false);pressFullScan(true);if(!onlyOz)assertSeparateBrowsers();check(browserReady(true),"OZ button startup must keep a real browser");cases.put(new JSONObject().put("name",onlyOz?"AsianaActivity full-route button startup":"KE full-route then OZ full-route button startup").put("passed",true).put("elapsedMs",SystemClock.elapsedRealtime()-start));checkpoint(onlyOz?"OZ_BUTTON_SINGLE.PASS":"DUAL_BUTTON.PASS");}finally{removeMonitor(monitor);}
 }
 void heap(JSONArray samples,String stage)throws Exception{Runtime rt=Runtime.getRuntime();samples.put(new JSONObject().put("stage",stage).put("usedHeapBytes",rt.totalMemory()-rt.freeMemory()).put("maxHeapBytes",rt.maxMemory()));checkpoint("LARGE_CACHE."+stage);}
 // Canonical digest emits one scalar/key at a time; it never serializes the
 // full records map or retains a second canonical copy of the cache body.
 void digestBytes(java.security.MessageDigest digest,String scalar){digest.update(scalar.getBytes(StandardCharsets.UTF_8));}
 void digestValue(java.security.MessageDigest digest,Object value)throws Exception{
  if(value==null||value==JSONObject.NULL){digestBytes(digest,"null");return;}
  if(value instanceof JSONObject object){digestBytes(digest,"{");java.util.ArrayList<String> keys=new java.util.ArrayList<>();for(java.util.Iterator<String> it=object.keys();it.hasNext();)keys.add(it.next());java.util.Collections.sort(keys);for(String key:keys){digestBytes(digest,JSONObject.quote(key));digestBytes(digest,":");digestValue(digest,object.get(key));digestBytes(digest,",");}digestBytes(digest,"}");return;}
  if(value instanceof JSONArray array){digestBytes(digest,"[");for(int n=0;n<array.length();n++){digestValue(digest,array.get(n));digestBytes(digest,",");}digestBytes(digest,"]");return;}
  digestBytes(digest,value instanceof String?JSONObject.quote((String)value):value.toString());
 }
 byte[] recordsDigest(JSONObject body)throws Exception{java.security.MessageDigest digest=java.security.MessageDigest.getInstance("SHA-256");digestValue(digest,body.getJSONObject("records"));return digest.digest();}
 void runLargeCacheRoundTrip()throws Exception{
  long start=SystemClock.elapsedRealtime();checkpoint("LARGE_CACHE.START");cacheMemory=new JSONArray();JSONArray heaps=cacheMemory;heap(heaps,"before.read");
  JSONObject envelope;try(java.io.InputStream in=ctx.getAssets().open("device-cache-fixture.json")){envelope=new JSONObject(new String(in.readAllBytes(),StandardCharsets.UTF_8));}
  check("gzip-base64".equals(envelope.optString("encoding")),"large cache fixture must keep the production gzip envelope");
  JSONObject body=GitHubStore.unpack(envelope);envelope=null;heap(heaps,"after.unpack");
  AsianaData.checkedCarrier(body);heap(heaps,"after.validate");int expectedCount=body.getJSONObject("records").length();check(expectedCount>=1000,"fixture must exercise at least 1000 real records");
  byte[] expected=recordsDigest(body);heap(heaps,"before.pack");envelope=GitHubStore.packed(body);heap(heaps,"after.pack");
  // Only one decoded cache body is held across each half of the round trip.
  body=null;System.gc();Thread.sleep(150);heap(heaps,"original.released");
  body=GitHubStore.unpack(envelope);envelope=null;heap(heaps,"roundtrip.unpacked");AsianaData.checkedCarrier(body);heap(heaps,"roundtrip.validated");
  check(body.getJSONObject("records").length()==expectedCount,"large cache record count changed");check(java.security.MessageDigest.isEqual(expected,recordsDigest(body)),"large cache canonical record digest changed");body=null;System.gc();
  heap(heaps,"complete");cases.put(new JSONObject().put("name","Real Asiana shared cache large-record codec round trip").put("passed",true).put("elapsedMs",SystemClock.elapsedRealtime()-start).put("memory",heaps));checkpoint("LARGE_CACHE.PASS");
 }
 // The backend retains compressed bytes on disk, exactly like production
 // remote reads, and never retains a decoded complete document.
 static final class DiskBackend implements SharedCache.Backend {
  final java.io.File file;int revision=0,reads=0,writes=0;
  DiskBackend(java.io.File file){this.file=file;}
  public SharedCache.Document read()throws Exception{reads++;JSONObject envelope=new JSONObject(new String(java.nio.file.Files.readAllBytes(file.toPath()),StandardCharsets.UTF_8));return new SharedCache.Document("fixture-"+revision,GitHubStore.unpack(envelope));}
  public void write(JSONObject body,String sha)throws Exception{check(("fixture-"+revision).equals(sha),"disk fixture revision conflict");java.nio.file.Files.writeString(file.toPath(),GitHubStore.packed(body).toString(),StandardCharsets.UTF_8);revision++;writes++;}
 }
 record ReuseFixture(String origin,String region,String key,JSONObject query,JSONObject local,long now,int count,byte[] unchangedDigest){}
 byte[] otherRecordsDigest(JSONObject body,String excluded)throws Exception{
  java.security.MessageDigest digest=java.security.MessageDigest.getInstance("SHA-256");JSONObject records=body.getJSONObject("records");java.util.ArrayList<String> keys=new java.util.ArrayList<>();for(java.util.Iterator<String> it=records.keys();it.hasNext();){String key=it.next();if(!excluded.equals(key))keys.add(key);}java.util.Collections.sort(keys);for(String key:keys){digestBytes(digest,key);digestValue(digest,records.getJSONObject(key));}return digest.digest();
 }
 ReuseFixture prepareReuseFixture(DiskBackend backend)throws Exception{
  JSONObject envelope;try(java.io.InputStream in=ctx.getAssets().open("device-cache-fixture.json")){envelope=new JSONObject(new String(in.readAllBytes(),StandardCharsets.UTF_8));}
  JSONObject body=GitHubStore.unpack(envelope);envelope=null;JSONObject records=body.getJSONObject("records");check(records.length()>=1000,"reuse fixture requires 1000 actual records");String key=records.keys().next();long latest=0;for(java.util.Iterator<String> it=records.keys();it.hasNext();)latest=Math.max(latest,records.getJSONObject(it.next()).getLong("success"));
  // A controlled next-day clock makes the actual remote observations expired.
  // Only a separate one-record local test observation receives a fresh success
  // time; source dates and the downloaded public fixture remain unchanged.
  long now=SharedCache.expiresAt(latest)+2000;JSONObject local=new JSONObject(records.getJSONObject(key).toString());local.put("success",now-1000);JSONObject current=local.getJSONObject("current"),query=new JSONObject().put("carrier","OZ").put("origin",current.getString("origin")).put("destination",current.getString("destination")).put("month",current.getString("month"));
  byte[] unchanged=otherRecordsDigest(body,key);int count=records.length();String origin=body.getString("origin"),region=body.getString("region");body.put("lease",new JSONObject().put("owner","device-offline-reuse").put("until",now+SharedCache.LEASE_MS));java.nio.file.Files.writeString(backend.file.toPath(),GitHubStore.packed(body).toString(),StandardCharsets.UTF_8);
  return new ReuseFixture(origin,region,key,query,local,now,count,unchanged);
 }
 void runStagedCacheReuse()throws Exception{
  long start=SystemClock.elapsedRealtime();checkpoint("STAGED_REUSE.START");System.gc();Thread.sleep(100);cacheMemory=new JSONArray();JSONArray heaps=cacheMemory;heap(heaps,"reuse.before.setup");DiskBackend backend=new DiskBackend(new java.io.File(ctx.getCacheDir(),"device-reuse-envelope.json"));ReuseFixture fixture=prepareReuseFixture(backend);System.gc();heap(heaps,"reuse.setup.released");
  SharedCache cache=new SharedCache(backend,fixture.origin(),fixture.region(),"device-offline-reuse","OZ");JSONArray queries=new JSONArray().put(fixture.query());SharedCache.Decision decision=cache.inspect(queries,fixture.now());check(!decision.cached()&&decision.pending().size()==1,"expired remote entry must need local reuse");heap(heaps,"reuse.after.inspect");
  java.util.Map<String,JSONObject> updates=cache.localReuseCandidates(key->fixture.key().equals(key)?fixture.local():null,decision.pending(),fixture.now());check(updates.size()==1,"one fresh local observation must be staged");long originalSuccess=updates.get(fixture.key()).getLong("success");
  // This is the 128 collection preparation order. Holding the old Decision
  // across checkpoint would retain two complete graphs during backend.read.
  decision=null;cache.checkpoint(updates,false,fixture.now());updates.clear();heap(heaps,"reuse.after.checkpoint");decision=cache.inspect(queries,fixture.now());check(decision.cached()&&decision.pending().isEmpty(),"checkpointed local observation must satisfy the query");
  JSONObject body=decision.body(),saved=body.getJSONObject("records").getJSONObject(fixture.key());check(body.getJSONObject("records").length()==fixture.count(),"local reuse changed the complete-cache record count");check(saved.getLong("success")==originalSuccess,"reuse must preserve the local success time");check(java.util.Arrays.equals(otherRecordsDigest(body,fixture.key()),fixture.unchangedDigest()),"reuse changed unrelated cached records");check(Search.sameDates(saved.getJSONObject("current").getJSONArray("dates"),fixture.local().getJSONObject("current").getJSONArray("dates")),"local dates changed while merging");heap(heaps,"reuse.after.verify");
  decision=null;body=null;saved=null;System.gc();heap(heaps,"reuse.complete");check(backend.reads==3&&backend.writes==1,"staged reuse should inspect, checkpoint, then inspect once");backend.file.delete();cases.put(new JSONObject().put("name","Large actual cache staged fresh-local reuse checkpoint").put("passed",true).put("elapsedMs",SystemClock.elapsedRealtime()-start).put("records",fixture.count()).put("backendReads",backend.reads).put("backendWrites",backend.writes).put("memory",heaps));checkpoint("STAGED_REUSE.PASS");
 }
 Button namedButton(View view,String label){if(view instanceof Button b&&label.contentEquals(b.getText()))return b;if(view instanceof ViewGroup g)for(int n=0;n<g.getChildCount();n++){Button found=namedButton(g.getChildAt(n),label);if(found!=null)return found;}return null;}
 TextView namedText(View view,String text){if(view instanceof TextView t&&text.contentEquals(t.getText()))return t;if(view instanceof ViewGroup g)for(int n=0;n<g.getChildCount();n++){TextView found=namedText(g.getChildAt(n),text);if(found!=null)return found;}return null;}
 int browserCount(View view){int n=view instanceof WebView?1:0;if(view instanceof ViewGroup g)for(int i=0;i<g.getChildCount();i++)n+=browserCount(g.getChildAt(i));return n;}
 void runAirFranceTabs()throws Exception{
  long start=SystemClock.elapsedRealtime();checkpoint("AF_TEST_TAB.START");for(boolean oz:new boolean[]{false,true}){
   Activity parent=launchActivity(oz?AsianaActivity.class:MainActivity.class);await("AF test tab missing",10000,()->{boolean[] found={false};onMain(()->found[0]=namedButton(parent.getWindow().getDecorView(),"에어프랑스 (테스트중)")!=null);return found[0];});
   ActivityMonitor monitor=addMonitor(AirFranceActivity.class.getName(),null,false);try{onMain(()->check(namedButton(parent.getWindow().getDecorView(),"에어프랑스 (테스트중)").performClick(),"AF tab did not handle click"));Activity af=waitForMonitorWithTimeout(monitor,10000);check(af instanceof AirFranceActivity,"AF tab did not open independent test activity");await("AF test page missing",8000,()->{boolean[] found={false};onMain(()->found[0]=namedText(af.getWindow().getDecorView(),"테스트중")!=null&&namedButton(af.getWindow().getDecorView(),"돌아가기")!=null);return found[0];});onMain(()->{check("에어프랑스 (테스트중)".contentEquals(af.getTitle()),"AF test title mismatch");check(browserCount(af.getWindow().getDecorView())==0,"test tab must not create a browser");check(CollectionService.active==null&&AsianaCollectionService.activeOz==null,"AF test tab started a collector");check(namedButton(af.getWindow().getDecorView(),"돌아가기").performClick(),"AF back click failed");});await("AF test activity did not finish",5000,af::isFinishing);check(!parent.isFinishing(),"AF back must preserve the carrier dashboard");}finally{removeMonitor(monitor);onMain(parent::finish);}
  }cases.put(new JSONObject().put("name","Air France test tab from both existing carrier dashboards and back").put("passed",true).put("elapsedMs",SystemClock.elapsedRealtime()-start).put("label","에어프랑스 (테스트중)").put("browserCreated",false));checkpoint("AF_TEST_TAB.PASS");
 }
 long journalCount(ScanJournal journal,String where,String[] args){try(android.database.Cursor cursor=journal.getReadableDatabase().rawQuery("SELECT COUNT(*) FROM events WHERE "+where,args)){check(cursor.moveToFirst(),"journal count unavailable");return cursor.getLong(0);}}
 void runDurableCrashRecord()throws Exception{
  long start=SystemClock.elapsedRealtime();checkpoint("CRASH_RECORD.START");java.io.File directory=new java.io.File(ctx.getFilesDir(),"device-crash-fixture");check(directory.isDirectory()||directory.mkdirs(),"crash fixture directory failed");java.io.File file=new java.io.File(directory,CrashCapture.FILE);new android.util.AtomicFile(file).delete();int[] chained={0};String crashId=java.util.UUID.randomUUID().toString();long now=System.currentTimeMillis();
  CrashCapture.Handler handler=new CrashCapture.Handler((thread,failure)->chained[0]++,(thread,failure)->CrashCapture.write(file,CrashCapture.record(thread,failure,crashId,android.os.Process.myPid(),SystemClock.elapsedRealtime(),now,SystemClock.elapsedRealtime())));
  handler.uncaughtException(Thread.currentThread(),new IllegalStateException("offline-private-message-must-not-be-recorded"));check(chained[0]==1,"original uncaught handler must be called exactly once");JSONObject record=CrashCapture.read(file);check(record!=null&&file.length()<=CrashCapture.MAX_BYTES,"atomic bounded crash file missing");check(!record.toString().contains("offline-private-message"),"Throwable message leaked into crash evidence");
  try(ScanJournal journal=new ScanJournal(ctx)){long history=journalCount(journal,"carrier IN ('KE','OZ')",null);CrashCapture.importPending(file,journal);check(!file.exists(),"successfully imported crash file was not removed");check(journalCount(journal,"carrier='APP' AND code='JAVA_CRASH' AND payload LIKE ?",new String[]{"%"+crashId+"%"})==1,"APP crash row missing");CrashCapture.write(file,record);CrashCapture.importPending(file,journal);check(!file.exists(),"duplicate imported crash file was not removed");check(journalCount(journal,"carrier='APP' AND code='JAVA_CRASH' AND payload LIKE ?",new String[]{"%"+crashId+"%"})==1,"crash import duplicated history");check(journalCount(journal,"carrier IN ('KE','OZ')",null)==history,"APP crash import changed carrier history");}
  directory.delete();cases.put(new JSONObject().put("name","Atomic uncaught crash capture and durable deduplicated SQLite import").put("passed",true).put("elapsedMs",SystemClock.elapsedRealtime()-start).put("handlerChained",true).put("exceptionMessageRetained",false));checkpoint("CRASH_RECORD.PASS");
 }
 String form(){
  YearMonth m=YearMonth.from(Search.today().plusMonths(1));String ym=m.toString().replace('-','.');String shortYm=ym.substring(2);String source=ZonedDateTime.now(Search.KST).format(DateTimeFormatter.ofPattern("yyyy. MM. dd. HH:mm",java.util.Locale.ROOT));
  StringBuilder h=new StringBuilder("<!doctype html><meta name='viewport' content='width=device-width,initial-scale=1'><style>body{margin:0;padding:20px}a,button{display:block;padding:12px;margin:12px}section{display:none;position:fixed;top:20px;left:5px;width:300px;height:600px;background:white;z-index:4}#depCalendar{display:none}</style><input type='hidden' id='tripType' value='RT'><a href='javascript:void(0)' onclick=\"document.getElementById('tripType').value='OW';this.dataset.selected='yes'\">편도</a><input id='departureAirport1'><input id='arrivalAirport1'><input id='departureDate1'><a id='btn_depAllAirport1' href='javascript:void(0)' onclick=\"document.getElementById('depAllAirport1').style.display='block'\">출발 도시</a><a id='btn_arrAllAirport1' href='javascript:void(0)' onclick=\"document.getElementById('arrAllAirport1').style.display='block'\">도착 도시</a><section id='depAllAirport1'><ul><li airport='ICN' area='KR' mgtarea='KR'><a href='#none' onclick=\"document.getElementById('departureAirport1').value='ICN';document.getElementById('depAllAirport1').style.display='none'\">서울 인천 ICN</a></li></ul></section><section id='arrAllAirport1'><ul><li airport='NRT' area='CE' mgtarea='CE'><a href='#none' onclick=\"document.getElementById('arrivalAirport1').value='NRT';document.getElementById('arrAllAirport1').style.display='none'\">나리타 NRT</a></li></ul></section><button id='sCalendarMonth' onclick=\"document.getElementById('months').style.display='block'\">탑승월</button><section id='months'><button class='month_btn' data-year='");
  h.append(m.getYear()).append("' data-month='").append(m.getMonthValue()).append("' onclick=\"document.getElementById('departureDate1').value='").append(ym).append("';document.getElementById('span_dep_date').textContent='").append(shortYm).append("';document.getElementById('months').style.display='none'\">탑승월</button></section><span id='span_dep_date'></span><button id='btn_MileageSeat_search' onclick=\"document.getElementById('depCalendar').style.display='block'\">조회</button><p>좌석 상황은 대한민국 시간 (").append(source).append(")</p><div id='depCalendar'><h2>").append(m.getYear()).append("년 ").append(m.getMonthValue()).append("월</h2>");
  for(int day=1;day<=m.lengthOfMonth();day++)h.append("<a id='cal_").append(day).append("' data-date='").append(m.atDay(day).toString().replace('-','.')).append("' data-economy='[]' data-business='[]'>").append(day).append("</a>");return h.append("</div>").toString();
 }
}

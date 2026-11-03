package kr.tong.sky;

import android.app.*;import android.os.*;import android.content.*;import android.net.*;import android.net.http.SslError;import android.webkit.*;import android.view.*;import android.widget.*;import org.json.*;import java.util.*;import java.nio.charset.StandardCharsets;

public final class CollectionService extends Service {
 static CollectionService active;
 static Service active(boolean oz){return oz?AsianaCollectionService.activeOz:active;}
 volatile ScannerLease scannerLease;boolean leaseCheck;ArrayList<String> allKeys;int regionIndex,allTotal,allDone,allFailures;long sessionStarted=SystemClock.elapsedRealtime();
 static final int NOTICE=910;static final String CHANNEL="collection";static final String STOP="kr.tong.sky.STOP_COLLECTION";
 static final long MAX_SESSION_MS=5*60*60*1000L+45*60*1000L;
 android.hardware.display.VirtualDisplay display;android.media.ImageReader images;Presentation presentation;PowerManager.WakeLock wake;Intent request;volatile boolean finished=false,closed=false;boolean startupFailed=false,foregroundStarted=false;String fixture;String batchKey="";long lastNotification=0;JSONObject lastState;String publishedState="";
 volatile Exception sharedFailure;volatile SharedCache shared;volatile SharedBatcher sharedBatcher;volatile int sharedPending;volatile boolean regionReleased;long nextQueryAt;String statusMessage="";final Runnable nextQueryTick=this::startNext;String decisionMessage="",estimateMessage="";volatile boolean ending=false;final java.util.concurrent.ExecutorService sharedWorker=java.util.concurrent.Executors.newSingleThreadExecutor();
 Intent getIntent(){return request;}boolean isFinishing(){return finished||closed||ending;}
 interface CheckedAction {void run()throws Exception;}
 static String failureMessage(String message){return message==null||message.trim().isEmpty()?"조회 중 오류가 발생해 중단했습니다.":message;}
 void recordException(Exception error){try{if(diagnostic!=null)diagnostic.exception(error);}catch(RuntimeException ignored){}}
 void runSafely(String context,CheckedAction action){if(isFinishing())return;try{action.run();}catch(Exception error){recordException(error);cancel(context+": "+failureMessage(error.getMessage()));}}
 boolean currentBrowser(int run,WebView expected){return expected!=null&&expected==web&&run==token&&!paused&&!isFinishing();}
 void runBrowserSafely(int run,WebView expected,String context,CheckedAction action){if(!currentBrowser(run,expected))return;runSafely(context,action);}
 void runQuerySafely(int run,WebView expected,String context,CheckedAction action){if(!running)return;runBrowserSafely(run,expected,context,action);}
 void runWebEventSafely(WebView expected,String context,CheckedAction action){if(expected!=web)return;runSafely(context,action);}
 void stopForegroundNow(){try{stopForeground(STOP_FOREGROUND_REMOVE);}catch(RuntimeException ignored){}foregroundStarted=false;}
 void stopSelfSafely(){try{stopSelf();}catch(RuntimeException ignored){}}
 void stopPlatformService(){stopForegroundNow();stopSelfSafely();}
 void emergencyStop(String message,Exception error){
  if(closed)return;recordException(error);lastError=failureMessage(message);finished=true;ending=true;running=false;paused=true;typing=false;token++;if(sharedBatcher!=null)sharedBatcher.stop();handler.removeCallbacksAndMessages(null);
  try{if(diagnostic!=null){diagnostic.stop(lastError);diagnostic.finish(false,lastError);}}catch(RuntimeException ignored){}
  try{if(lastState==null)lastState=new JSONObject();lastState.put("appVersion",BuildConfig.VERSION_NAME).put("running",false).put("message",lastError).put("title","수집 중단");getSharedPreferences("collection",0).edit().putString("state",lastState.toString()).apply();}catch(Exception ignored){}
  try{if(status!=null)status.setText(lastError);}catch(RuntimeException ignored){}stopPlatformService();
 }

 static final String SOURCE="https://www.koreanair.com/booking/book-and-manage/award-seat-availability";
 WebView web;TextView status,progressTitle,progressInfo;ProgressBar progress;SkyDb db;final Handler handler=new Handler(Looper.getMainLooper());
 CollectionDiagnostics diagnostic;JSONObject lastSuccessfulQuery;boolean reuseDocument,coldFallback,searchSubmitted;long documentEpoch;
 final List<JSONObject> queue=new ArrayList<>();static final long BETWEEN_QUERIES_MS=1500;JSONObject regionBatch;int failures=0;int priorCompleted=0;int index=0;JSONObject query;String script,stage="consent";long stageAt,totalAt;boolean running=false,paused=false,typing=false;int token=0,success=0;String lastError="",lastWait="";
 static boolean airline(String url){try{Uri u=Uri.parse(url);String h=u.getHost();return "https".equals(u.getScheme())&&(u.getPort()==-1||u.getPort()==443)&&("koreanair.com".equals(h)||"www.koreanair.com".equals(h));}catch(Exception e){return false;}}
 static boolean collectionUrl(String url){try{return airline(url)&&Uri.parse(url).getPath().equals("/booking/book-and-manage/award-seat-availability");}catch(Exception e){return false;}}
 boolean browserUrl(String url){return collectionUrl(url);}
 boolean legacyAwardFixture(){return BuildConfig.DEBUG&&fixture!=null;}
 @Override public void onCreate(){
  super.onCreate();active=this;
  try{
   SkyLocale.apply(this);status=new TextView(this);progressTitle=new TextView(this);progressInfo=new TextView(this);progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setMax(100);
   if(!BuildConfig.SCANNER){startupFailed=true;finished=true;if(active==this)active=null;stopSelf();return;}
   NotificationManager n=getSystemService(NotificationManager.class);n.createNotificationChannel(new NotificationChannel(CHANNEL,"지역별 수집 진행",NotificationManager.IMPORTANCE_LOW));
   // Promote before database migration or other startup work. A second carrier can be rejected by Android independently.
   startForeground(NOTICE,notice("수집 준비 중",0,true));foregroundStarted=true;
   db=new SkyDb(this);diagnostic=new CollectionDiagnostics(this,"KE",db);
   script=new String(getAssets().open("calendar.js").readAllBytes(),StandardCharsets.UTF_8);
  }catch(Exception error){failStartup(error);}
 }
 void failStartup(Exception error){
  startupFailed=true;finished=true;running=false;paused=true;typing=false;token++;handler.removeCallbacksAndMessages(null);
  lastError="수집 서비스를 시작하지 못했습니다. 기존 조회 결과는 유지됩니다.";
  if(active==this)active=null;
  // Stop the failed request before optional diagnostics; never alter the other carrier's service or lease.
  if(foregroundStarted){try{stopForeground(STOP_FOREGROUND_REMOVE);}catch(RuntimeException ignored){}foregroundStarted=false;}
  try{stopSelf();}catch(RuntimeException ignored){}
  try{if(db==null)db=new SkyDb(this);if(diagnostic==null)diagnostic=new CollectionDiagnostics(this,"KE",db);diagnostic.exception(error);diagnostic.failure("SERVICE_START_FAILED",lastError,false);diagnostic.finish(false,lastError);}catch(Exception ignored){}
  try{JSONObject state=new JSONObject().put("appVersion",BuildConfig.VERSION_NAME).put("running",false).put("title","수집 시작 실패").put("message",lastError).put("percent",0).put("done",0).put("total",0);lastState=state;getSharedPreferences("collection",0).edit().putString("state",state.toString()).apply();if(status!=null)status.setText(lastError);}catch(Exception ignored){}
 }
 @Override public int onStartCommand(Intent intent,int flags,int id){
  if(startupFailed||closed||finished){stopSelf();return START_NOT_STICKY;}
  if(intent==null){end("수집이 중단됐습니다. 앱에서 이어서 시작하세요.",false);return START_NOT_STICKY;}
  if(STOP.equals(intent.getAction())){cancel("사용자가 알림에서 수집을 중단했습니다.");return START_NOT_STICKY;}
  if(request!=null)return START_REDELIVER_INTENT;request=intent;batchKey=intent.getStringExtra("batchKey");if(batchKey==null)batchKey="";
  fixture=BuildConfig.DEBUG?intent.getStringExtra("__fixture"):null;allKeys=intent.getStringArrayListExtra("allBatchKeys");
  boolean share=intent.getBooleanExtra("shared",false);setStatus("조회 대상 목록을 준비하고 있습니다.");
  sharedWorker.execute(()->{
   if(isFinishing())return;
   try{
   if(allKeys!=null){
    if(allKeys.isEmpty()||!allKeys.get(0).equals(batchKey))throw new Exception("전체 조회 목록 오류");
    ArrayList<String> eligible=new ArrayList<>();for(String key:allKeys){if(isFinishing())return;JSONObject batch=eligibleBatch(key);int count=batch.getJSONArray("queries").length();if(count>0){eligible.add(key);allTotal+=count;}}
    allKeys=eligible;if(allKeys.isEmpty()){handler.post(()->{if(!isFinishing())end("현재 조회 가능한 노선이 없습니다. 저장된 달력은 유지합니다.",false);});return;}batchKey=allKeys.get(0);
   }else if(!batchKey.isEmpty()&&eligibleBatch(batchKey).getJSONArray("queries").length()==0){handler.post(()->{if(!isFinishing())end("현재 조회 가능한 노선이 없습니다. 저장된 달력은 유지합니다.",false);});return;}

    handler.post(()->{if(isFinishing())return;if(share)prepareShared();else try{begin();}catch(Exception error){if(diagnostic!=null)diagnostic.exception(error);cancel("수집 준비 오류: "+error.getMessage());}});
   }catch(Exception error){if(diagnostic!=null)diagnostic.exception(error);handler.post(()->{if(!isFinishing())cancel("수집 목록 확인 오류: "+error.getMessage());});}
  });return START_REDELIVER_INTENT;
 }
 JSONObject eligibleBatch(String key)throws Exception{JSONObject previous=db.batch(key);if(previous==null)throw new Exception("수집 목록 없음");JSONObject batch=RouteRules.filterBatch(previous,"KE",Search.today(),Search.lastDate(false));batch=PageReuse.orderRemaining(batch);db.batch(batch);return batch;}
 void prepareShared(){runSafely("공유 자료 준비 오류",()->prepareSharedOwned());}
 void prepareSharedOwned(){if(diagnostic!=null)diagnostic.prepare(batchKey,doneCount(),totalCount());
  setStatus("GitHub에서 마지막 수집 성공일 확인 중 · 한국 시간 매일 00시 기준");
  sharedWorker.execute(()->{try{SharedWorkCoordinator.run(()->{if(!isFinishing())prepareSharedTransaction();});}
   catch(Exception e){if(shared!=null)sharedFailure=e;if(diagnostic!=null)diagnostic.exception(e);handler.post(()->{if(!closed)cancel("공유 자료 확인 중단 · 기존 달력 유지 · "+e.getMessage());});}});
 }
 void prepareSharedTransaction()throws Exception{
   ensureLease();JSONObject batch=db.batch(batchKey);if(batch==null)throw new Exception("수집 목록 없음");if("OZ".equals(batch.optString("carrier")))throw new Exception("대한항공 수집 목록이 아닙니다.");String origin=batch.getString("origin"),region=batch.getString("region");JSONArray queries=batch.getJSONArray("queries");SharedCache client=GitHubStore.client(this,origin,region,java.util.UUID.randomUUID().toString());
   SharedCache.Decision decision=null;
   try{
   client.recoverRegionLease(scannerLease,System.currentTimeMillis(),SystemClock.elapsedRealtime());decision=client.inspect(queries,System.currentTimeMillis());decisionMessage=QueryEstimate.decision(decision.document(),queries,System.currentTimeMillis());estimateMessage=QueryEstimate.estimate(this,decision.pending().size());handler.post(()->publish(false));
   int recoverPending=db.sharedPendingCount(batchKey);sharedPending=recoverPending;
   if((!decision.cached()||recoverPending>0)&&!decision.busy()){
    if(!(BuildConfig.DEBUG&&GitHubStore.hasTestBackend(region))&&GitHubToken.read(this).isEmpty()){if(recoverPending==0)SharedCache.importAllRegion(db,decision.document());handler.post(()->end("오늘 사용할 유효한 GitHub 공유 자료가 없습니다. 설정 → GitHub 저장 연결을 설정하면 이 휴대폰에서 수집하고 공유합니다. 기존 달력은 유지합니다.",false));return;}
    // The inspection body is no longer needed; do not retain two full caches while claiming.
    decision.close();decision=null;decision=client.claim(queries,System.currentTimeMillis(),recoverPending>0);if(!decision.busy()&&(!decision.cached()||recoverPending>0)){shared=client;regionReleased=false;}
   }
   decisionMessage=QueryEstimate.decision(decision.document(),queries,System.currentTimeMillis());estimateMessage=QueryEstimate.estimate(this,decision.pending().size());
   if(recoverPending>0){
    if(decision.busy()){handler.post(()->end("다른 휴대폰이 공유 자료를 사용 중입니다. 이 휴대폰의 저장·공유 대기 자료는 유지합니다.",false));return;}
    decision.close();decision=null;SharedBatcher.drain(db,batchKey,client,false,System.currentTimeMillis(),this::ensureLease);sharedPending=db.sharedPendingCount(batchKey);decision=client.inspect(queries,System.currentTimeMillis());
   }
   if(isFinishing()){decision.close();decision=null;flushSharedAndRelease();return;}
   if(shared!=null){
    long reuseAt=System.currentTimeMillis();java.util.List<JSONObject> pending=decision.pending();
    // No Decision or bulk local snapshot map is retained during another region read.
    decision.close();decision=null;client.reuseLocalRegion(db,pending,reuseAt);decision=client.inspect(queries,reuseAt);
    decisionMessage=QueryEstimate.decision(decision.document(),queries,System.currentTimeMillis());estimateMessage=QueryEstimate.estimate(this,decision.pending().size());
   }
   SharedCache.importAllRegion(db,decision.document());
   if(shared!=null)handler.post(()->{if(!isFinishing()&&shared==client)ensureSharedBatcher().start();});
   if(decision.cached()){long oldest=decision.oldestSuccess(queries);batch.put("cursor",queries.length()).put("completedAt",oldest).put("failures",0);db.batch(batch);handler.post(()->{priorCompleted=queries.length();updateProgress(false);end("기존 조회 정보가 있습니다. 한국 시간 오늘 다른 휴대폰 또는 이 휴대폰이 수집한 GitHub 달력을 가져왔습니다. 항공사에 재조회하지 않았습니다.",true);});return;}
   if(decision.busy()){handler.post(()->end("다른 휴대폰에서 같은 출발지·지역을 수집 중입니다. 현재 저장된 결과를 가져왔으며 중복 조회하지 않습니다. 완료 후 조회를 누르면 최신 자료를 받습니다.",false));return;}
   java.util.Set<String> pendingKeys=new java.util.HashSet<>();for(JSONObject q:decision.pending())pendingKeys.add(Search.key(q));JSONArray ordered=new JSONArray();for(int i=0;i<queries.length();i++)if(!pendingKeys.contains(Search.key(queries.getJSONObject(i))))ordered.put(queries.getJSONObject(i));int reused=ordered.length();for(JSONObject q:decision.pending())ordered.put(q);
   batch.put("queries",ordered).put("cursor",reused).put("completedAt",0).put("failures",0).put("error","");db.batch(batch);
   handler.post(()->{if(isFinishing())return;try{begin();}catch(Exception e){if(diagnostic!=null)diagnostic.exception(e);cancel("수집 준비 오류: "+e.getMessage());}});
   }finally{if(decision!=null)decision.close();}
 }
 SharedBatcher ensureSharedBatcher(){
  if(sharedBatcher!=null)return sharedBatcher;
  final SharedCache owner=shared;final String scope=batchKey;
  sharedBatcher=new SharedBatcher(db,scope,owner,handler,sharedWorker,this::ensureLease,new SharedBatcher.Listener(){
   public void pending(int count){if(shared==owner&&batchKey.equals(scope)){sharedPending=count;if(status!=null)renderStatus();}}
   public void failed(Exception error){if(shared==owner&&!closed){sharedFailure=error;recordException(error);cancel("휴대폰 저장 완료 · GitHub 공유 실패 · 공유 대기 자료는 유지합니다. · "+failureMessage(error.getMessage()));}}
  });return sharedBatcher;
 }
 void publishShared(JSONObject saved,Runnable continuation){
  final int run=token;final WebView expected=web;
  if(shared==null){runBrowserSafely(run,expected,"저장 후 조회 진행 오류",continuation::run);return;}
  ensureSharedBatcher().saved(()->runBrowserSafely(run,expected,"저장 후 조회 진행 오류",continuation::run));
 }
 void scheduleNext(long delay){handler.removeCallbacks(nextQueryTick);handler.postDelayed(nextQueryTick,Math.max(0,delay));}
 boolean cooling(){long remaining=nextQueryAt-SystemClock.elapsedRealtime();if(remaining<=0)return false;scheduleNext(remaining);return true;}
 long nextQueryDelay(boolean another){return another?Math.max(0,nextQueryAt-SystemClock.elapsedRealtime()):300;}
 void flushSharedAndRelease()throws Exception{
  if(shared==null||regionReleased||closed)return;
  if(sharedFailure!=null)throw sharedFailure;
  if(sharedBatcher!=null&&sharedBatcher.failure()!=null)throw sharedBatcher.failure();
  if(scannerLease!=null)scannerLease.ensure();
  if(batchKey.isEmpty())shared.checkpoint(java.util.Map.of(),true,System.currentTimeMillis());
  else{SharedBatcher.drain(db,batchKey,shared,true,System.currentTimeMillis(),()->{if(scannerLease!=null)scannerLease.ensure();});sharedPending=db.sharedPendingCount(batchKey);}
  regionReleased=true;
 }
 void createBrowser(){
  // This private display renders only this app's browser. It never mirrors or captures the phone display.
  images=android.media.ImageReader.newInstance(640,1680,android.graphics.PixelFormat.RGBA_8888,2);images.setOnImageAvailableListener(r->{try(android.media.Image frame=r.acquireLatestImage()){}catch(Exception ignored){}},handler);
  display=getSystemService(android.hardware.display.DisplayManager.class).createVirtualDisplay("TongSKY collection",640,1680,320,images.getSurface(),android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY|android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION);
  if(display==null)throw new IllegalStateException("백그라운드 조회 화면 생성 실패");presentation=new Presentation(this,display.getDisplay(),R.style.AppTheme);web=new WebView(presentation.getContext());presentation.setContentView(web);presentation.getWindow().addFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);presentation.show();web.onResume();
  wake=getSystemService(PowerManager.class).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"TongSKY:collection");wake.acquire(MAX_SESSION_MS+60000);handler.postDelayed(()->cancel("안드로이드 실행 제한에 대비해 저장 후 중단했습니다. 다음 실행에서 이어집니다."),MAX_SESSION_MS);
 }
 void begin(){runSafely("조회 화면 준비 오류",()->beginOwned());}
 void beginOwned()throws Exception{
  if(diagnostic!=null)diagnostic.step("browser_start");if(web==null)createBrowser();
  if(diagnostic!=null)diagnostic.step("browser_configure");WebSettings s=web.getSettings();s.setJavaScriptEnabled(true);if(fixture!=null)s.setBlockNetworkLoads(true);s.setDomStorageEnabled(true);s.setAllowFileAccess(false);s.setAllowContentAccess(false);s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);s.setSupportMultipleWindows(false);s.setSafeBrowsingEnabled(true);CookieManager.getInstance().setAcceptThirdPartyCookies(web,false);
  // Default WebView identity and real keyboard events. No token/cookie copying or security-check bypass.
  web.setWebViewClient(new WebViewClient(){
   @Override public void onPageStarted(WebView view,String url,android.graphics.Bitmap icon){if(view==web){documentEpoch++;clearPageReuse();}}
   @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){if(v!=web||isFinishing())return true;try{if(!browserUrl(r.getUrl().toString())){cancel("공개 조회 화면을 벗어나 자동 처리를 중단했습니다.");return true;}return false;}catch(Exception error){recordException(error);cancel("조회 화면 이동 확인 오류: "+failureMessage(error.getMessage()));return true;}}
   @Override public void onReceivedHttpError(WebView v,WebResourceRequest r,WebResourceResponse response){runWebEventSafely(v,"조회 응답 확인 오류",()->{if(r.getUrl().getHost()!=null&&(r.getUrl().getHost().equals("koreanair.com")||r.getUrl().getHost().endsWith(".koreanair.com"))&&Arrays.asList(401,403,429).contains(response.getStatusCode()))handler.post(()->runWebEventSafely(v,"조회 응답 확인 오류",()->cancel("대한항공 접근 제한 HTTP "+response.getStatusCode()+" · 자동 재시도하지 않습니다.")));});}
   @Override public void onReceivedError(WebView v,WebResourceRequest r,WebResourceError e){runWebEventSafely(v,"조회 연결 확인 오류",()->{if(r.isForMainFrame()){cancel("연결 오류로 중단했습니다. 오류 코드 "+e.getErrorCode()+" · "+e.getDescription()+" · 마지막 정상 결과는 유지됩니다.");}});}
   @Override public boolean onRenderProcessGone(WebView v,RenderProcessGoneDetail detail){boolean crashed=false;try{crashed=detail!=null&&detail.didCrash();}catch(RuntimeException ignored){}return handleRendererExit(v,crashed);}
   @Override public void onReceivedSslError(WebView v,SslErrorHandler h,SslError e){try{h.cancel();}catch(RuntimeException error){recordException(error);}runWebEventSafely(v,"인증서 오류 처리 실패",()->cancel("인증서 오류로 연결을 중단했습니다."));}
  });
  // Instrumentation-only probe is gated by a debuggable build. Production has no exported probe surface.
  try{if(BuildConfig.DEBUG&&getIntent().getBooleanExtra("liveProbe",false)){query=new JSONObject().put("carrier","KE").put("origin","ICN").put("destination",getIntent().getStringExtra("probeDestination")==null?"CDG":getIntent().getStringExtra("probeDestination")).put("month",Search.today().plusMonths(1).withDayOfMonth(1).toString().substring(0,7));queue.add(query);}else if(getIntent().hasExtra("batchKey")){regionBatch=db.batch(batchKey);if(regionBatch==null)throw new Exception("수집 목록이 없습니다.");priorCompleted=regionBatch.getInt("cursor");failures=regionBatch.optInt("failures");JSONArray all=regionBatch.getJSONArray("queries");for(int i=priorCompleted;i<all.length();i++)queue.add(all.getJSONObject(i));}else queue.addAll(db.pending(false));}catch(Exception e){if(diagnostic!=null)diagnostic.exception(e);cancel("수집 목록 오류: "+e.getMessage());return;}
  if(queue.isEmpty()){end("조회할 항목이 없습니다.",false);return;}startNext();
 }
 boolean handleRendererExit(WebView view,boolean crashed){
  if(view==null)return true;
  // A callback from an old renderer must not stop its replacement or the other airline.
  if(view==web){
   web=null;String message="웹 화면이 종료되어 조회를 중단했습니다. 프로세스 충돌 "+crashed+" · 저장된 결과는 유지됩니다.";
   try{cancel(message);}catch(RuntimeException error){
    running=false;paused=true;typing=false;ending=true;token++;handler.removeCallbacksAndMessages(null);lastError=message;
    try{if(diagnostic!=null){diagnostic.exception(error);diagnostic.failure("WEBVIEW_EXIT",message,false);}}catch(RuntimeException ignored){}
    try{stopSelf();}catch(RuntimeException ignored){}
   }
  }
  // Clear the service reference before cancellation can trigger teardown, then release each resource independently.
  try{if(view.getParent() instanceof android.view.ViewGroup parent)parent.removeView(view);}catch(RuntimeException ignored){}
  try{view.destroy();}catch(RuntimeException ignored){}
  return true;
 }
 void ensureLease()throws Exception{if(BuildConfig.DEBUG&&fixture!=null)return;scannerLease=LocalScannerLease.acquire(this,this);}
 void startNext(){runSafely("다음 조회 준비 오류",this::startNextQueued);}
 void startNextQueued(){if(running||isFinishing()||paused||leaseCheck)return;if(index<queue.size()&&cooling())return;if(sharedBatcher!=null&&!sharedBatcher.canProceed(this::startNext))return;leaseCheck=true;sharedWorker.execute(()->{try{ensureLease();handler.post(()->{leaseCheck=false;if(!isFinishing())startNextOwned();});}catch(Exception e){handler.post(()->{leaseCheck=false;cancel(e.getMessage());});}});}
 void startNextOwned(){runSafely("조회 화면 시작 오류",()->startNextOwnedUnsafe());}
 void startNextOwnedUnsafe(){if(running||paused||isFinishing())return;if(index<queue.size()&&cooling())return;if(sharedBatcher!=null&&!sharedBatcher.canProceed(this::startNext))return;if(index>=queue.size()){running=false;updateProgress(false);if(regionBatch!=null){try{regionBatch.put("completedAt",System.currentTimeMillis());db.batch(regionBatch);}catch(Exception e){if(diagnostic!=null)diagnostic.exception(e);cancel("수집 상태 저장 오류");return;}}setStatus("수집 종료 · 성공 "+(priorCompleted+index-failures)+" · 미확인 "+failures+"\n"+(lastError.isEmpty()?failures==0?"저장된 달력에서 확인할 수 있습니다.":"미확인 항목은 좌석 없음이 아닙니다. 마지막 정상 자료를 유지했습니다.":lastError));if(BuildConfig.DEBUG)probeRecord();end(status.getText().toString(),true);return;}
  query=queue.get(index);java.time.LocalDate limit=Search.lastDate(false);if(query.optString("month").compareTo(java.time.YearMonth.from(limit).toString())>0){cancel("조회 범위는 "+limit+"까지입니다. 지역 검색을 다시 시작하세요.");return;}boolean reuse="reuse".equals(PageReuse.stage("KE",lastSuccessfulQuery,query,collectionUrl(web.getUrl()),reuseDocument));coldFallback=false;searchSubmitted=false;stage=reuse?"reuse":"consent";if(!reuse)clearPageReuse();lastWait="";stageAt=totalAt=SystemClock.elapsedRealtime();running=true;if(diagnostic!=null)diagnostic.start(query,batchKey,stage,doneCount(),totalCount());int run=++token;updateProgress(false);setStatus((index+1)+"/"+queue.size()+" · "+Search.key(query)+"\n공식 화면에서 조회 중");if(fixture!=null){try{query.put("testSource","2026-09-06T23:00:00+09:00");}catch(Exception e){if(diagnostic!=null)diagnostic.exception(e);cancel("검증 자료 설정 오류");return;}stage="parse";web.loadDataWithBaseURL(SOURCE,fixture.replace("ICN","__TONG_ORIGIN__").replace("CDG",query.optString("destination")).replace("__TONG_ORIGIN__",query.optString("origin")),"text/html","UTF-8",SOURCE);handler.postDelayed(()->tick(run),12000);}else if(reuse){handler.post(()->tick(run));}else{web.loadUrl(SOURCE,java.util.Map.of("Accept-Language","ko-KR,ko;q=0.9"));handler.postDelayed(()->tick(run),800);}
 }
 void tick(int run){final WebView expected=web;runQuerySafely(run,expected,"조회 화면 실행 오류",()->tickOwned(run,expected));}
 void tickOwned(int run,WebView expected)throws Exception{if(!(BuildConfig.DEBUG&&fixture!=null)&&(scannerLease==null||!scannerLease.valid())){cancel("공통 수집 잠금 만료 · 중단합니다.");return;}if(!running||paused||run!=token||isFinishing())return;if("parse_test".equals(stage)&&!legacyAwardFixture()){cancel("검증 전용 조회 단계는 일반 수집에서 사용할 수 없습니다.");return;}if(typing){handler.postDelayed(()->tick(run),100);return;}if(SystemClock.elapsedRealtime()-stageAt>(stage.equals("consent")?60_000:25_000)||SystemClock.elapsedRealtime()-totalAt>180_000){if(regionBatch!=null)skipCurrent(stageLabel(stage)+" 응답 시간 초과 · 미확인");else cancel(stageLabel(stage)+"에서 응답을 확인하지 못했습니다. 기존 결과를 유지합니다.");return;}
  if(!collectionUrl(web.getUrl())){handler.postDelayed(()->tick(run),250);return;}
  final String expectedStage=stage;final long document=documentEpoch;JSONObject scriptQuery=query;String js=script+"("+JSONObject.quote(stage)+","+scriptQuery+")";web.evaluateJavascript(js,raw->{if(!running||!currentBrowser(run,expected)||!expectedStage.equals(stage))return;if(document!=documentEpoch){handler.postDelayed(()->tick(run),300);return;}try{JSONObject r=new JSONObject(raw);if(r.optBoolean("cold")){if(!"reuse".equals(stage)||searchSubmitted||coldFallback){cancel("월 이동 확인을 하지 못해 조회를 중단했습니다. 기존 자료는 유지합니다.");return;}coldFallback=true;clearPageReuse();stage="consent";stageAt=SystemClock.elapsedRealtime();web.loadUrl(SOURCE,java.util.Map.of("Accept-Language","ko-KR,ko;q=0.9"));handler.postDelayed(()->tick(run),800);return;}if(r.optBoolean("submitted"))searchSubmitted=true;if(r.has("detail"))lastWait=r.optString("detail");if(diagnostic!=null&&r.has("detail"))diagnostic.detail(r.optString("detail"));if(r.has("error")){clearPageReuse();String message=r.getString("error");if("UNSUPPORTED_ITINERARY".equals(r.optString("code"))&&regionBatch!=null&&query!=null&&collectionUrl(web.getUrl())&&java.util.Set.of("reuse","result","filter","parse").contains(stage))skipCurrent("UNSUPPORTED_ITINERARY",message);else cancel(message);return;}if(r.has("data")){JSONObject snapshot=r.getJSONObject("data");reuseDocument=r.optBoolean("reuseReady",false);lastSuccessfulQuery=reuseDocument?new JSONObject(query.toString()):null;saveCollectedResult(snapshot,run,expected);return;}if(r.has("next")){String nextStage=r.getString("next");if(("search".equals(stage)||"reuse".equals(stage))&&"result".equals(nextStage))searchSubmitted=true;if("parse_test".equals(nextStage)&&!legacyAwardFixture()){cancel("검증 전용 조회 단계는 일반 수집에서 사용할 수 없습니다.");return;}if(!stage.equals(nextStage))stageAt=SystemClock.elapsedRealtime();stage=nextStage;if(diagnostic!=null)diagnostic.step(stage);updateProgress(false);setStatus((index+1)+"/"+queue.size()+" · "+Search.key(query)+"\n"+stageLabel(stage));if(r.has("type"))typeCode(r.getString("type"));if(r.has("tap"))tap(r.getJSONObject("tap"));}handler.postDelayed(()->tick(run),300);}catch(Exception e){if(diagnostic!=null)diagnostic.exception(e);cancel("조회 결과를 확인하지 못했습니다: "+e.getMessage());}});
 }
 void clearPageReuse(){lastSuccessfulQuery=null;reuseDocument=false;}
 void saveCollectedResult(JSONObject snapshot,int run,WebView expected)throws Exception{
  if(!running||!currentBrowser(run,expected))return;
  boolean recovered=db.error(Search.key(query))!=null;if(shared==null)db.save(query,snapshot);else db.saveForShare(query,snapshot,batchKey);
  nextQueryAt=SystemClock.elapsedRealtime()+BETWEEN_QUERIES_MS;success++;index++;saveCursor();running=false;stage="consent";updateProgress(false);setStatus(index<queue.size()?"결과 저장 완료 · 다음 조회까지 1.5초 대기 중":"모든 결과 저장 완료");
  if(diagnostic!=null)diagnostic.step("publish_shared");publishShared(db.data(Search.key(query)),()->{if(diagnostic!=null)diagnostic.success(recovered);if(fixture==null)QueryEstimate.record(this,SystemClock.elapsedRealtime()-totalAt+nextQueryDelay(index<queue.size()));scheduleNext(nextQueryDelay(index<queue.size()));});
 }
 int doneCount(){return allDone+priorCompleted+index;}
 int totalCount(){return allKeys!=null?allTotal:priorCompleted+queue.size();}
 void updateProgress(boolean stopped){int total=totalCount(),done=doneCount();estimateMessage=done>=total?"남은 조회 0개":QueryEstimate.estimate(this,total-done);int pct=total==0?0:Math.min(100,done*100/total);boolean complete=total>0&&done>=total;progress.setProgress(pct);progressTitle.setText((stopped?"조회 중단":complete?"조회 완료":"조회 중")+" · "+pct+"%");progressInfo.setText((allKeys!=null?"전체 · "+(regionIndex+1)+"/"+allKeys.size()+" 지역 · ":"")+"처리 "+done+" / "+total+"개 노선·월 · 미확인 "+(allFailures+failures));progress.setContentDescription("조회 진행률 "+pct+"퍼센트");}

 static String stageLabel(String step){if(step.equals("reuse"))return "같은 노선 다음 달 조회";if(step.equals("consent"))return "대한항공 첫 화면 로딩";if(step.startsWith("origin"))return "출발 공항 선택";if(step.startsWith("dest"))return "도착 공항 선택";if(step.startsWith("month"))return "탑승월 선택";if(step.equals("filter"))return "좌석 등급 확인";if(step.equals("parse"))return "달력 정보 읽기";return "보너스석 조회";}
 void tap(JSONObject point)throws Exception{
  final int run=token;final WebView expected=web;
  runQuerySafely(run,expected,"선택 위치 확인 오류",()->{
   final double cssX=point.getDouble("x"),cssY=point.getDouble("y");
   expected.evaluateJavascript("JSON.stringify({width:visualViewport?.width||innerWidth,left:visualViewport?.offsetLeft||0,top:visualViewport?.offsetTop||0})",raw->runQuerySafely(run,expected,"선택 위치 확인 오류",()->{
    JSONObject viewport=new JSONObject(new JSONArray("["+raw+"]").getString(0));double width=viewport.getDouble("width");float scale=expected.getWidth()/(float)width,x=(float)(cssX-viewport.getDouble("left"))*scale,y=(float)(cssY-viewport.getDouble("top"))*scale;
    if(!Float.isFinite(x)||!Float.isFinite(y)||x<0||y<0||x>=expected.getWidth()||y>=expected.getHeight())throw new Exception("공항 항목 위치 오류");
    handler.postDelayed(()->runQuerySafely(run,expected,"공항 선택 입력 오류",()->{
     long time=SystemClock.uptimeMillis();MotionEvent down=MotionEvent.obtain(time,time,MotionEvent.ACTION_DOWN,x,y,0);try{down.setSource(InputDevice.SOURCE_TOUCHSCREEN);expected.dispatchTouchEvent(down);}finally{down.recycle();}
     handler.postDelayed(()->runQuerySafely(run,expected,"공항 선택 입력 오류",()->{MotionEvent up=MotionEvent.obtain(time,SystemClock.uptimeMillis(),MotionEvent.ACTION_UP,x,y,0);try{up.setSource(InputDevice.SOURCE_TOUCHSCREEN);expected.dispatchTouchEvent(up);}finally{up.recycle();}}),60);
    }),120);
   }));
  });
 }
 void typeCode(String code){
  final int run=token;final WebView expected=web;
  runQuerySafely(run,expected,"공항 코드 입력 오류",()->{
   expected.requestFocus();KeyCharacterMap map=KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD);KeyEvent[] events=map.getEvents(code.toCharArray());if(events==null)throw new IllegalStateException("공항 코드 키 입력을 지원하지 않습니다.");typing=true;
   for(int i=0;i<events.length;i++){final KeyEvent event=events[i];handler.postDelayed(()->runQuerySafely(run,expected,"공항 코드 입력 오류",()->expected.dispatchKeyEvent(KeyEvent.changeTimeRepeat(event,SystemClock.uptimeMillis(),0))),i*90L);}
   handler.postDelayed(()->runQuerySafely(run,expected,"공항 코드 입력 오류",()->{typing=false;stageAt=SystemClock.elapsedRealtime();}),events.length*90L+450);
  });
 }

 void saveCursor()throws Exception{if(regionBatch!=null){regionBatch.put("cursor",priorCompleted+index).put("failures",failures).put("error","");if(priorCompleted+index==regionBatch.getJSONArray("queries").length())regionBatch.put("completedAt",System.currentTimeMillis());db.batch(regionBatch);}}
 void skipCurrent(String message){skipCurrent(DiagnosticEvent.code(message),message);}
 void skipCurrent(String code,String message){clearPageReuse();if(diagnostic!=null)diagnostic.failure(code,message,true);try{db.error(query,message);failures++;index++;saveCursor();running=false;token++;updateProgress(false);setStatus(message+" · 기존 결과 유지 · 다음 항목까지 1.5초 대기");nextQueryAt=SystemClock.elapsedRealtime()+BETWEEN_QUERIES_MS;scheduleNext(BETWEEN_QUERIES_MS);}catch(Exception e){if(diagnostic!=null)diagnostic.exception(e);cancel("수집 상태를 저장하지 못했습니다.");}}
 void cancel(String message){
  if(finished||closed)return;message=failureMessage(message);boolean interruptedQuery=running;running=false;paused=true;typing=false;token++;lastError=message;handler.removeCallbacksAndMessages(null);
  try{if(diagnostic!=null)diagnostic.stop(message);}catch(RuntimeException error){recordException(error);}
  try{if(regionBatch!=null){regionBatch.put("error",message);db.batch(regionBatch);}}catch(Exception error){recordException(error);lastError=message+"\n중단 상태를 저장하지 못했습니다. 기존 결과는 유지됩니다.";}
  try{if(query!=null&&interruptedQuery)db.error(query,message);}catch(RuntimeException error){recordException(error);lastError=message+"\n중단 상태를 저장하지 못했습니다. 기존 결과는 유지됩니다.";}
  String stopped=lastError+"\n완료한 항목은 유지되며 다음 실행에서 이어집니다.";
  try{if(!queue.isEmpty())updateProgress(true);setStatus(stopped);if(BuildConfig.DEBUG)probeRecord();end(stopped,false);}catch(RuntimeException error){emergencyStop(stopped,error);}
 }

 void probeRecord(){try{JSONObject r=new JSONObject().put("success",success).put("attemptedQueries",index).put("stage",stage).put("progressPercent",progress.getProgress()).put("error",lastError).put("backgroundService",true).put("time",java.time.Instant.now().toString());try(java.io.FileOutputStream f=openFileOutput("live-probe.json",MODE_PRIVATE)){f.write(r.toString(2).getBytes(StandardCharsets.UTF_8));}}catch(Exception ignored){}}
 void setStatus(String message){statusMessage=message;renderStatus();}
 void renderStatus(){status.setText(statusMessage+(sharedPending>0?"\n휴대폰 저장 완료 · GitHub 공유 대기 "+sharedPending+"개":"")+(diagnostic==null||diagnostic.warning.isEmpty()?"":"\n"+diagnostic.warning));publish(!lastError.isEmpty());}
 void notifyProgress(Notification n){if(checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)==android.content.pm.PackageManager.PERMISSION_GRANTED)getSystemService(NotificationManager.class).notify(NOTICE,n);}
 Notification notice(String message,int pct,boolean ongoing){PendingIntent open=PendingIntent.getActivity(this,910,new Intent(this,CollectActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);Notification.Builder b=new Notification.Builder(this,CHANNEL).setSmallIcon(R.drawable.ic_sky).setContentTitle(ongoing?"통SKY 수집 중 · "+pct+"%":"통SKY · 수집 종료").setContentText(message).setContentIntent(open).setOnlyAlertOnce(true).setOngoing(ongoing).setCategory(Notification.CATEGORY_PROGRESS);if(ongoing){PendingIntent stop=PendingIntent.getService(this,911,new Intent(this,CollectionService.class).setAction(STOP),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);b.setProgress(100,pct,false).addAction(new Notification.Action.Builder(null,"수집 중단",stop).build());}return b.build();}
 void publish(boolean stopped){try{lastState=new JSONObject().put("appVersion",BuildConfig.VERSION_NAME).put("running",!stopped&&!finished).put("batchKey",batchKey).put("title",progressTitle.getText()).put("info",progressInfo.getText()).put("message",status.getText()).put("decision",decisionMessage).put("estimate",estimateMessage).put("sharedPending",sharedPending).put("percent",progress.getProgress()).put("done",doneCount()).put("total",totalCount());String encoded=lastState.toString();if(!encoded.equals(publishedState)){getSharedPreferences("collection",0).edit().putString("state",encoded).apply();publishedState=encoded;}if(SystemClock.elapsedRealtime()-lastNotification>1500||stopped){lastNotification=SystemClock.elapsedRealtime();notifyProgress(notice(progressInfo.getText()+" · "+stageLabel(stage),progress.getProgress(),!stopped&&!finished));}}catch(Exception ignored){}}
 void end(String message,boolean complete){if(finished||closed||ending)return;try{endOwned(failureMessage(message),complete);}catch(RuntimeException error){emergencyStop(message,error);}}
 void endOwned(String message,boolean complete){if(finished||closed||ending)return;if(shared==null){finishEnd(message,complete);return;}ending=true;running=false;if(sharedBatcher!=null)sharedBatcher.stop();handler.removeCallbacksAndMessages(null);setStatus("GitHub 저장 마무리 중");sharedWorker.execute(()->{String result=message;boolean ok=complete,checkpointFailed=false;try{SharedWorkCoordinator.run(this::flushSharedAndRelease);}catch(Exception e){result=message+"\nGitHub 마무리 저장 실패 · 완료한 휴대폰 자료는 유지합니다.";ok=false;checkpointFailed=true;}final String text=result;final boolean done=ok,shareFailed=checkpointFailed;handler.post(()->{if(shareFailed&&diagnostic!=null)diagnostic.failure("SHARED_CHECKPOINT_FAILED",text,false);finishEnd(text,done);});});}
 void finishEnd(String message,boolean complete){if(finished||closed)return;try{finishEndOwned(failureMessage(message),complete);}catch(RuntimeException error){emergencyStop(message,error);}}
 void finishEndOwned(String message,boolean complete){
  if(finished||closed)return;
  // A stop can arrive while the final shared checkpoint is still in flight.
  if(!lastError.isEmpty()){message=lastError+"\n완료한 항목은 유지되며 다음 실행에서 이어집니다.";complete=false;}
  if(complete&&allKeys!=null&&regionIndex+1<allKeys.size()){
   if(SystemClock.elapsedRealtime()-sessionStarted>=MAX_SESSION_MS){
    // The checkpoint has already released this region. Finish directly instead
    // of re-entering end() while ending is true or releasing the lease again.
    lastError="안드로이드 실행 제한에 대비해 중단했습니다. 다시 검색하면 저장 자료를 재사용합니다.";
    message=lastError;complete=false;updateProgress(true);
   }else{
    allDone+=priorCompleted+index;allFailures+=failures;batchKey=allKeys.get(++regionIndex);if(sharedBatcher!=null)sharedBatcher.stop();sharedBatcher=null;sharedPending=0;regionReleased=false;sharedFailure=null;shared=null;ending=false;queue.clear();query=null;regionBatch=null;priorCompleted=index=success=failures=0;running=false;token++;lastError="";updateProgress(false);setStatus("지역 완료 · 다음 지역까지 1.5초 대기");
    handler.postDelayed(()->cancel("안드로이드 실행 제한 · 저장 후 중단"),Math.max(1,MAX_SESSION_MS-(SystemClock.elapsedRealtime()-sessionStarted)));handler.postDelayed(this::prepareShared,BETWEEN_QUERIES_MS);return;
   }
  }
  if(diagnostic!=null){if(!complete&&!diagnostic.stopRecorded)diagnostic.stop(message);diagnostic.finish(complete,message);}finished=true;running=false;handler.removeCallbacksAndMessages(null);setStatus(message);
  try{if(lastState==null)lastState=new JSONObject().put("percent",progress.getProgress());lastState.put("running",false).put("message",message).put("title",complete?"조회 완료 · "+progress.getProgress()+"%":"수집 중단 · "+progress.getProgress()+"%");getSharedPreferences("collection",0).edit().putString("state",lastState.toString()).apply();}catch(Exception ignored){}
  stopForegroundNow();try{notifyProgress(notice(message,progress.getProgress(),false));}catch(RuntimeException error){recordException(error);}finally{stopSelfSafely();}
 }

 @Override public void onTimeout(int startId,int foregroundServiceType){
  // Android requires an immediate platform stop; a shared checkpoint may be slow.
  stopPlatformService();String message="안드로이드의 백그라운드 실행 시간이 끝났습니다. 저장 후 중단했으며 다음 실행에서 이어집니다.";
  try{cancel(message);}finally{if(!finished&&!closed)finishEnd(message,false);}
 }
 @Override public void onTaskRemoved(Intent rootIntent){super.onTaskRemoved(rootIntent);if(!isFinishing())publish(false);}
 @Override public IBinder onBind(Intent intent){return null;}
 @Override public void onDestroy(){
  if(closed)return;
  closed=true;running=false;paused=true;typing=false;token++;if(sharedBatcher!=null)sharedBatcher.stop();handler.removeCallbacksAndMessages(null);
  if(!finished){if(diagnostic!=null)diagnostic.failure("PROCESS_DESTROYED","시스템이 수집 서비스를 종료했습니다.",false);lastError="시스템이 수집 프로세스를 종료했습니다. 저장된 진행 위치에서 복구를 기다립니다.";try{if(lastState!=null){lastState.put("running",false).put("message",lastError);getSharedPreferences("collection",0).edit().putString("state",lastState.toString()).apply();}}catch(Exception ignored){}}
  if(web!=null){WebView old=web;web=null;try{old.stopLoading();}catch(RuntimeException ignored){}try{if(old.getParent() instanceof android.view.ViewGroup parent)parent.removeView(old);}catch(RuntimeException ignored){}try{old.destroy();}catch(RuntimeException ignored){}}
  if(presentation!=null){Presentation old=presentation;presentation=null;try{old.dismiss();}catch(RuntimeException ignored){}}
  if(display!=null){android.hardware.display.VirtualDisplay old=display;display=null;try{old.release();}catch(RuntimeException ignored){}}
  if(images!=null){android.media.ImageReader old=images;images=null;try{old.close();}catch(RuntimeException ignored){}}
  if(wake!=null){PowerManager.WakeLock old=wake;wake=null;try{if(old.isHeld())old.release();}catch(RuntimeException ignored){}}
  Runnable cleanup=()->{try{LocalScannerLease.release(this);}catch(Exception ignored){}try{if(db!=null)db.close();}catch(RuntimeException ignored){}try{if(diagnostic!=null)diagnostic.close();}catch(RuntimeException ignored){}};
  try{sharedWorker.execute(cleanup);}catch(java.util.concurrent.RejectedExecutionException ignored){
   // A pre-closed executor must not turn service teardown into a process crash. Wait off the UI thread before closing its databases.
   Thread recovery=new Thread(()->{try{while(!sharedWorker.awaitTermination(30,java.util.concurrent.TimeUnit.SECONDS)){}cleanup.run();}catch(InterruptedException interrupted){Thread.currentThread().interrupt();}},"TongSKY-cleanup");recovery.setDaemon(true);recovery.start();
  }finally{sharedWorker.shutdown();if(active==this)active=null;super.onDestroy();}
 }
}

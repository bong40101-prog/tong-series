package kr.tong.sky;

import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.net.Uri;
import android.net.http.SslError;
import android.os.*;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

/** Foreground official-site navigation, automatic correlated search capture and device-only history. */
public class AirFranceActivity extends Activity {
 static final String HOME="https://wwws.airfrance.co.kr/";
 static final int EXPORT=1293;
 final Handler handler=new Handler(Looper.getMainLooper());
 final ExecutorService worker=Executors.newSingleThreadExecutor();
 LinearLayout root,saved,days;FrameLayout browserBox;TextView status,monthLabel;Spinner routeFilter,cabinFilter;
 WebView browser,popup;Dialog popupDialog;AirFranceDb db;String captureScript="",autoScript="",visibleScript="";
 SharedPreferences autoPrefs;AirFranceAutoPlan autoPlan;AirFranceAutoState autoState;TextView planSummary;
 boolean automaticReady,automaticBusy,automaticSaving;int automaticGeneration;long automaticOperation,automaticStartedAt,automaticPausedAt,nextAutomaticAt,loginLandingAt;
 String collectionRun="",emptyQuietKey="";int collectionIndex=-1,collectionPage=-1;WebView collectionBrowser;long emptyQuietSince;
 String collectionMode="",witnessRun="",visibleQuietFingerprint="";int witnessIndex=-1,witnessPage=-1;WebView witnessBrowser;JSONObject visibleWitness;long visibleQuietSince;boolean networkValidated;
 final java.util.IdentityHashMap<WebView,AirFranceDocumentStart.Handle> earlyScripts=new java.util.IdentityHashMap<>();
 final Runnable automaticTick=this::tickAutomatic;static final java.util.concurrent.atomic.AtomicInteger AUTOMATIC_WRITES=new java.util.concurrent.atomic.AtomicInteger();
 boolean resumed,closed,showingSaved,captureAllowed=true,loadingFilters;int pageGeneration,recordsGeneration;
 String preferredRoute="";final String eventSession=UUID.randomUUID().toString();
 YearMonth month=YearMonth.now(ZoneId.of("Asia/Seoul"));JSONArray routes=new JSONArray(),rows=new JSONArray();
 static boolean under(String host,String domain){return host.equals(domain)||host.endsWith("."+domain);}
 static Uri secureUri(String value){try{Uri u=Uri.parse(value);return "https".equalsIgnoreCase(u.getScheme())&&u.getHost()!=null&&u.getUserInfo()==null&&(u.getPort()==-1||u.getPort()==443)?u:null;}catch(Exception e){return null;}}
 static boolean rewardOrigin(String value){Uri u=secureUri(value);if(u==null)return false;String h=u.getHost().toLowerCase(Locale.ROOT);return Set.of("airfrance.co.kr","www.airfrance.co.kr","wwws.airfrance.co.kr","airfrance.fr","www.airfrance.fr","wwws.airfrance.fr","airfrance.com","www.airfrance.com","wwws.airfrance.com").contains(h);}
 static boolean rewardPage(String value){if(!rewardOrigin(value))return false;Uri uri=secureUri(value);String path=uri.getPath()==null?"":uri.getPath().toLowerCase(Locale.ROOT);if(path.matches(".*(?:^|/)(?:auth|login|account|profile|checkout|payment)(?:/|$).*"))return false;return path.matches("(?:/[a-z]{2})?/(?:search(?:/.*)?|booking/(?:offers|flights)(?:/.*)?)");}
 static boolean capturePage(String value){if(!rewardOrigin(value))return false;String path=secureUri(value).getPath();return path==null||path.isEmpty()||path.equals("/")||path.matches("/[a-zA-Z]{2}/?")||rewardPage(value);}
 static boolean officialNavigation(String value){Uri u=secureUri(value);if(u==null)return false;String h=u.getHost().toLowerCase(Locale.ROOT);return under(h,"airfrance.co.kr")||under(h,"airfrance.fr")||under(h,"airfrance.com")||under(h,"airfranceklm.com")||under(h,"flyingblue.com");}
 static boolean authenticationPage(String value){if(!officialNavigation(value))return false;Uri uri=secureUri(value);String host=uri.getHost().toLowerCase(Locale.ROOT),path=uri.getPath()==null?"":uri.getPath().toLowerCase(Locale.ROOT);return host.startsWith("login.")||host.startsWith("identity.")||path.matches(".*(?:^|/)(?:auth|login|identity|authentication|authorize|oauth|sso)(?:/|$).*");}
 static boolean loginLandingPage(String value){if(!rewardOrigin(value))return false;String path=secureUri(value).getPath();return path==null||path.isEmpty()||path.equals("/")||path.matches("/[a-zA-Z]{2}/?")||path.matches("(?:/[a-zA-Z]{2})?/search/landing/?");}
 void noteAuthenticationNavigation(String url){if(autoState==null||autoState.terminal()||!authenticationPage(url))return;try{if(!autoState.loginObserved){autoState.loginObserved=true;autoState.persist(autoPrefs);}}catch(Exception ignored){}}
 @Override public void onCreate(Bundle state){super.onCreate(state);SkyLocale.apply(this);setTitle("에어프랑스 (테스트중)");buildUi();try{
  db=new AirFranceDb(getApplicationContext());try(InputStream in=getAssets().open("af-capture.js")){captureScript=new String(in.readAllBytes(),StandardCharsets.UTF_8);}try(InputStream in=getAssets().open("af-auto.js")){autoScript=new String(in.readAllBytes(),StandardCharsets.UTF_8);}try(InputStream in=getAssets().open("af-visible.js")){visibleScript=new String(in.readAllBytes(),StandardCharsets.UTF_8);}
  autoPrefs=getSharedPreferences(AirFranceAutoPlan.PREFS,MODE_PRIVATE);loadAutomaticPreferences();automaticReady=true;planSummary.setText(autoPlan.summary());
 }catch(Exception e){say("자동검색 연결을 준비하지 못했습니다. 기존 저장 결과는 유지됩니다.");}}
 void buildUi(){
  root=Ui.root(this);TextView title=Ui.text(this,"에어프랑스 (테스트중)",20);root.addView(title);
  TextView guide=Ui.text(this,"로그인만 해 주세요. 이후 노선·날짜 검색과 결과 저장은 앱이 자동으로 진행합니다. 자동검색 중에는 이 화면을 유지해 주세요.",12);root.addView(guide);
  planSummary=Ui.text(this,"자동검색 조건을 불러오는 중",11);root.addView(planSummary);
  LinearLayout actions=new LinearLayout(this);actions.addView(Ui.button(this,"자동검색",v->startAutomatic()),new LinearLayout.LayoutParams(0,-2,1));actions.addView(Ui.button(this,"중단",v->stopAutomatic("USER_STOP")),new LinearLayout.LayoutParams(0,-2,1));actions.addView(Ui.button(this,"검색 조건",v->editAutomaticPlan()),new LinearLayout.LayoutParams(0,-2,1));actions.addView(Ui.button(this,"저장 결과",v->showSaved()),new LinearLayout.LayoutParams(0,-2,1));root.addView(actions);
  status=Ui.text(this,"로그인 화면이 나오면 로그인해 주세요. 이미 로그인되어 있으면 자동검색을 이어갑니다.",12);root.addView(status);
  browserBox=new FrameLayout(this);TextView start=Ui.text(this,"공식 로그인·자동검색 화면을 여는 중입니다.",16);start.setGravity(Gravity.CENTER);browserBox.addView(start,new FrameLayout.LayoutParams(-1,-1));root.addView(browserBox,new LinearLayout.LayoutParams(-1,0,1));
  saved=new LinearLayout(this);saved.setOrientation(LinearLayout.VERTICAL);saved.setVisibility(View.GONE);root.addView(saved,new LinearLayout.LayoutParams(-1,0,1));
  LinearLayout bottom=new LinearLayout(this);bottom.addView(Ui.button(this,"이전 화면",v->goBack()),new LinearLayout.LayoutParams(0,-2,1));bottom.addView(Ui.button(this,"통SKY로 돌아가기",v->finish()),new LinearLayout.LayoutParams(0,-2,1));root.addView(bottom);setContentView(root);
 }
 void say(String value){if(!closed&&status!=null)status.setText(value);}
 boolean submit(Runnable task){if(closed)return false;try{worker.execute(task);return true;}catch(RejectedExecutionException e){return false;}}
 void configure(WebView view){WebSettings s=view.getSettings();s.setJavaScriptEnabled(true);s.setDomStorageEnabled(true);s.setAllowFileAccess(false);s.setAllowContentAccess(false);s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);s.setSafeBrowsingEnabled(true);s.setSupportMultipleWindows(true);s.setJavaScriptCanOpenWindowsAutomatically(false);CookieManager.getInstance().setAcceptThirdPartyCookies(view,true);view.setWebViewClient(new OfficialClient());view.setWebChromeClient(new OfficialChrome());}
 boolean airFranceProfileReady(){try{return WebViewProcessPolicy.isReadyForAirFrance(this);}catch(RuntimeException e){return false;}}
 boolean ensureBrowser(){
  if(!airFranceProfileReady()){pauseAutomaticFailure("WEBVIEW_PROFILE_UNAVAILABLE",true,"WEBVIEW_PROFILE_UNAVAILABLE");say("에어프랑스 로그인 화면을 준비하지 못했습니다. 앱을 다시 열어 주세요. 저장된 조회 결과는 유지됩니다.");return false;}
  showingSaved=false;saved.setVisibility(View.GONE);browserBox.setVisibility(View.VISIBLE);
  if(browser!=null)return true;
  try{WebView created=new WebView(this);try{configure(created);browser=created;browserBox.removeAllViews();browserBox.addView(created,new FrameLayout.LayoutParams(-1,-1));return true;}catch(RuntimeException e){if(browser==created)browser=null;dispose(created);throw e;}}
  catch(RuntimeException e){say("공식 화면을 열 수 없습니다. Android System WebView를 확인해 주세요.");return false;}
 }
 void openOfficial(){startAutomatic();}
  WebView activeBrowser(){return popup!=null?popup:browser;}
 boolean owned(WebView view){return view!=null&&(view==browser||view==popup)&&!closed;}
 static String safeUrl(WebView view){try{return view==null?"":view.getUrl();}catch(RuntimeException e){return "";}}
 void setCapture(WebView view,boolean enabled){try{if(view!=null&&capturePage(safeUrl(view)))view.evaluateJavascript("window.__tongAF&&window.__tongAF.setEnabled("+enabled+")",null);}catch(RuntimeException ignored){}}
 void install(WebView view){if(closed||!resumed||!captureAllowed||view==null||!capturePage(safeUrl(view))||captureScript.isEmpty())return;try{view.evaluateJavascript(captureScript,null);setCapture(view,true);}catch(RuntimeException e){stopCapture("정보 연결이 중단되었습니다. 저장된 결과는 유지됩니다.");}}
 void recordFailure(String code){Context context=getApplicationContext();submit(()->{try{AirFranceDiagnostics.failure(context,eventSession,code);}catch(Exception e){handler.post(()->{if(!closed)status.append("\n오류 기록을 저장하지 못했습니다.");});}});}
 void recordAutomaticEvent(String event,int count){
  try{if(autoState!=null&&autoPlan!=null)recordAutomaticObservation(event,count,autoPlan.task(Math.min(autoState.index,autoPlan.size()-1)),autoState.runId,autoState.index);}catch(Exception ignored){}
 }
 void recordAutomaticObservation(String event,int count,JSONObject task,String run,int index){
  try{JSONObject snapshot=new JSONObject(task.toString());Context context=getApplicationContext();submit(()->{try{AirFranceDiagnostics.autoObservation(context,eventSession,event,snapshot,run,index,count);}catch(Exception e){handler.post(()->{if(!closed)status.append("\n자동검색 기록을 저장하지 못했습니다.");});}});}catch(Exception ignored){}
 }
 void recordAutomaticFailure(String reason,String sourceCode){
  try{if(autoState==null||autoPlan==null)return;JSONObject task=new JSONObject(autoPlan.task(Math.min(autoState.index,autoPlan.size()-1)).toString());String run=autoState.runId;int index=autoState.index;Context context=getApplicationContext();submit(()->{try{AirFranceDiagnostics.autoFailure(context,eventSession,reason,task,run,index,sourceCode);}catch(Exception e){handler.post(()->{if(!closed)status.append("\n자동검색 오류 기록을 저장하지 못했습니다.");});}});}catch(Exception ignored){}
 }
 void stopCapture(String value){stopCapture(value,"");}
 void stopCapture(String value,String sourceCode){boolean automatic=autoState!=null&&!autoState.terminal();pauseAutomaticFailure(value.contains("접근을 제한")?"ACCESS_RESTRICTED":"WEB_ERROR",automatic,sourceCode);boolean wasAllowed=captureAllowed;captureAllowed=false;if(wasAllowed&&!automatic)recordFailure(value.contains("접근을 제한")?"AF_ACCESS_RESTRICTED":"AF_WEB_ERROR");setCapture(browser,false);setCapture(popup,false);say(value);}
 void importCurrent(){
  if(!resumed||closed)return;WebView current=activeBrowser();if(db==null){say("저장 공간을 열지 못했습니다. 앱을 다시 열어 주세요.");return;}
  if(!captureAllowed){say("현재 공식 화면에서는 가져오기가 중단되었습니다. 기존 저장 결과는 유지됩니다.");return;}
  if(current==null||!capturePage(safeUrl(current))){say("공식 사이트에서 로그인하고 마일리지 항공권 검색 결과로 이동해 주세요.");return;}
  int generation=pageGeneration;say("현재 마일리지 검색 정보를 확인하고 있습니다.");
  try{current.evaluateJavascript("JSON.stringify(window.__tongAF?window.__tongAF.drain():{schema:1,status:'not-installed',records:[]})",raw->{if(closed||!resumed||activeBrowser()!=current||pageGeneration!=generation)return;acceptPacket(raw);});}catch(RuntimeException e){stopCapture("공식 화면과의 연결이 중단되었습니다. 저장된 결과는 유지됩니다.");}
 }
 void acceptPacket(String raw){
  AirFranceDb store=db;if(closed||!resumed||store==null)return;WebView source=activeBrowser();int sourcePage=pageGeneration;
  try{if(raw==null||raw.length()>1048576)throw new Exception();Object decoded=new JSONTokener(raw).nextValue();JSONObject packet=decoded instanceof String?new JSONObject((String)decoded):decoded instanceof JSONObject?(JSONObject)decoded:null;if(packet==null||packet.optInt("schema")!=1)throw new Exception();String state=packet.optString("status");
   if("access_restricted".equals(state)){stopCapture("공식 사이트에서 접근을 제한했습니다. 가져오기를 중단하고 기존 저장 결과를 유지합니다.");return;}
   if("login_required".equals(state)){recordFailure("AF_LOGIN_REQUIRED");say("공식 화면에서 다시 로그인한 뒤 마일리지 항공권을 검색해 주세요.");return;}
   JSONArray records=packet.optJSONArray("records");if(records==null||records.length()==0){recordFailure("AF_NO_RESULTS");say("unsupported_response".equals(state)?"이 검색 화면의 자료 형식은 아직 가져올 수 없습니다. 좌석 없음으로 저장하지 않습니다.":"가져올 마일리지 검색 결과가 없습니다. 결과 화면이 먼저 열렸다면 공식 화면에서 다시 검색한 뒤 눌러 주세요.");return;}
   if(records.length()>30)throw new Exception();JSONArray copy=new JSONArray(records.toString());submit(()->{int changed=0,accepted=0,rejected=0,storageErrors=0;JSONArray ack=new JSONArray();JSONObject lastSaved=null;for(int i=0;i<copy.length();i++){boolean validated=false;try{JSONObject envelope=copy.getJSONObject(i);AirFranceData.validate(envelope,System.currentTimeMillis());validated=true;changed+=store.save(envelope);ack.put(envelope.getString("captureId"));lastSaved=envelope;accepted++;}catch(Exception e){rejected++;if(validated)storageErrors++;}}final int count=changed,ok=accepted,failed=rejected,storageFailed=storageErrors;JSONObject imported=lastSaved;handler.post(()->{if(closed)return;if(resumed&&source!=null&&source==activeBrowser()&&sourcePage==pageGeneration&&capturePage(safeUrl(source)))try{source.evaluateJavascript("window.__tongAF&&window.__tongAF.ack("+ack.toString()+")",null);}catch(RuntimeException ignored){}if(imported!=null)try{month=YearMonth.from(LocalDate.parse(imported.getJSONArray("dates").getJSONObject(0).getString("date")));preferredRoute=imported.getString("origin")+" → "+imported.getString("destination");}catch(Exception ignored){}if(storageFailed>0)recordFailure("AF_STORAGE_ERROR");if(ok==0){if(storageFailed==0)recordFailure("AF_RESULT_ERROR");say("현재 검색 정보를 확인하지 못해 저장하지 않았습니다. 기존 저장 결과는 유지됩니다.");}else say((count==0?"이미 저장된 조회 정보입니다.":count+"개 조회 항목을 기기에 저장했습니다.")+(failed>0?" 확인되지 않은 자료는 저장하지 않았습니다.":"")+" 저장 결과에서 조회 시각을 확인하세요.");if(showingSaved)loadRoutes();});});
  }catch(Exception e){recordFailure("AF_RESULT_ERROR");say("현재 검색 정보를 읽지 못했습니다. 기존 저장 결과는 유지됩니다.");}
 }
 void loadAutomaticPreferences()throws Exception{
  JSONObject savedPlan;try{savedPlan=new JSONObject(autoPrefs.getString(AirFranceAutoPlan.PLAN_KEY,""));LocalDate start=LocalDate.parse(savedPlan.getString("start")),end=LocalDate.parse(savedPlan.getString("end"));if(start.isBefore(Search.today())&&!end.isBefore(Search.today()))savedPlan.put("start",Search.today().toString());autoPlan=AirFranceAutoPlan.parse(savedPlan,Search.today());}
  catch(Exception e){autoPlan=AirFranceAutoPlan.defaults(Search.today());}
  autoPrefs.edit().putString(AirFranceAutoPlan.PLAN_KEY,autoPlan.json().toString()).apply();autoState=AirFranceAutoState.read(autoPrefs,autoPlan);restoreAutomaticGap();
  // An old WebView does not survive Activity recreation. Its pending task is retried only
  // after the previous browser was destroyed and any already-received DB write finishes.
  if(!autoState.terminal()){autoState.navigationIssued=false;autoState.mode="ready";autoState.phase="prepare";autoState.persist(autoPrefs);}
 }
 void restoreAutomaticGap(){nextAutomaticAt=SystemClock.elapsedRealtime()+Math.min(10000L,Math.max(0L,autoState.nextEligibleAt-System.currentTimeMillis()));}
 void scheduleAutomatic(long delay){if(closed||!resumed||!automaticReady)return;handler.removeCallbacks(automaticTick);handler.postDelayed(automaticTick,Math.max(0,delay));}
 void wakeCurrentAutomatic(String run,int index){if(!closed&&resumed&&!showingSaved&&automaticReady&&autoState!=null&&!autoState.terminal()&&run.equals(autoState.runId)&&index==autoState.index)scheduleAutomatic(150);}
 void startAutomatic(){
  if(closed||!automaticReady){say("자동검색 준비를 마치지 못했습니다. 기존 저장 결과는 유지됩니다.");return;}
  try{if(autoState==null||autoState.terminal()){autoState=AirFranceAutoState.fresh(autoPlan);autoState.persist(autoPrefs);automaticGeneration++;automaticStartedAt=0;}
   captureAllowed=true;showingSaved=false;saved.setVisibility(View.GONE);browserBox.setVisibility(View.VISIBLE);setDriverForeground(browser,true);setDriverForeground(popup,true);scheduleAutomatic(0);
  }catch(Exception e){say("자동검색 상태를 준비하지 못했습니다. 기존 저장 결과는 유지됩니다.");}
 }
 void stopAutomatic(String code){
  recordAutomaticEvent("STOPPED",autoState==null?0:autoState.checked);
  automaticGeneration++;automaticOperation++;automaticBusy=false;resetResultCollection();handler.removeCallbacks(automaticTick);try{if(autoState!=null)autoState.stop(autoPrefs,code==null?"USER_STOP":code);}catch(Exception ignored){}
  try{if(browser!=null)browser.evaluateJavascript("window.__tongAFAuto&&window.__tongAFAuto.stop("+JSONObject.quote(autoState==null?"":autoState.runId)+")",null);}catch(RuntimeException ignored){}
  say("자동검색을 중단했습니다. 완료한 조회 결과와 과거 기록은 유지됩니다.");
 }
 void pauseAutomaticFailure(String code,boolean report){pauseAutomaticFailure(code,report,"");}
 void pauseAutomaticFailure(String code,boolean report,String sourceCode){
  if(autoState==null||autoState.terminal())return;automaticGeneration++;automaticOperation++;automaticBusy=false;resetResultCollection();handler.removeCallbacks(automaticTick);try{autoState.fail(autoPrefs,code);}catch(Exception ignored){}
  if(report)recordAutomaticFailure(code,sourceCode);
 }
 void resetResultCollection(){collectionRun="";collectionIndex=-1;collectionPage=-1;collectionBrowser=null;collectionMode="";networkValidated=false;emptyQuietSince=0;emptyQuietKey="";visibleWitness=null;witnessRun="";witnessIndex=-1;witnessPage=-1;witnessBrowser=null;resetVisibleQuiet();}
 void resetVisibleQuiet(){visibleQuietSince=0;visibleQuietFingerprint="";}
 boolean collectionApproved(WebView source,String run,int index){return source==collectionBrowser&&run.equals(collectionRun)&&index==collectionIndex&&pageGeneration==collectionPage;}
 boolean witnessApproved(WebView source,String run,int index){return visibleWitness!=null&&source==witnessBrowser&&run.equals(witnessRun)&&index==witnessIndex&&pageGeneration==witnessPage;}
 static boolean freshCalendar(JSONObject value){try{long age=System.currentTimeMillis()-Instant.parse(value.getString("observedAt")).toEpochMilli();return "calendar".equals(value.getString("kind"))&&age>=-5000&&age<=120000;}catch(Exception e){return false;}}
 boolean softWitnessApproved(WebView source,String run,int index){return witnessApproved(source,run,index)&&freshCalendar(visibleWitness)&&"CURRENT_TASK_NORMAL_NAVIGATION".equals(visibleWitness.optString("nativeNavigationEvidence"));}
 boolean checkAutomaticDeadline(){if(automaticStartedAt<=0||SystemClock.elapsedRealtime()-automaticStartedAt<=90000)return false;pauseAutomaticFailure("QUERY_TIMEOUT",true);say("자동검색 응답을 확인하지 못해 중단했습니다. 확인되지 않은 항목을 완료로 세지 않았으며 기존 자료는 유지됩니다.");return true;}
 void setDriverForeground(WebView view,boolean foreground){try{if(view!=null&&capturePage(safeUrl(view)))view.evaluateJavascript("window.__tongAFAuto&&window.__tongAFAuto.setForeground&&window.__tongAFAuto.setForeground("+foreground+")",null);}catch(RuntimeException ignored){}}
 AirFranceDocumentStart.Handle installEarlyScript(WebView view,String script){return AirFranceDocumentStart.install(view,script,Set.of("https://wwws.airfrance.co.kr","https://www.airfrance.co.kr","https://airfrance.co.kr","https://wwws.airfrance.fr","https://www.airfrance.fr","https://airfrance.fr","https://wwws.airfrance.com","https://www.airfrance.com","https://airfrance.com"));}
 void installEarlyForTask(WebView view,JSONObject task,String run)throws Exception{
  AirFranceDocumentStart.Handle previous=earlyScripts.remove(view);if(previous!=null)previous.remove();
  String quotedTask=task.toString(),quotedRun=JSONObject.quote(run);
  String script=captureScript+"\n;"+autoScript+"\n;"+visibleScript+"\n;if(window.__tongAF){window.__tongAF.beginTask("+quotedTask+","+quotedRun+");}if(window.__tongAFAuto){window.__tongAFAuto.markNavigated("+quotedTask+","+quotedRun+");}";
  AirFranceDocumentStart.Handle handle=installEarlyScript(view,script);earlyScripts.put(view,handle);
 }
 void tickAutomatic(){
  if(closed||!resumed||!automaticReady)return;if(automaticSaving){scheduleAutomatic(250);return;}if(automaticBusy){if(!checkAutomaticDeadline())scheduleAutomatic(250);return;}
  try{
   if(AUTOMATIC_WRITES.get()>0){scheduleAutomatic(250);return;}
   String previousRun=autoState==null?"":autoState.runId;int previousIndex=autoState==null?-1:autoState.index;autoState=AirFranceAutoState.read(autoPrefs,autoPlan);if(!autoState.runId.equals(previousRun)||autoState.index!=previousIndex)restoreAutomaticGap();if(autoState.terminal()){if("complete".equals(autoState.mode))say("자동검색 완료 · 확인 "+autoState.checked+"/"+autoPlan.size()+"개 · 저장 "+autoState.savedRows+"개 조회 항목. 과거 이력은 유지됩니다.");return;}
   if(showingSaved){scheduleAutomatic(1000);return;}if(SystemClock.elapsedRealtime()<nextAutomaticAt){scheduleAutomatic(nextAutomaticAt-SystemClock.elapsedRealtime());return;}
   if(!ensureBrowser()){pauseAutomaticFailure("WEB_ERROR",true);return;}
   JSONObject task=autoPlan.task(autoState.index);String run=autoState.runId;
   if(!autoState.navigationIssued){
    try{installEarlyForTask(browser,task,run);}catch(RuntimeException e){pauseAutomaticFailure("DOCUMENT_START_UNAVAILABLE",true);say("현재 Android System WebView에서 자동검색에 필요한 초기 응답 연결을 사용할 수 없습니다. 기존 저장 자료는 유지됩니다.");return;}
    resetResultCollection();if(autoState.index==0&&autoState.checked==0)recordAutomaticEvent("STARTED",autoPlan.size());autoState.navigationIssued=true;autoState.phase(autoPrefs,"navigating","official_search");automaticStartedAt=SystemClock.elapsedRealtime();automaticGeneration++;
    say(automaticProgress(task)+"\n공식 로그인 화면이 나오면 로그인만 해 주세요. 이후 검색과 저장은 자동으로 진행합니다.");
    try{browser.loadUrl(AirFranceAutoPlan.navigationUrl(task,"https://wwws.airfrance.co.kr"));}catch(RuntimeException e){pauseAutomaticFailure("NAVIGATION_ERROR",true);say("공식 자동검색 화면을 열지 못했습니다. 기존 저장 결과는 유지됩니다.");return;}
    scheduleAutomatic(700);return;
   }
   WebView current=activeBrowser();if(current==null){pauseAutomaticFailure("WEB_ERROR",true);return;}
   String url=safeUrl(current);
   if(!capturePage(url)){
    if(officialNavigation(url)){noteAuthenticationNavigation(url);autoState.phase(autoPrefs,"waiting_login","official_login");automaticStartedAt=0;loginLandingAt=0;say(automaticProgress(task)+"\n공식 화면에서 로그인해 주세요. 검색 버튼은 직접 누르지 않아도 됩니다.");scheduleAutomatic(1000);return;}
    // Navigation may be in flight before its first visible document.
    if(url==null||url.isEmpty()||"about:blank".equals(url)){scheduleAutomatic(500);return;}
    pauseAutomaticFailure("NAVIGATION_BLOCKED",true);say("공식 로그인·검색 주소를 확인하지 못해 자동검색을 중단했습니다.");return;
   }
   if(autoState.loginObserved&&!autoState.loginReturnReapplied&&loginLandingPage(url)){
    if(loginLandingAt==0){loginLandingAt=SystemClock.elapsedRealtime();scheduleAutomatic(700);return;}if(SystemClock.elapsedRealtime()-loginLandingAt<2000){scheduleAutomatic(500);return;}
    // The provider sometimes drops the public search context after its own login redirect.
    // Reapply the same normal page link once; this is not proof that login succeeded.
    resetResultCollection();autoState.loginReturnReapplied=true;autoState.phase(autoPrefs,"navigating","restore_search");installEarlyForTask(current,task,run);automaticGeneration++;automaticStartedAt=SystemClock.elapsedRealtime();loginLandingAt=0;
    current.loadUrl(AirFranceAutoPlan.navigationUrl(task,"https://wwws.airfrance.co.kr"));say(automaticProgress(task)+"\n공식 로그인 화면에서 돌아왔습니다. 선택한 검색 조건을 자동으로 다시 연결합니다.");scheduleAutomatic(700);return;
   }
   loginLandingAt=0;
   if(automaticStartedAt==0)automaticStartedAt=SystemClock.elapsedRealtime();
   if(checkAutomaticDeadline())return;
   int generation=automaticGeneration,index=autoState.index,sourcePage=pageGeneration;long operation=++automaticOperation;automaticBusy=true;boolean collect=collectionApproved(current,run,index),softWitness=softWitnessApproved(current,run,index);String mode=collect?collectionMode:"",witness=witnessApproved(current,run,index)?visibleWitness.toString():"null";
   String probe="(function(){var c=window.__tongAF;var p=c?c.drain():null;var r=c&&c.requestState?c.requestState():null;var d=null,s=null,v=null,w="+witness+",m="+JSONObject.quote(mode)+";var soft="+softWitness+"&&m!=='offers'&&p&&p.status==='unsupported_response'&&(p.reasonCode==='SCHEMA_MISMATCH'||p.reasonCode==='ROUND_TRIP_PRICE');var ok=p&&p.status!=='access_restricted'&&(p.status!=='unsupported_response'||soft)&&p.status!=='login_required';var n=ok&&!soft&&p.records&&p.records.some(function(x){return x.kind==='offers';});if(ok&&w&&window.__tongAFVisible)v=window.__tongAFVisible.read("+task.toString()+","+JSONObject.quote(run)+",w);if(ok&&window.__tongAFAuto&&((m==='offers'&&n)||(m==='calendar_dom'&&v&&v.candidate&&v.proof))){var signal=Object.assign({},r,{collectionKind:m,pageGeneration:"+sourcePage+"});if(m==='calendar_dom')signal.domProof=v.proof;s=window.__tongAFAuto.settleResults?window.__tongAFAuto.settleResults("+task.toString()+","+JSONObject.quote(run)+",signal):{state:'unsupported',reason:'RESULT_SIGNAL_INVALID'};p=c.drain();r=c.requestState();if(w&&window.__tongAFVisible)v=window.__tongAFVisible.read("+task.toString()+","+JSONObject.quote(run)+",w);}else if(ok&&!soft&&(!p.records||p.records.length===0)&&p.status!=='empty'&&(!r||!r.started)&&window.__tongAFAuto)d=window.__tongAFAuto.step("+task.toString()+","+JSONObject.quote(run)+");return JSON.stringify({schema:1,packet:p,request:r,driver:d,settle:s,visible:v});})()";
   try{current.evaluateJavascript(probe,raw->{boolean ownsBusy=operation==automaticOperation;if(ownsBusy)automaticBusy=false;if(!ownsBusy||closed||!resumed||showingSaved||generation!=automaticGeneration||sourcePage!=pageGeneration||autoState==null||!run.equals(autoState.runId)||index!=autoState.index||activeBrowser()!=current){wakeCurrentAutomatic(run,index);return;}handleAutomaticProbe(raw,current,task,run,index,generation);});if(operation==automaticOperation&&automaticBusy)scheduleAutomatic(250);}
   catch(RuntimeException e){automaticBusy=false;pauseAutomaticFailure("WEB_ERROR",true);say("자동검색 화면과의 연결이 중단되었습니다. 저장 결과는 유지됩니다.");}
  }catch(Exception e){automaticBusy=false;pauseAutomaticFailure("STATE_ERROR",true);say("자동검색 상태를 확인하지 못해 중단했습니다. 기존 자료는 유지됩니다.");}
 }
 String automaticProgress(JSONObject task){return "자동검색 · "+autoState.checked+"/"+autoPlan.size()+"개 확인 · "+task.optJSONObject("origin").optString("code")+" → "+task.optJSONObject("destination").optString("code")+" · "+task.optString("date");}
 static JSONObject decodedObject(String raw)throws Exception{if(raw==null||raw.length()>1048576)throw new JSONException("invalid bridge packet");Object decoded=new JSONTokener(raw).nextValue();return decoded instanceof String?new JSONObject((String)decoded):decoded instanceof JSONObject?(JSONObject)decoded:null;}
 void handleAutomaticProbe(String raw,WebView source,JSONObject task,String run,int index,int generation){
  if(closed||!resumed||showingSaved||autoState==null||autoState.terminal()||generation!=automaticGeneration||!run.equals(autoState.runId)||index!=autoState.index||source!=activeBrowser())return;
  try{
   JSONObject probe=decodedObject(raw);if(probe==null||probe.optInt("schema")!=1)throw new JSONException("invalid probe");JSONObject packet=probe.optJSONObject("packet"),request=probe.optJSONObject("request"),driver=probe.optJSONObject("driver"),settle=probe.optJSONObject("settle"),visible=probe.optJSONObject("visible");boolean softFormat=softFormatPacket(packet)&&!"offers".equals(collectionMode);
   if(packet!=null){String state=packet.optString("status");if(!"empty".equals(state)||!matchingRequest(request,task,run)||request.getInt("pending")!=0){emptyQuietSince=0;emptyQuietKey="";}
    if("access_restricted".equals(state)){stopCapture("공식 사이트에서 접근을 제한했습니다. 자동검색을 중단하고 기존 결과를 유지합니다.",packet.optString("reasonCode"));return;}
    if("unsupported_response".equals(state)){String reason=packet.optString("reasonCode");if(softFormat&&matchingRequest(request,task,run)&&!softWitnessApproved(source,run,index)){JSONArray calendar=retainedCalendars(packet.optJSONArray("records"));if(calendar.length()>0){approveResultCollection(calendar,source,task,run,index,generation,request,true);return;}}if(!softFormat||!softWitnessApproved(source,run,index)){pauseAutomaticFailure("RESULT_BUFFER_LIMIT".equals(reason)?"RESULT_BUFFER_LIMIT":"UNSUPPORTED_RESULT",true,reason);say("이 검색 결과의 형식을 확인하지 못해 자동검색을 멈췄습니다. 좌석 없음이나 완료로 저장하지 않았습니다.");return;}}
    if("login_required".equals(state)){resetResultCollection();autoState.phase(autoPrefs,"waiting_login","official_login");automaticStartedAt=0;say(automaticProgress(task)+"\n공식 화면에서 다시 로그인하면 자동검색을 이어갑니다.");scheduleAutomatic(1000);return;}
   }
   if(settle!=null){String state=settle.optString("state"),reason=settle.optString("reason");
    if("blocked".equals(state)){stopCapture("공식 사이트에서 접근을 제한했습니다. 자동검색을 중단하고 기존 결과를 유지합니다.",reason);return;}
    if("unsupported".equals(state)){pauseAutomaticFailure("RESULT_SCROLL_LIMIT".equals(reason)?"RESULT_SCROLL_LIMIT":"UNSUPPORTED_RESULT",true,restrictionSource(reason));say("항공편 목록의 끝과 응답 완료를 확인하지 못해 중단했습니다. 기존 결과는 유지하며 완료로 세지 않습니다.");return;}
    if("waiting_login".equals(state)){resetResultCollection();autoState.phase(autoPrefs,"waiting_login","official_login");automaticStartedAt=0;scheduleAutomatic(1000);return;}
    if("stopped".equals(state)){stopAutomatic("USER_STOP");return;}
   }
   if(packet!=null){JSONArray records=packet.optJSONArray("records");JSONObject empty=packet.optJSONObject("emptyProof");if("empty".equals(packet.optString("status"))&&empty!=null&&"offers".equals(empty.optString("kind"))&&onlyCalendarRecords(records)){
     if(!matchesAutomaticTask(empty,task,run)||!matchingRequest(request,task,run)||empty.optInt("generation",-1)!=request.optInt("generation",-2)){pauseAutomaticFailure("RESULT_ERROR",true,"TASK_MISMATCH");return;}if(request.getInt("pending")!=0){emptyQuietSince=0;emptyQuietKey="";scheduleAutomatic(700);return;}
     String key=empty.toString()+"|"+request.getInt("generation");if(!key.equals(emptyQuietKey)){emptyQuietKey=key;emptyQuietSince=SystemClock.elapsedRealtime();scheduleAutomatic(700);return;}if(SystemClock.elapsedRealtime()-emptyQuietSince<1000){scheduleAutomatic(700);return;}persistAutomaticRecords(null,empty,source,task,run,index,generation);return;
    }
    if(records!=null&&records.length()>0){
     emptyQuietSince=0;emptyQuietKey="";
     if(!matchingRequest(request,task,run)){pauseAutomaticFailure("RESULT_ERROR",true,"TASK_MISMATCH");return;}
     boolean offers=!softFormat&&hasNetworkOffers(records);if(offers&&!networkValidated||!offers&&!witnessApproved(source,run,index)){approveResultCollection(softFormat?retainedCalendars(records):records,source,task,run,index,generation,request,softFormat);return;}
     if("calendar_dom".equals(collectionMode)||!offers){handleVisibleResults(visible,settle,request,offers?records:null,source,task,run,index,generation);return;}
     if(collectionApproved(source,run,index)&&settleReady(settle,request,task)){persistAutomaticRecords(records,null,source,task,run,index,generation);return;}
     autoState.phase(autoPrefs,"waiting_result","collect_results");say(automaticProgress(task)+"\n항공편 목록을 끝까지 자동으로 확인하는 중입니다. 추가 결과를 기다린 뒤 저장합니다.");scheduleAutomatic(700);return;
    }
   }
   if(driver!=null){String state=driver.optString("state"),phase=driver.optString("phase","official_search");if(!phase.matches("[a-z_]{1,48}"))phase="official_search";
    if("blocked".equals(state)){stopCapture("공식 사이트에서 접근을 제한했습니다. 자동검색을 중단하고 기존 결과를 유지합니다.",driver.optString("reason"));return;}
    if("unsupported".equals(state)||"failed".equals(state)){pauseAutomaticFailure("UNSUPPORTED_SEARCH_FORM",true);say("공식 검색 조건을 확인하지 못해 자동검색을 멈췄습니다. 확인되지 않은 검색은 완료로 세지 않습니다.");return;}
    if("waiting_consent".equals(state)||"waiting_login".equals(state)&&"site_consent".equals(phase)){autoState.phase(autoPrefs,"waiting_login","privacy_choice");automaticStartedAt=0;say(automaticProgress(task)+"\n공식 화면의 개인정보·쿠키 선택을 직접 결정해 주세요. 이후 검색은 자동으로 진행합니다.");scheduleAutomatic(1000);return;}
    if("waiting_login".equals(state)){autoState.phase(autoPrefs,"waiting_login",phase);automaticStartedAt=0;say(automaticProgress(task)+"\n공식 화면에서 로그인만 해 주세요. 로그인 후 검색은 자동입니다.");scheduleAutomatic(1000);return;}
    if("navigate".equals(state)){// The native controller already issued exactly one ordinary link for this task.
     pauseAutomaticFailure("DUPLICATE_NAVIGATION_BLOCKED",true);say("이미 시작한 검색의 중복 이동을 차단했습니다. 기존 자료는 유지됩니다.");return;
    }
    autoState.phase(autoPrefs,"submitted".equals(state)?"waiting_result":"navigating",phase);
   }else if(request!=null&&request.optBoolean("started"))autoState.phase(autoPrefs,"waiting_result","wait_response");
   say(automaticProgress(task)+"\n"+("waiting_result".equals(autoState.mode)?"공식 검색 결과를 기다리는 중 · 결과가 확인되면 자동 저장합니다.":"공식 검색 조건을 자동으로 선택하고 있습니다."));scheduleAutomatic(700);
  }catch(Exception e){pauseAutomaticFailure("INVALID_AUTOMATIC_PACKET",true);say("자동검색 정보를 확인하지 못해 중단했습니다. 기존 자료는 유지됩니다.");}
 }
 static String restrictionSource(String reason){return "RESULT_TASK_MISMATCH".equals(reason)?"TASK_MISMATCH":"RESULT_SCOPE_MISMATCH".equals(reason)?"ROUTE_MISMATCH":"RESULT_SIGNAL_INVALID".equals(reason)?"SCHEMA_MISMATCH":reason;}
 static boolean matchesVisibleCandidate(JSONObject visible,JSONObject task,String run,int page){try{
  if(visible==null||AirFranceData.exactInt(visible,"schema",1,1)!=1||!Boolean.TRUE.equals(visible.opt("candidate"))||!"candidate".equals(visible.getString("state"))||!task.getString("id").equals(visible.getString("taskId"))||!run.equals(visible.getString("runToken")))return false;
  String fingerprint=visible.getString("fingerprint");if(!fingerprint.matches("afdom-[a-f0-9]{16}"))return false;JSONObject proof=visible.getJSONObject("proof"),from=task.getJSONObject("origin"),to=task.getJSONObject("destination");
  AirFranceData.exactInt(proof,"schema",1,1);AirFranceData.exactInt(proof,"adults",1,1);AirFranceData.exactInt(proof,"cardCount",1,128);if(AirFranceData.exactInt(proof,"pageGeneration",0,Integer.MAX_VALUE)!=page||!(proof.get("loading") instanceof Boolean)||!Boolean.TRUE.equals(proof.get("approvedCandidate")))return false;
  return task.getString("id").equals(proof.getString("taskId"))&&run.equals(proof.getString("runToken"))&&task.getString("date").equals(proof.getString("date"))&&task.getString("date").equals(proof.getString("selectedDate"))&&task.getString("cabin").equals(proof.getString("cabin"))&&task.getString("trip").equals(proof.getString("trip"))&&"REWARD".equals(proof.getString("bookingFlow"))&&fingerprint.equals(proof.getString("fingerprint"))&&from.getString("code").equals(proof.getJSONObject("origin").getString("code"))&&from.getString("type").equals(proof.getJSONObject("origin").getString("type"))&&to.getString("code").equals(proof.getJSONObject("destination").getString("code"))&&to.getString("type").equals(proof.getJSONObject("destination").getString("type"))&&Objects.equals(task.isNull("returnDate")?null:task.getString("returnDate"),proof.isNull("returnDate")?null:proof.getString("returnDate"));
 }catch(Exception e){return false;}}
 void visibleUnsupported(String source){if("VISIBLE_ACCESS_RESTRICTED".equals(source)){stopCapture("공식 사이트에서 접근을 제한했습니다. 자동검색을 중단하고 기존 결과를 유지합니다.",source);return;}pauseAutomaticFailure("DOM_RESULT_UNSUPPORTED",true,source);say("공식 화면의 선택 조건과 표시 운임을 확인하지 못해 중단했습니다. 좌석 없음이나 완료로 저장하지 않았으며 기존 기록은 유지됩니다.");}
 void handleVisibleResults(JSONObject visible,JSONObject settle,JSONObject request,JSONArray networkRecords,WebView source,JSONObject task,String run,int index,int generation)throws Exception{
  if(!witnessApproved(source,run,index)||!matchingRequest(request,task,run)){visibleUnsupported("VISIBLE_SCOPE_MISMATCH");return;}
  if(visible!=null&&"unsupported".equals(visible.optString("state"))){visibleUnsupported(visible.optString("reason","VISIBLE_RESULT_UNSUPPORTED"));return;}
  if(visible==null||!Boolean.TRUE.equals(visible.opt("candidate"))){resetVisibleQuiet();autoState.phase(autoPrefs,"waiting_result","wait_visible_results");say(automaticProgress(task)+"\n공식 화면의 선택 날짜와 항공편 표시를 확인하는 중입니다.");scheduleAutomatic(700);return;}
  if(!matchesVisibleCandidate(visible,task,run,pageGeneration)){visibleUnsupported("VISIBLE_SCOPE_MISMATCH");return;}
  if(!collectionApproved(source,run,index)||!"calendar_dom".equals(collectionMode)){collectionRun=run;collectionIndex=index;collectionPage=pageGeneration;collectionBrowser=source;collectionMode="calendar_dom";resetVisibleQuiet();autoState.phase(autoPrefs,"waiting_result","collect_visible");say(automaticProgress(task)+"\n공식 화면을 아래까지 확인하며 표시 운임을 읽는 중입니다.");scheduleAutomatic(700);return;}
  JSONObject proof=visible.getJSONObject("proof");String fingerprint=visible.getString("fingerprint");if(!settleReady(settle,request,task)||settle.optInt("pageGeneration",-1)!=pageGeneration||!fingerprint.equals(settle.optString("domFingerprint"))||settle.optInt("cardCount",-1)!=proof.getInt("cardCount")||proof.getBoolean("loading")){resetVisibleQuiet();scheduleAutomatic(700);return;}
  if(!fingerprint.equals(visibleQuietFingerprint)){visibleQuietFingerprint=fingerprint;visibleQuietSince=SystemClock.elapsedRealtime();scheduleAutomatic(700);return;}if(SystemClock.elapsedRealtime()-visibleQuietSince<1000){scheduleAutomatic(700);return;}
  if(networkRecords!=null&&networkValidated&&hasNetworkOffers(networkRecords)){persistAutomaticRecords(networkRecords,null,source,task,run,index,generation);return;}
  JSONObject record=visible.optJSONObject("record");if(record==null){visibleUnsupported("VISIBLE_PRICE_MISSING");return;}if(!"visible_offers".equals(record.optString("kind"))||!matchesAutomaticTask(record,task,run)||record.getJSONObject("visibleProof").getInt("generation")!=pageGeneration){visibleUnsupported("VISIBLE_SCOPE_MISMATCH");return;}persistAutomaticRecords(new JSONArray().put(record),null,source,task,run,index,generation);
 }
 static boolean hasNetworkOffers(JSONArray records){if(records!=null)for(int n=0;n<records.length();n++){JSONObject record=records.optJSONObject(n);if(record!=null&&"offers".equals(record.optString("kind")))return true;}return false;}
 static boolean onlyCalendarRecords(JSONArray records){if(records==null)return false;for(int n=0;n<records.length();n++){JSONObject value=records.optJSONObject(n);if(value==null||!"calendar".equals(value.optString("kind")))return false;}return true;}
 static boolean softFormatPacket(JSONObject packet){return packet!=null&&"unsupported_response".equals(packet.optString("status"))&&Set.of("SCHEMA_MISMATCH","ROUND_TRIP_PRICE").contains(packet.optString("reasonCode"));}
 static JSONArray retainedCalendars(JSONArray records){JSONArray out=new JSONArray();if(records!=null)for(int n=0;n<records.length();n++){JSONObject value=records.optJSONObject(n);if(value!=null&&"calendar".equals(value.optString("kind")))out.put(value);}return out;}
 static boolean settleReady(JSONObject settle,JSONObject request,JSONObject task)throws JSONException{return settle!=null&&"ready".equals(settle.optString("state"))&&task.getString("id").equals(settle.optString("taskKey"))&&settle.optInt("generation",-1)==request.optInt("generation",-2)&&request.getInt("pending")==0;}
 static boolean matchingRequest(JSONObject request,JSONObject task,String run)throws JSONException{if(request==null||!task.getString("id").equals(request.optString("taskId"))||!run.equals(request.optString("runToken")))return false;Object pending=request.opt("pending"),generation=request.opt("generation");if(!(pending instanceof Number)||!(generation instanceof Number))return false;double p=((Number)pending).doubleValue(),g=((Number)generation).doubleValue();return p==Math.rint(p)&&p>=0&&p<=100&&g==Math.rint(g)&&g>=0&&g<=Integer.MAX_VALUE;}
 static JSONObject calendarWitness(JSONObject calendar,JSONObject task,String run,String url,boolean navigationIssued,int page)throws JSONException{
  JSONObject out=new JSONObject(calendar.toString()).put("pageGeneration",page);out.remove("nativeNavigationEvidence");Uri current=secureUri(url);String path=current==null?"":current.getPath();boolean search=path!=null&&path.matches("(?:/[a-zA-Z]{2})?/search(?:/.*)?");
  try{if(navigationIssued&&search&&rewardPage(url)&&!authenticationPage(url)&&"calendar".equals(calendar.getString("kind"))&&matchesAutomaticTask(calendar,task,run)&&calendar.getString("pageOrigin").equals("https://"+current.getHost().toLowerCase(Locale.ROOT)))out.put("nativeNavigationEvidence","CURRENT_TASK_NORMAL_NAVIGATION");}catch(Exception ignored){}
  return out;
 }
 void approveResultCollection(JSONArray records,WebView source,JSONObject task,String run,int index,int generation,JSONObject request)throws Exception{
  approveResultCollection(records,source,task,run,index,generation,request,false);
 }
 void approveResultCollection(JSONArray records,WebView source,JSONObject task,String run,int index,int generation,JSONObject request,boolean softBootstrap)throws Exception{
  if(records.length()>30)throw new JSONException("too many capture envelopes");JSONArray copy=new JSONArray(records.toString());int sourcePage=pageGeneration;long operation=++automaticOperation;automaticBusy=true;
  if(!submit(()->{boolean offers=false;JSONObject calendar=null;String failure="";try{for(int n=0;n<copy.length();n++){JSONObject value=AirFranceData.normalize(copy.getJSONObject(n),System.currentTimeMillis());if(!matchesAutomaticTask(value,task,run)||softBootstrap&&(!"calendar".equals(value.getString("kind"))||!freshCalendar(value)))throw new JSONException("mismatched automatic result");if("offers".equals(value.getString("kind")))offers=true;else if("calendar".equals(value.getString("kind")))calendar=value;}}catch(Exception e){failure="RESULT_ERROR";}final boolean hasOffers=offers;final JSONObject witness=calendar;final String failed=failure;handler.post(()->{boolean ownsBusy=operation==automaticOperation;if(ownsBusy)automaticBusy=false;if(!ownsBusy||closed||!resumed||showingSaved||generation!=automaticGeneration||sourcePage!=pageGeneration||autoState==null||autoState.terminal()||!run.equals(autoState.runId)||index!=autoState.index||source!=activeBrowser()){wakeCurrentAutomatic(run,index);return;}
    if(!failed.isEmpty()){pauseAutomaticFailure(failed,true,"TASK_MISMATCH");return;}try{if(witness!=null){visibleWitness=calendarWitness(witness,task,run,safeUrl(source),autoState.navigationIssued,sourcePage);witnessRun=run;witnessIndex=index;witnessPage=sourcePage;witnessBrowser=source;}if(hasOffers){networkValidated=true;collectionRun=run;collectionIndex=index;collectionPage=sourcePage;collectionBrowser=source;if(!"calendar_dom".equals(collectionMode))collectionMode="offers";autoState.phase(autoPrefs,"waiting_result","collect_results");}}catch(JSONException e){pauseAutomaticFailure("STATE_ERROR",true);return;}say(automaticProgress(task)+(hasOffers?"\n항공편 목록을 끝까지 자동으로 확인합니다.":"\n공식 화면에 표시된 항공편과 운임을 확인하는 중입니다."));scheduleAutomatic(700);
   });})){automaticBusy=false;throw new JSONException("worker is closed");}scheduleAutomatic(250);
 }
 static boolean matchesAutomaticTask(JSONObject record,JSONObject task,String run)throws Exception{
  JSONObject from=task.getJSONObject("origin"),to=task.getJSONObject("destination");return task.getString("id").equals(record.getString("taskId"))&&run.equals(record.getString("taskRunId"))&&from.getString("code").equals(record.getString("origin"))&&to.getString("code").equals(record.getString("destination"))&&from.getString("type").equals(record.getString("originType"))&&to.getString("type").equals(record.getString("destinationType"))&&task.getString("date").equals(record.optString("requestDate",record.optString("date")))&&task.getString("cabin").equals(record.getString("cabin"))&&task.getString("trip").equals(record.getString("requestedTrip"))&&Objects.equals(task.isNull("returnDate")?null:task.getString("returnDate"),record.isNull("requestedReturnDate")?null:record.getString("requestedReturnDate"));
 }
 void persistAutomaticRecords(JSONArray records,JSONObject empty,WebView source,JSONObject task,String run,int index,int generation)throws Exception{
  AirFranceDb store=db;AirFranceAutoPlan planForRun=autoPlan;SharedPreferences prefsForRun=autoPrefs;int sourcePage=pageGeneration;if(store==null)throw new JSONException("missing local DB");if(records!=null&&records.length()>30)throw new JSONException("too many capture envelopes");
  JSONArray copy=records==null?null:new JSONArray(records.toString());JSONObject emptyCopy=empty==null?null:new JSONObject(empty.toString());automaticSaving=true;AUTOMATIC_WRITES.incrementAndGet();autoState.phase(autoPrefs,"saving","persist_result");
  boolean submitted=submit(()->{int saved=0,accepted=0;JSONArray ack=new JSONArray();JSONObject last=null;boolean wasEmpty=false;String failure=null;
   try{
    if(emptyCopy!=null){JSONObject proof=AirFranceData.normalizeEmpty(emptyCopy,System.currentTimeMillis());if(!matchesAutomaticTask(proof,task,run))throw new JSONException("mismatched empty proof");wasEmpty=true;accepted=1;}
    else {JSONArray canonical=new JSONArray();for(int n=0;n<copy.length();n++){JSONObject value=AirFranceData.normalize(copy.getJSONObject(n),System.currentTimeMillis());if(!matchesAutomaticTask(value,task,run)||"visible_offers".equals(value.optString("kind"))&&value.getJSONObject("visibleProof").getInt("generation")!=sourcePage)throw new JSONException("mismatched automatic result");canonical.put(value);}for(int n=0;n<canonical.length();n++){JSONObject value=canonical.getJSONObject(n);saved+=store.save(value);ack.put(value.getString("captureId"));last=value;accepted++;}}
    if(accepted==0)throw new JSONException("no matched result");
    if(!AirFranceAutoState.complete(prefsForRun,planForRun,run,index,saved))failure="STALE_TASK";
   }catch(android.database.sqlite.SQLiteException e){failure="STORAGE_ERROR";}catch(Exception e){failure="RESULT_ERROR";}finally{AUTOMATIC_WRITES.decrementAndGet();}
   final int persisted=saved;final String failed=failure;final JSONObject imported=last;final boolean noQuotes=wasEmpty;handler.post(()->{automaticSaving=false;if(closed)return;if(generation!=automaticGeneration)return;
    if(failed!=null){pauseAutomaticFailure(failed,true);say("자동검색 결과를 저장하지 못해 멈췄습니다. 완료로 세지 않았으며 기존 이력은 유지됩니다.");return;}
    try{autoState=AirFranceAutoState.read(autoPrefs,autoPlan);}catch(Exception e){pauseAutomaticFailure("STATE_ERROR",true);return;}
    if(resumed&&owned(source))try{source.evaluateJavascript("window.__tongAF&&window.__tongAF.ack("+ack.toString()+")",null);}catch(RuntimeException ignored){}
    if(imported!=null)try{month=YearMonth.from(LocalDate.parse(imported.getJSONArray("dates").getJSONObject(0).getString("date")));preferredRoute=imported.getString("origin")+" → "+imported.getString("destination");}catch(Exception ignored){}
    recordAutomaticObservation(noQuotes?"NO_MATCHING_QUOTES":"QUERY_CHECKED",persisted,task,run,index);if("complete".equals(autoState.mode))recordAutomaticObservation("COMPLETED",autoState.checked,task,run,index);say("자동검색 · "+autoState.checked+"/"+autoPlan.size()+"개 확인 · "+(noQuotes?"이번 검색에서 일치하는 운임 없음":persisted+"개 조회 항목 저장")+" · 과거 이력 유지");
    automaticStartedAt=0;nextAutomaticAt=SystemClock.elapsedRealtime()+10000;if(showingSaved)loadRoutes();scheduleAutomatic(10000);
   });
  });if(!submitted){AUTOMATIC_WRITES.decrementAndGet();automaticSaving=false;throw new JSONException("worker is closed");}
 }
 void editAutomaticPlan(){
  if(autoPlan==null)return;handler.removeCallbacks(automaticTick);LinearLayout form=new LinearLayout(this);form.setOrientation(LinearLayout.VERTICAL);int pad=Ui.dp(this,14);form.setPadding(pad,pad,pad,pad);
  EditText from=new EditText(this),to=new EditText(this),begin=new EditText(this),until=new EditText(this),stay=new EditText(this);from.setSingleLine(true);to.setSingleLine(true);begin.setSingleLine(true);until.setSingleLine(true);stay.setSingleLine(true);stay.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
  from.setText(autoPlan.origin.optString("code"));StringBuilder codes=new StringBuilder();for(int i=0;i<autoPlan.destinations.length();i++){if(i>0)codes.append(',');codes.append(autoPlan.destinations.optJSONObject(i).optString("code"));}to.setText(codes);begin.setText(autoPlan.start.toString());until.setText(autoPlan.end.toString());stay.setText(String.valueOf(autoPlan.returnDays==0?7:autoPlan.returnDays));
  Spinner fromType=new Spinner(this),toType=new Spinner(this),cabins=new Spinner(this),trip=new Spinner(this);String[] stationTypes={"도시","공항"};fromType.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,stationTypes));toType.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,stationTypes));fromType.setSelection("CITY".equals(autoPlan.origin.optString("type"))?0:1);toType.setSelection("CITY".equals(autoPlan.destinations.optJSONObject(0).optString("type"))?0:1);
  String[] cabinCodes={"ECONOMY","PREMIUM","BUSINESS","FIRST"};cabins.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"이코노미","프리미엄","비즈니스","퍼스트"}));cabins.setSelection(Arrays.asList(cabinCodes).indexOf(autoPlan.cabin));trip.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"왕복 · 오는 날짜 미정","편도","왕복 · 체류일수 지정"}));trip.setSelection("ONE_WAY".equals(autoPlan.trip)?1:autoPlan.returnDays==0?0:2);
  CheckBox reverse=new CheckBox(this);reverse.setText("출발·도착을 바꾼 방향도 검색");reverse.setChecked(autoPlan.bothDirections);
  form.addView(Ui.text(this,"출발 코드 (예: SEL, ICN)",13));form.addView(from);form.addView(fromType);form.addView(Ui.text(this,"도착 코드 (여러 곳은 쉼표로 구분)",13));form.addView(to);form.addView(toType);form.addView(Ui.text(this,"검색 시작일 · YYYY-MM-DD",13));form.addView(begin);form.addView(Ui.text(this,"검색 마지막 날 · YYYY-MM-DD",13));form.addView(until);form.addView(cabins);form.addView(trip);form.addView(Ui.text(this,"오는 날짜 지정 시 체류일수",13));form.addView(stay);form.addView(reverse);form.addView(Ui.text(this,"성인 1명 기준입니다. 기본 조건은 서울–파리 왕복 비즈니스, 내일부터 30일입니다. 로그인 후 선택한 조건의 날짜·노선을 순서대로 조회합니다.",12));
  ScrollView scroll=new ScrollView(this);scroll.addView(form);AlertDialog dialog=new AlertDialog.Builder(this).setTitle("에어프랑스 자동검색 조건").setView(scroll).setNegativeButton("취소",null).setPositiveButton("저장·자동검색",null).create();dialog.setOnDismissListener(d->scheduleAutomatic(500));dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{try{
   JSONObject origin=AirFranceAutoPlan.station(from.getText().toString().trim().toUpperCase(Locale.ROOT),fromType.getSelectedItemPosition()==0?"CITY":"AIRPORT");JSONArray destinations=new JSONArray();for(String code:to.getText().toString().toUpperCase(Locale.ROOT).split(","))destinations.put(AirFranceAutoPlan.station(code.trim(),toType.getSelectedItemPosition()==0?"CITY":"AIRPORT"));int tripIndex=trip.getSelectedItemPosition(),days=tripIndex==2?Integer.parseInt(stay.getText().toString().trim()):0;
   JSONObject value=new JSONObject().put("schema",1).put("origin",origin).put("destinations",destinations).put("start",begin.getText().toString().trim()).put("end",until.getText().toString().trim()).put("cabin",cabinCodes[cabins.getSelectedItemPosition()]).put("trip",tripIndex==1?"ONE_WAY":"ROUND_TRIP").put("returnDays",days).put("bothDirections",reverse.isChecked());AirFranceAutoPlan selected=AirFranceAutoPlan.parse(value,Search.today());
   stopAutomatic("PLAN_CHANGED");autoPlan=selected;autoPrefs.edit().putString(AirFranceAutoPlan.PLAN_KEY,selected.json().toString()).remove(AirFranceAutoPlan.RUN_KEY).apply();autoState=AirFranceAutoState.fresh(selected);autoState.persist(autoPrefs);planSummary.setText(selected.summary());dialog.dismiss();startAutomatic();
  }catch(Exception e){Ui.message(this,"검색 조건 확인","코드·기간·체류일수를 확인해 주세요. 최대 1,000개 조회 항목을 설정할 수 있습니다.");}}));dialog.show();
 }

 final class OfficialClient extends WebViewClient {
  @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){if(!request.isForMainFrame())return false;String value=request.getUrl().toString();if(officialNavigation(value))return false;if(request.hasGesture()&&secureUri(value)!=null){try{startActivity(new Intent(Intent.ACTION_VIEW,request.getUrl()));say("외부 브라우저를 열었습니다. 그 브라우저의 로그인은 앱에 자동으로 이어지지 않습니다.");}catch(ActivityNotFoundException e){say("연결할 브라우저가 없습니다.");}}else say("지원하지 않는 연결을 열지 않았습니다. 공식 화면에서 계속 진행해 주세요.");return true;}
  @Override public boolean shouldOverrideUrlLoading(WebView view,String value){return !officialNavigation(value);}
  @Override public void onPageStarted(WebView view,String url,Bitmap icon){if(owned(view)){pageGeneration++;automaticOperation++;automaticBusy=false;resetResultCollection();noteAuthenticationNavigation(url);if(autoState!=null)wakeCurrentAutomatic(autoState.runId,autoState.index);}}
  @Override public void onPageCommitVisible(WebView view,String url){if(owned(view))install(view);}
  @Override public void onPageFinished(WebView view,String url){if(!owned(view))return;String title;try{title=view.getTitle();}catch(RuntimeException e){return;}if(title!=null&&title.toLowerCase(Locale.ROOT).contains("access denied")){stopCapture("공식 사이트에서 접근을 제한했습니다. 가져오기를 중단하고 기존 저장 결과를 유지합니다.","DOCUMENT_DENIAL_TITLE");return;}install(view);}
  @Override public void onReceivedHttpError(WebView view,WebResourceRequest request,WebResourceResponse response){if(!owned(view)||!request.isForMainFrame())return;int code=response.getStatusCode();if(code==403||code==429)stopCapture("공식 사이트에서 접근을 제한했습니다. 가져오기를 중단하고 기존 저장 결과를 유지합니다.",code==403?"MAIN_HTTP_403":"MAIN_HTTP_429");else if(code==401){recordFailure("AF_LOGIN_REQUIRED");say("공식 화면에서 다시 로그인해 주세요.");setCapture(view,false);}}
  @Override public void onReceivedError(WebView view,WebResourceRequest request,WebResourceError error){if(owned(view)&&request.isForMainFrame())stopCapture("공식 화면에 연결하지 못했습니다. 저장된 결과는 유지됩니다.");}
  @Override public void onReceivedSslError(WebView view,SslErrorHandler ssl,SslError error){ssl.cancel();if(owned(view))stopCapture("공식 연결의 보안을 확인하지 못해 중단했습니다. 저장된 결과는 유지됩니다.");}
  @Override public boolean onRenderProcessGone(WebView view,RenderProcessGoneDetail detail){if(!owned(view))return true;if(view==popup)closePopup();else if(view==browser){browser=null;dispose(view);}stopCapture("공식 화면이 종료되었습니다. 저장된 결과는 유지됩니다. 로그인·검색을 눌러 화면을 다시 열 수 있습니다.");return true;}
 }
 final class OfficialChrome extends WebChromeClient {
  @Override public boolean onCreateWindow(WebView source,boolean dialog,boolean gesture,Message result){if(closed||!resumed||!gesture||!owned(source)||!officialNavigation(safeUrl(source))||popup!=null||!(result.obj instanceof WebView.WebViewTransport))return false;
   WebView child=null;try{child=new WebView(AirFranceActivity.this);configure(child);if(autoState!=null&&!autoState.terminal())try{installEarlyForTask(child,autoPlan.task(autoState.index),autoState.runId);}catch(Exception e){dispose(child);say("로그인 창의 자동검색 연결을 준비하지 못했습니다.");return false;}popup=child;popupDialog=new Dialog(AirFranceActivity.this);LinearLayout body=Ui.root(AirFranceActivity.this);body.addView(Ui.button(AirFranceActivity.this,"로그인 창 닫기",v->closePopup()),new LinearLayout.LayoutParams(-1,-2));body.addView(child,new LinearLayout.LayoutParams(-1,0,1));popupDialog.setContentView(body);popupDialog.setOnDismissListener(d->closePopup());popupDialog.show();popupDialog.getWindow().setLayout(-1,-1);((WebView.WebViewTransport)result.obj).setWebView(child);result.sendToTarget();return true;}catch(RuntimeException e){if(child!=null&&popup!=child)dispose(child);closePopup();say("공식 로그인 창을 열지 못했습니다. 현재 화면에서 다시 진행해 주세요.");return false;}}
  @Override public void onCloseWindow(WebView view){if(view==popup)closePopup();}
 }
 void closePopup(){WebView child=popup;popup=null;AirFranceDocumentStart.Handle early=earlyScripts.remove(child);if(early!=null)try{early.remove();}catch(RuntimeException ignored){}Dialog dialog=popupDialog;popupDialog=null;if(dialog!=null)try{dialog.setOnDismissListener(null);dialog.dismiss();}catch(RuntimeException ignored){}dispose(child);}
 static void dispose(WebView view){if(view==null)return;try{view.stopLoading();}catch(RuntimeException ignored){}try{if(view.getParent() instanceof android.view.ViewGroup)((android.view.ViewGroup)view.getParent()).removeView(view);}catch(RuntimeException ignored){}try{view.destroy();}catch(RuntimeException ignored){}}
 void showSaved(){showingSaved=true;resetResultCollection();setDriverForeground(browser,false);setDriverForeground(popup,false);browserBox.setVisibility(View.GONE);saved.setVisibility(View.VISIBLE);if(saved.getChildCount()==0)buildSaved();loadRoutes();}
 void buildSaved(){
  routeFilter=new Spinner(this);saved.addView(routeFilter);cabinFilter=new Spinner(this);cabinFilter.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"모든 좌석 등급","이코노미","프리미엄","비즈니스","퍼스트"}));saved.addView(cabinFilter);
  LinearLayout nav=new LinearLayout(this);nav.addView(Ui.button(this,"이전 달",v->{month=month.minusMonths(1);loadRows();}),new LinearLayout.LayoutParams(0,-2,1));monthLabel=Ui.text(this,"",15);monthLabel.setGravity(Gravity.CENTER);nav.addView(monthLabel,new LinearLayout.LayoutParams(0,-2,1));nav.addView(Ui.button(this,"다음 달",v->{month=month.plusMonths(1);loadRows();}),new LinearLayout.LayoutParams(0,-2,1));saved.addView(nav);
  saved.addView(Ui.text(this,"달력 참고 = 검색 당시 최저 마일 후보 · 상세 조회 = 확인된 항공편 정보 · 화면 표시 운임 = 공식 화면에서 읽은 운임이며 편명은 미확인입니다. 잔여 좌석 수가 제공되지 않으면 알 수 없음으로 표시합니다. 모두 성인 1명 조회 기준입니다.",11));
  ScrollView scroll=new ScrollView(this);days=new LinearLayout(this);days.setOrientation(LinearLayout.VERTICAL);scroll.addView(days);saved.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));saved.addView(Ui.button(this,"저장 결과 내보내기",v->exportSaved()),new LinearLayout.LayoutParams(-1,-2));
  AdapterView.OnItemSelectedListener choice=new AdapterView.OnItemSelectedListener(){public void onItemSelected(AdapterView<?> p,View v,int n,long id){if(!loadingFilters)loadRows();}public void onNothingSelected(AdapterView<?> p){}};routeFilter.setOnItemSelectedListener(choice);cabinFilter.setOnItemSelectedListener(choice);
 }
 void loadRoutes(){if(db==null){say("저장 공간을 열지 못했습니다.");return;}AirFranceDb store=db;submit(()->{try{JSONArray found=store.routes();handler.post(()->{if(closed||!showingSaved)return;String previous=preferredRoute.isEmpty()?routeCode():preferredRoute;routes=found;List<String> labels=new ArrayList<>();int selected=0;for(int i=0;i<routes.length();i++){JSONObject route=routes.optJSONObject(i);String label=route.optString("origin")+" → "+route.optString("destination");labels.add(label);if(label.equals(previous))selected=i;}if(labels.isEmpty())labels.add("저장된 노선 없음");loadingFilters=true;routeFilter.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,labels));routeFilter.setSelection(selected);loadingFilters=false;loadRows();});}catch(Exception e){handler.post(()->say("저장 결과를 불러오지 못했습니다. 자료는 유지됩니다."));}});}
 String routeCode(){int i=routeFilter==null?-1:routeFilter.getSelectedItemPosition();JSONObject r=routes.optJSONObject(i);return r==null?"":r.optString("origin")+" → "+r.optString("destination");}
 void loadRows(){if(closed||!showingSaved||days==null)return;int generation=++recordsGeneration;monthLabel.setText(month.toString());JSONObject route=routes.optJSONObject(routeFilter.getSelectedItemPosition());if(route==null){rows=new JSONArray();renderDays();return;}String origin=route.optString("origin"),destination=route.optString("destination");YearMonth wanted=month;AirFranceDb store=db;if(store==null)return;submit(()->{try{JSONArray found=store.records(origin,destination,wanted);handler.post(()->{if(closed||!showingSaved||generation!=recordsGeneration)return;rows=found;renderDays();});}catch(Exception e){handler.post(()->say("저장 결과를 불러오지 못했습니다. 자료는 유지됩니다."));}});}
 static String cabinName(String cabin){return switch(cabin){case "ECONOMY"->"이코노미";case "PREMIUM"->"프리미엄";case "BUSINESS"->"비즈니스";case "FIRST"->"퍼스트";default->"등급 미확인";};}
 boolean selectedCabin(JSONObject row){int selected=cabinFilter.getSelectedItemPosition();return selected==0||row.optString("cabin").equals(new String[]{"","ECONOMY","PREMIUM","BUSINESS","FIRST"}[selected]);}
 static String priceLabel(JSONObject row){String scope=row.optString("priceScope");boolean round="ROUND_TRIP".equals(scope);String kind="DOM_PRICE".equals(scope)?"화면 표시 ":"OUTBOUND_OF_ROUND_TRIP".equals(scope)?"왕복 여정 가는 편 ":round?(row.isNull("returnDate")?"왕복 최저 ":"왕복 합계 "):"편도 ";if("MONTH".equals(row.optString("calendarType")))kind="월 최저 · 날짜 미확정 · "+kind;return kind+String.format(Locale.KOREA,"%,d",row.optLong("miles"))+" 마일";}
 static String observationLabel(JSONObject row){return "visible_offers".equals(row.optString("kind"))?"화면 표시 운임 · 편명 미확인":"offers".equals(row.optString("kind"))?"상세 조회":"달력 참고";}
 static String visibleTimes(JSONObject row){JSONObject itinerary=row.optJSONObject("visibleItinerary");if(itinerary==null)return "";StringBuilder text=new StringBuilder("공식 화면의 현지 시각 · 시간대·편명 미확인\n").append(itinerary.optString("origin")).append(' ').append(itinerary.optString("departureLocalTime")).append(" → ").append(itinerary.optString("destination")).append(' ').append(itinerary.optString("arrivalLocalTime")).append('\n');int stops=itinerary.optInt("connectionCount",-1);if(stops>=0)text.append(stops==0?"직항":"경유 "+stops+"회").append('\n');if(!itinerary.isNull("arrivalDayOffset"))text.append("도착일 +").append(itinerary.optInt("arrivalDayOffset")).append("일\n");return text.toString();}
 static String seatsLabel(JSONObject row){return row.isNull("remainingSeats")?"해당 운임 좌석 수 알 수 없음":"해당 운임 표시 "+row.optInt("remainingSeats")+"석 · 조회 시점 기준";}
 void renderDays(){days.removeAllViews();if(rows.length()>=2000)days.addView(Ui.text(this,"표시할 수 있는 최대 2,000개 조회 항목입니다. 전체 이력은 내보내기로 보관할 수 있습니다.",12));boolean any=false;for(int i=0;i<rows.length();i++){JSONObject row=rows.optJSONObject(i);if(row!=null&&"MONTH".equals(row.optString("calendarType"))&&selectedCabin(row)){any=true;JSONArray item=new JSONArray().put(row);Button candidate=Ui.button(this,month+" · "+cabinName(row.optString("cabin"))+"\n"+priceLabel(row)+"\n"+seatsLabel(row),v->showDetails(month+" 월 최저 마일 후보",item));candidate.setTextSize(13);days.addView(candidate,new LinearLayout.LayoutParams(-1,-2));}}for(int day=1;day<=month.lengthOfMonth();day++){String date=month.atDay(day).toString();JSONArray matches=new JSONArray();StringBuilder line=new StringBuilder(date);for(int i=0;i<rows.length();i++){JSONObject row=rows.optJSONObject(i);if(row!=null&&!"MONTH".equals(row.optString("calendarType"))&&date.equals(row.optString("date"))&&selectedCabin(row)){matches.put(row);line.append("\n").append(cabinName(row.optString("cabin"))).append(" · ").append(observationLabel(row)).append(" · ").append(priceLabel(row)).append((row.optString("fareFamily").isEmpty()||"UNKNOWN".equals(row.optString("fareFamily")))?"":" · "+row.optString("fareFamily")).append("\n").append(seatsLabel(row));}}if(matches.length()>0){any=true;Button dayButton=Ui.button(this,line.toString(),v->showDetails(date,matches));dayButton.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);dayButton.setTextSize(13);days.addView(dayButton,new LinearLayout.LayoutParams(-1,-2));}}if(!any)days.addView(Ui.text(this,"현재 표시 범위에서 이 달·등급의 자료를 찾지 못했습니다. 좌석이 없다는 뜻은 아닙니다.",14));}
 void showDetails(String date,JSONArray matches){StringBuilder text=new StringBuilder();for(int i=0;i<matches.length();i++){JSONObject row=matches.optJSONObject(i);text.append(cabinName(row.optString("cabin"))).append(" · ").append(observationLabel(row)).append("\n").append(priceLabel(row)).append(" · 성인 1명 기준\n").append((row.optString("fareFamily").isEmpty()||"UNKNOWN".equals(row.optString("fareFamily")))?"":"운임 종류: "+row.optString("fareFamily")+"\n").append(seatsLabel(row)).append("\n").append(visibleTimes(row));if(!row.isNull("returnDate"))text.append("오는 날짜: ").append(row.optString("returnDate")).append("\n");JSONObject tax=row.optJSONObject("tax");if(tax!=null)text.append("세금·수수료: ").append(tax.optString("currency")).append(" ").append(tax.opt("amount")).append("\n");else text.append("세금·수수료: 알 수 없음\n");text.append("조회 시각 (UTC): ").append(row.optString("observedAt")).append("\n");JSONArray flights=row.optJSONArray("flights");if(flights!=null&&flights.length()>0)for(int j=0;j<flights.length();j++){JSONObject f=flights.optJSONObject(j);if(f!=null)text.append(f.optString("number")).append(" ").append(f.optString("origin")).append(" → ").append(f.optString("destination")).append(" · ").append(f.optString("departure")).append("\n");}text.append("\n");}Ui.message(this,date,text.toString());}
 void exportSaved(){if(db==null)return;Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/x-ndjson").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE,"TongSKY-AirFrance-"+LocalDate.now(ZoneId.of("Asia/Seoul"))+".jsonl");try{startActivityForResult(intent,EXPORT);}catch(ActivityNotFoundException e){say("파일을 저장할 앱을 찾지 못했습니다.");}}
 @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request!=EXPORT||result!=RESULT_OK||data==null||data.getData()==null||closed||db==null)return;Uri uri=data.getData();AirFranceDb store=db;submit(()->{try(OutputStream out=getContentResolver().openOutputStream(uri,"wt")){if(out==null)throw new IOException();store.export(out);handler.post(()->say("저장 결과를 내보냈습니다."));}catch(Exception e){recordFailure("AF_STORAGE_ERROR");handler.post(()->say("파일을 내보내지 못했습니다. 기기의 저장 결과는 유지됩니다."));}});}
 void goBack(){try{if(popup!=null){if(popup.canGoBack())popup.goBack();else closePopup();return;}if(showingSaved){showingSaved=false;saved.setVisibility(View.GONE);browserBox.setVisibility(View.VISIBLE);setDriverForeground(browser,true);setDriverForeground(popup,true);scheduleAutomatic(100);return;}if(browser!=null&&browser.canGoBack()){browser.goBack();return;}finish();}catch(RuntimeException e){stopCapture("이전 공식 화면을 열지 못했습니다. 저장된 결과는 유지됩니다.");}}
 @Override public void onBackPressed(){goBack();}
 @Override protected void onResume(){super.onResume();resumed=true;if(automaticPausedAt>0&&automaticStartedAt>0)automaticStartedAt+=SystemClock.elapsedRealtime()-automaticPausedAt;automaticPausedAt=0;
  try{if(browser!=null){browser.onResume();install(browser);setDriverForeground(browser,true);}if(popup!=null){popup.onResume();install(popup);setDriverForeground(popup,true);}}catch(RuntimeException e){stopCapture("공식 화면과의 연결이 중단되었습니다. 저장된 결과는 유지됩니다.");}
  scheduleAutomatic(150);
 }
 @Override protected void onPause(){resumed=false;automaticOperation++;automaticBusy=false;resetResultCollection();setDriverForeground(browser,false);setDriverForeground(popup,false);automaticPausedAt=SystemClock.elapsedRealtime();handler.removeCallbacks(automaticTick);if(autoState==null||autoState.terminal()){setCapture(browser,false);setCapture(popup,false);}try{if(browser!=null)browser.onPause();if(popup!=null)popup.onPause();}catch(RuntimeException ignored){}try{if(airFranceProfileReady())CookieManager.getInstance().flush();}catch(RuntimeException ignored){}super.onPause();}
 @Override protected void onDestroy(){if(closed){super.onDestroy();return;}closed=true;resumed=false;automaticGeneration++;pageGeneration++;recordsGeneration++;handler.removeCallbacksAndMessages(null);for(AirFranceDocumentStart.Handle handle:earlyScripts.values())try{handle.remove();}catch(RuntimeException ignored){}earlyScripts.clear();closePopup();WebView old=browser;browser=null;dispose(old);AirFranceDb store=db;db=null;try{worker.execute(()->{if(store!=null)store.close();});}catch(RejectedExecutionException ignored){}worker.shutdown();super.onDestroy();}
}

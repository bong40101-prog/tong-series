package kr.tong.sky;

import android.app.*;import android.content.*;import android.content.pm.PackageManager;import android.os.*;import android.view.*;import android.webkit.*;import android.widget.*;import org.json.*;import java.time.*;import java.time.format.*;import java.nio.charset.StandardCharsets;import java.util.concurrent.*;

/** Offline, debug-only real Android startup/VirtualDisplay/WebView probe. */
public final class LabInstrumentation extends Instrumentation {
 Context ctx;final JSONArray cases=new JSONArray();int intercepted=0;
 final String waiting="<!doctype html><meta name='viewport' content='width=device-width,initial-scale=1'><p>Offline startup fixture</p>";
 @Override public void onCreate(Bundle args){super.onCreate(args);start();}
 @Override public void onStart(){ctx=getTargetContext();Bundle result=new Bundle();try{
  check(BuildConfig.DEBUG,"probe requires debug build");
  check(ctx.checkSelfPermission(android.Manifest.permission.INTERNET)==PackageManager.PERMISSION_DENIED,"probe APK must have no INTERNET permission");
  ctx.getSharedPreferences("settings",0).edit().putBoolean("testOffline",true).commit();
  ctx.getSharedPreferences("settings-asiana",0).edit().putBoolean("testOffline",true).commit();
  runFullForm(false);stopBoth();runFullForm(true);stopBoth();runButtonStartup(true);stopBoth();runButtonStartup(false);stopBoth();
  JSONObject report=new JSONObject().put("success",true).put("cases",cases).put("interceptedCollectLaunches",intercepted).put("appVersion",BuildConfig.VERSION_NAME).put("api",Build.VERSION.SDK_INT).put("webViewPackage",WebView.getCurrentWebViewPackage()==null?"none":WebView.getCurrentWebViewPackage().packageName).put("networkPermission",false);
  save(report);result.putString("stream","OFFLINE_DEVICE_PROBE_PASS "+report+"\n");finish(Activity.RESULT_OK,result);
 }catch(Throwable failure){failure.printStackTrace();try{save(new JSONObject().put("success",false).put("cases",cases).put("error",failure.toString()));}catch(Exception ignored){}result.putString("stream","OFFLINE_DEVICE_PROBE_FAIL "+failure+"\n");finish(Activity.RESULT_CANCELED,result);}}
 void save(JSONObject report)throws Exception{try(java.io.FileOutputStream out=ctx.openFileOutput("device-startup-report.json",Context.MODE_PRIVATE)){out.write(report.toString(2).getBytes(StandardCharsets.UTF_8));}}
 static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 interface Condition {boolean get()throws Exception;}
 void await(String message,long millis,Condition condition)throws Exception{long end=SystemClock.elapsedRealtime()+millis;while(SystemClock.elapsedRealtime()<end){if(condition.get())return;Thread.sleep(100);}throw new AssertionError(message+" KE="+state(false)+" OZ="+state(true));}
 JSONObject state(boolean oz)throws Exception{return new JSONObject(ctx.getSharedPreferences(oz?"collection-oz":"collection",0).getString("state","{}"));}
 void onMain(Runnable task){runOnMainSync(task);}
 boolean browserReady(boolean oz){boolean[] ready={false};onMain(()->{if(oz){AsianaCollectionService s=AsianaCollectionService.activeOz;ready[0]=s!=null&&s.web!=null&&s.web.getWidth()>0&&s.presentation!=null&&s.presentation.isShowing()&&s.display!=null&&s.display.getDisplay().isValid()&&s.wake!=null&&s.wake.isHeld();}else{CollectionService s=CollectionService.active;ready[0]=s!=null&&s.web!=null&&s.web.getWidth()>0&&s.presentation!=null&&s.presentation.isShowing()&&s.display!=null&&s.display.getDisplay().isValid()&&s.wake!=null&&s.wake.isHeld();}});return ready[0];}
 Activity launcher(){return startActivitySync(new Intent(ctx,LabActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));}
 void startKeWaiting()throws Exception{Activity a=launcher();onMain(()->ctx.startForegroundService(new Intent(ctx,CollectionService.class).putExtra("liveProbe",true).putExtra("__fixture",waiting)));await("KE private browser did not start",15000,()->browserReady(false));onMain(a::finish);}
 void assertSeparateBrowsers(){onMain(()->{CollectionService ke=CollectionService.active;AsianaCollectionService oz=AsianaCollectionService.activeOz;check(ke!=null&&oz!=null,"both collectors must be alive");check(ke.web!=oz.web,"independent browsers");check(ke.display.getDisplay().getDisplayId()!=oz.display.getDisplay().getDisplayId(),"independent private displays");check(ke.web.getSettings().getBlockNetworkLoads()&&oz.web.getSettings().getBlockNetworkLoads(),"all fixture browsers block network loads");});}
 void stopBoth()throws Exception{onMain(()->{if(CollectionService.active!=null)CollectionService.active.cancel("오프라인 장치 검사 정리");if(AsianaCollectionService.activeOz!=null)AsianaCollectionService.activeOz.cancel("오프라인 장치 검사 정리");});await("services did not stop",10000,()->CollectionService.active==null&&AsianaCollectionService.activeOz==null);Thread.sleep(150);}
 void runFullForm(boolean withKe)throws Exception{
  long start=SystemClock.elapsedRealtime();ctx.getSharedPreferences("collection-oz",0).edit().clear().commit();if(withKe)startKeWaiting();
  Activity a=launcher();String html=form();Intent i=new Intent(ctx,AsianaCollectionService.class).putExtra("liveProbe",true).putExtra("probeDestination","NRT").putExtra("__fixture",html).putExtra("__formFixture",true);
  onMain(()->ctx.startForegroundService(i));await("OZ real private browser did not start",15000,()->browserReady(true));
  onMain(a::finish);
  if(withKe){assertSeparateBrowsers();onMain(()->CollectionService.active.cancel("동시 브라우저 시작 검사 완료"));await("KE stop did not complete",10000,()->CollectionService.active==null);check(browserReady(true),"stopping KE must preserve OZ browser");}
  await("OZ form did not complete",65000,()->state(true).has("running")&&!state(true).optBoolean("running",true));
  JSONObject s=state(true);check(s.optString("title").startsWith("조회 완료"),"OZ failed before calendar save: "+s);check(s.optInt("done")==1,"one fixture month must complete: "+s);
  String key="ICN-NRT-"+YearMonth.from(Search.today().plusMonths(1));try(SkyDb db=new SkyDb(ctx,true)){check(db.data(key)!=null,"real service did not save "+key);}
  await("completed OZ service did not release resources",10000,()->AsianaCollectionService.activeOz==null);
  cases.put(new JSONObject().put("name",withKe?"KE then OZ real form and independent stop":"OZ only real form calendar save").put("passed",true).put("elapsedMs",SystemClock.elapsedRealtime()-start).put("savedKey",key));
 }
 Button fullScan(View view){if(view instanceof Button b&&"전체 노선 검색".contentEquals(b.getText()))return b;if(view instanceof ViewGroup g)for(int n=0;n<g.getChildCount();n++){Button found=fullScan(g.getChildAt(n));if(found!=null)return found;}return null;}
 void pressFullScan(boolean oz)throws Exception{
  Activity a=startActivitySync(new Intent(ctx,oz?AsianaActivity.class:MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
  await("full-route button missing",10000,()->{boolean[] found={false};onMain(()->found[0]=fullScan(a.getWindow().getDecorView())!=null);return found[0];});
  onMain(()->{Button b=fullScan(a.getWindow().getDecorView());check(b!=null&&b.isEnabled(),"full-route button not enabled");check(b.performClick(),"full-route button did not handle click");});
  await("button flow did not reach real private browser",30000,()->browserReady(oz));
 }
 void runButtonStartup(boolean onlyOz)throws Exception{
  long start=SystemClock.elapsedRealtime();ActivityMonitor monitor=new ActivityMonitor(){@Override public ActivityResult onStartActivity(Intent intent){if(intent.getComponent()!=null&&CollectActivity.class.getName().equals(intent.getComponent().getClassName())&&intent.hasExtra("batchKey")){intent.putExtra("__fixture",waiting).putExtra("shared",false);intercepted++;}return null;}};addMonitor(monitor);
  try{if(!onlyOz)pressFullScan(false);pressFullScan(true);if(!onlyOz)assertSeparateBrowsers();check(browserReady(true),"OZ button startup must keep a real browser");cases.put(new JSONObject().put("name",onlyOz?"AsianaActivity full-route button startup":"KE full-route then OZ full-route button startup").put("passed",true).put("elapsedMs",SystemClock.elapsedRealtime()-start));}finally{removeMonitor(monitor);}
 }
 String form(){
  YearMonth m=YearMonth.from(Search.today().plusMonths(1));String ym=m.toString().replace('-','.');String shortYm=ym.substring(2);String source=ZonedDateTime.now(Search.KST).format(DateTimeFormatter.ofPattern("yyyy. MM. dd. HH:mm",java.util.Locale.ROOT));
  StringBuilder h=new StringBuilder("<!doctype html><meta name='viewport' content='width=device-width,initial-scale=1'><style>body{margin:0;padding:20px}a,button{display:block;padding:12px;margin:12px}section{display:none;position:fixed;top:20px;left:5px;width:300px;height:600px;background:white;z-index:4}#depCalendar{display:none}</style><input type='hidden' id='tripType' value='RT'><a href='javascript:void(0)' onclick=\"document.getElementById('tripType').value='OW';this.dataset.selected='yes'\">편도</a><input id='departureAirport1'><input id='arrivalAirport1'><input id='departureDate1'><a id='btn_depAllAirport1' href='javascript:void(0)' onclick=\"document.getElementById('depAllAirport1').style.display='block'\">출발 도시</a><a id='btn_arrAllAirport1' href='javascript:void(0)' onclick=\"document.getElementById('arrAllAirport1').style.display='block'\">도착 도시</a><section id='depAllAirport1'><ul><li airport='ICN' area='KR' mgtarea='KR'><a href='#none' onclick=\"document.getElementById('departureAirport1').value='ICN';document.getElementById('depAllAirport1').style.display='none'\">서울 인천 ICN</a></li></ul></section><section id='arrAllAirport1'><ul><li airport='NRT' area='CE' mgtarea='CE'><a href='#none' onclick=\"document.getElementById('arrivalAirport1').value='NRT';document.getElementById('arrAllAirport1').style.display='none'\">나리타 NRT</a></li></ul></section><button id='sCalendarMonth' onclick=\"document.getElementById('months').style.display='block'\">탑승월</button><section id='months'><button class='month_btn' data-year='");
  h.append(m.getYear()).append("' data-month='").append(m.getMonthValue()).append("' onclick=\"document.getElementById('departureDate1').value='").append(ym).append("';document.getElementById('span_dep_date').textContent='").append(shortYm).append("';document.getElementById('months').style.display='none'\">탑승월</button></section><span id='span_dep_date'></span><button id='btn_MileageSeat_search' onclick=\"document.getElementById('depCalendar').style.display='block'\">조회</button><p>좌석 상황은 대한민국 시간 (").append(source).append(")</p><div id='depCalendar'><h2>").append(m.getYear()).append("년 ").append(m.getMonthValue()).append("월</h2>");
  for(int day=1;day<=m.lengthOfMonth();day++)h.append("<a id='cal_").append(day).append("' data-date='").append(m.atDay(day).toString().replace('-','.')).append("' data-economy='[]' data-business='[]'>").append(day).append("</a>");return h.append("</div>").toString();
 }
}

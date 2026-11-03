package kr.tong.sky;

import android.app.Service;
import android.content.*;
import android.net.Uri;
import android.os.*;
import android.webkit.*;
import android.widget.*;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;
import org.junit.*;
import static org.junit.Assert.*;
import org.robolectric.*;
import org.robolectric.annotation.*;

/** Offline checks of the native monthly calendar -> validated save path. */
@org.junit.runner.RunWith(RobolectricTestRunner.class)
@Config(manifest=Config.NONE,packageName="kr.tong.sky",sdk=35)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class ServiceMonthlyCalendarTest {
 Context app;final List<Service> services=new ArrayList<>();
 @Before public void setup()throws Exception{app=RuntimeEnvironment.getApplication();var catalog=RepeatMarkerPolicy.class.getDeclaredField("catalog");catalog.setAccessible(true);catalog.set(null,new JSONObject("{\"regions\":{\"유럽\":[{\"code\":\"FRA\"}]}}"));LocalScannerLease.owners.clear();LocalScannerLease.lease=null;for(String name:new String[]{"tong-sky.db","tong-sky-asiana.db","tong-sky-diagnostics.db"})app.deleteDatabase(name);}
 @After public void cleanup()throws Exception{for(Service service:services){service.onDestroy();ExecutorService worker=service instanceof CollectionService ke?ke.sharedWorker:((AsianaCollectionService)service).sharedWorker;assertTrue(worker.awaitTermination(5,TimeUnit.SECONDS));}LocalScannerLease.owners.clear();LocalScannerLease.lease=null;CollectionService.active=null;AsianaCollectionService.activeOz=null;}
 static final class ProbeWeb extends WebView {
  String url=CollectionService.SOURCE;String evaluated="";final List<String> loads=new ArrayList<>();ValueCallback<String> callback;
  ProbeWeb(Context c){super(c);}
  @Override public String getUrl(){return url;}
  @Override public void loadUrl(String url,Map<String,String> headers){this.url=url;loads.add(url);}
  @Override public void loadUrl(String url){this.url=url;loads.add(url);}
  @Override public void evaluateJavascript(String script,ValueCallback<String> result){evaluated=script;callback=result;}
 }
 CollectionService service()throws Exception{
  CollectionService s=Robolectric.buildService(CollectionService.class).get();services.add(s);s.status=new TextView(s);s.progressTitle=new TextView(s);s.progressInfo=new TextView(s);s.progress=new ProgressBar(s,null,android.R.attr.progressBarStyleHorizontal);s.db=new SkyDb(s);s.request=new Intent(s,CollectionService.class);s.web=new ProbeWeb(s);s.script="(function(){return {wait:true}})";
  ScannerLease lease=new ScannerLease(new SharedCache.Backend(){public SharedCache.Document read(){return new SharedCache.Document(null,null);}public void write(JSONObject body,String sha){}});lease.deadline=lease.renewAt=SystemClock.elapsedRealtime()+600_000;s.scannerLease=lease;LocalScannerLease.lease=lease;LocalScannerLease.owners.add(s);return s;
 }
 JSONObject query(YearMonth month)throws Exception{return new JSONObject().put("carrier","KE").put("origin","ICN").put("destination","FRA").put("month",month.toString());}

 JSONObject snapshot(JSONObject q,int daysAgo)throws Exception{
  String at=OffsetDateTime.now(Search.KST).minusDays(daysAgo).toString();YearMonth month=YearMonth.parse(q.getString("month"));JSONArray days=new JSONArray();for(int i=1;i<=month.lengthOfMonth();i++){JSONObject d=new JSONObject().put("date",month.atDay(i).toString()).put("status","OBSERVED");for(String f:Search.ALL_FIELDS)d.put(f,!"economyAward".equals(f));days.put(d);}return new JSONObject(q.toString()).put("collectionMethod","MONTHLY_CALENDAR").put("sourceUpdatedAt",at).put("collectedAt",at).put("dates",days);
 }
 void running(CollectionService s)throws Exception{s.query=query(YearMonth.from(Search.today()).plusMonths(1));s.queue.add(s.query);s.running=true;s.stage="parse";s.stageAt=s.totalAt=SystemClock.elapsedRealtime();}
 ValueCallback<String> callback(CollectionService s)throws Exception{s.tick(s.token);ProbeWeb web=(ProbeWeb)s.web;assertNotNull(web.callback);return web.callback;}
 String result(JSONObject snapshot)throws Exception{return new JSONObject().put("data",snapshot).toString();}
 @Test public void productionMonthlyCalendarSavesImmediatelyWithoutFurtherNavigation()throws Exception{
  CollectionService s=service();running(s);JSONObject raw=snapshot(s.query,0);callback(s).onReceiveValue(result(raw));assertEquals(1,s.index);assertEquals(1,s.success);assertEquals(0,((ProbeWeb)s.web).loads.size());JSONObject saved=s.db.data(Search.key(s.query)).getJSONObject("current");assertFalse(saved.has("operationWeeks"));assertFalse(saved.has("flightDetailPolicyVersion"));assertTrue(Search.sameDates(raw.getJSONArray("dates"),saved.getJSONArray("dates")));assertTrue(SharedCache.collectionFresh(s.db.data(Search.key(s.query)),System.currentTimeMillis()));
 }
 @Test public void monthlyCalendarOutsideRepeatRegionKeepsAllRawMarks()throws Exception{
  CollectionService s=service();running(s);s.query.put("destination","BKK");callback(s).onReceiveValue(result(snapshot(s.query,0)));JSONObject saved=s.db.data(Search.key(s.query)).getJSONObject("current"),view=RouteWeekdayFilter.apply(saved);assertFalse(saved.has("repeatMarkerPolicy"));JSONObject day=view.getJSONArray("dates").getJSONObject(0);assertTrue(SeatMarks.verified(day,false));assertTrue(SeatMarks.marked(day,"prestigeAward",false));assertTrue(SharedCache.collectionFresh(s.db.data(Search.key(s.query)),System.currentTimeMillis()));
 }
 @Test public void savedMonthlyCalendarUsesWholeWeekdayTripletsWithoutFlightProof()throws Exception{
  CollectionService s=service();running(s);JSONObject raw=snapshot(s.query,0);JSONArray days=raw.getJSONArray("dates");for(int i=0;i<days.length();i++){JSONObject day=days.getJSONObject(i);day.put("economyAward",LocalDate.parse(day.getString("date")).getDayOfWeek()==DayOfWeek.SUNDAY);}callback(s).onReceiveValue(result(raw));JSONObject saved=s.db.data(Search.key(s.query)).getJSONObject("current"),view=RouteWeekdayFilter.apply(saved);assertEquals("[0]",saved.getJSONObject("repeatMarkerPolicy").getJSONArray("weekdays").toString());for(int i=0;i<days.length();i++){JSONObject day=view.getJSONArray("dates").getJSONObject(i);boolean sunday=LocalDate.parse(day.getString("date")).getDayOfWeek()==DayOfWeek.SUNDAY;assertEquals(sunday,RepeatMarkerPolicy.excluded(day));assertEquals(!sunday,SeatMarks.marked(day,"prestigeAward",false));}assertTrue(Search.sameDates(raw.getJSONArray("dates"),saved.getJSONArray("dates")));
 }
 @Test public void malformedMonthlyDataCannotOverwritePreviousResult()throws Exception{
  for(String change:List.of("route","missing","unknown-mark")){CollectionService s=service();running(s);s.db.save(s.query,snapshot(s.query,1));String before=s.db.data(Search.key(s.query)).getJSONObject("current").toString();JSONObject next=snapshot(s.query,0);if(change.equals("route"))next.put("destination","LHR");if(change.equals("missing"))next.getJSONArray("dates").remove(0);if(change.equals("unknown-mark"))next.getJSONArray("dates").getJSONObject(0).put("status","UNKNOWN");callback(s).onReceiveValue(result(next));assertEquals(before,s.db.data(Search.key(s.query)).getJSONObject("current").toString());assertEquals(0,s.success);assertEquals(0,((ProbeWeb)s.web).loads.size());}
 }
 @Test public void staleCancelledOrReplacedCallbacksCannotSave()throws Exception{
  for(boolean replaced:new boolean[]{false,true}){CollectionService s=service();running(s);ValueCallback<String> callback=callback(s);if(replaced)s.web=new ProbeWeb(s);else{s.running=false;s.token++;}callback.onReceiveValue(result(snapshot(s.query,0)));assertEquals(0,s.index);assertNull(s.db.data(Search.key(s.query)));}
 }
 @Test public void duplicateCallbackCannotSaveOrAdvanceTwice()throws Exception{
  CollectionService s=service();running(s);ValueCallback<String> callback=callback(s);String result=result(snapshot(s.query,0));callback.onReceiveValue(result);callback.onReceiveValue(result);assertEquals(1,s.index);assertEquals(1,s.success);assertEquals(0,((ProbeWeb)s.web).loads.size());
 }
 @Test public void detailAndScheduleStagesAreRemovedFromCollector()throws Exception{
  CollectionService s=service();assertFalse(s.browserUrl("https://www.koreanair.com/flight-status?isSchedule=T"));assertTrue(s.browserUrl(CollectionService.SOURCE));for(var method:CollectionService.class.getDeclaredMethods())assertFalse(method.getName().contains("OperationSchedule"));
 }
 @Test public void sameDirectionNextMonthReusesSuccessfulPageAfterMandatoryGap()throws Exception{
  CollectionService s=service();running(s);JSONObject prior=s.query;JSONObject next=query(YearMonth.parse(prior.getString("month")).plusMonths(1));s.queue.add(next);
  callback(s).onReceiveValue(new JSONObject().put("data",snapshot(prior,0)).put("reuseReady",true).toString());
  assertTrue(s.reuseDocument);assertEquals(Search.key(prior),Search.key(s.lastSuccessfulQuery));
  s.startNextOwned();assertFalse(s.running);assertEquals(0,((ProbeWeb)s.web).loads.size());
  s.nextQueryAt=SystemClock.elapsedRealtime();s.startNextOwned();assertTrue(s.running);assertEquals("reuse",s.stage);assertEquals(0,((ProbeWeb)s.web).loads.size());
 }
 @Test public void coldFallbackIsAllowedOnlyBeforeSubmissionAndOnlyOnce()throws Exception{
  CollectionService s=service();running(s);s.stage="reuse";callback(s).onReceiveValue("{\"cold\":true}");assertEquals("consent",s.stage);assertEquals(1,((ProbeWeb)s.web).loads.size());assertTrue(s.coldFallback);assertFalse(s.reuseDocument);
  s.stage="reuse";callback(s).onReceiveValue("{\"cold\":true}");assertTrue(s.paused);assertEquals(1,((ProbeWeb)s.web).loads.size());
  CollectionService submitted=service();running(submitted);submitted.stage="reuse";submitted.searchSubmitted=true;callback(submitted).onReceiveValue("{\"cold\":true}");assertTrue(submitted.paused);assertEquals(0,((ProbeWeb)submitted.web).loads.size());
 }
 @Test public void reloadOrStageChangeInvalidatesOldResultCallback()throws Exception{
  for(boolean navigation:new boolean[]{false,true}){CollectionService s=service();running(s);ValueCallback<String> old=callback(s);if(navigation){s.documentEpoch++;s.clearPageReuse();}else s.stage="result";old.onReceiveValue(result(snapshot(s.query,0)));assertNull(s.db.data(Search.key(s.query)));assertEquals(0,s.success);}
 }
 @Test public void officialUnavailableItinerarySkipsOnlyCurrentQueryAndRetainsPriorData()throws Exception{
  CollectionService s=service();running(s);s.stage="result";s.db.save(s.query,snapshot(s.query,1));String before=s.db.data(Search.key(s.query)).getJSONObject("current").toString();
  s.regionBatch=new JSONObject().put("key","KE-ICN-test").put("carrier","KE").put("queries",new JSONArray().put(s.query));
  callback(s).onReceiveValue("{\"error\":\"항공사 조회 불가 여정 · 미확인\",\"code\":\"UNSUPPORTED_ITINERARY\"}");
  assertEquals(1,s.index);assertEquals(1,s.failures);assertEquals(0,s.success);assertFalse(s.paused);assertFalse(s.reuseDocument);assertEquals(before,s.db.data(Search.key(s.query)).getJSONObject("current").toString());assertEquals(1,s.regionBatch.getInt("cursor"));assertNotNull(s.db.error(Search.key(s.query)));assertTrue(s.nextQueryAt>SystemClock.elapsedRealtime());
 }
 @Test public void securityErrorCannotSkipToTheNextQuery()throws Exception{
  CollectionService s=service();running(s);s.stage="reuse";s.regionBatch=new JSONObject().put("key","KE-ICN-test").put("queries",new JSONArray().put(s.query));callback(s).onReceiveValue("{\"error\":\"보안 확인 또는 접근 제한\"}");assertTrue(s.paused);assertEquals(0,s.index);assertEquals(0,((ProbeWeb)s.web).loads.size());
 }

}

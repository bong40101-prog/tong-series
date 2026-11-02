package kr.tong.sky;

import android.content.Context;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,packageName="kr.tong.sky",instrumentedPackages={"kr.tong.sky"})
public class RepeatMarkerPolicyTest {
 Context app;
 @Before public void setup()throws Exception{app=RuntimeEnvironment.getApplication();var field=RepeatMarkerPolicy.class.getDeclaredField("catalog");field.setAccessible(true);field.set(null,catalog());}
 JSONObject catalog()throws Exception{return new JSONObject().put("regions",new JSONObject().put("유럽",new JSONArray().put(new JSONObject().put("code","CDG"))).put("미주",new JSONArray().put(new JSONObject().put("code","LAX").put("countryCode","US")).put(new JSONObject().put("code","YVR").put("countryCode","CA"))));}
 JSONObject snapshot(String month)throws Exception{
  JSONObject raw=new JSONObject().put("carrier","KE").put("origin","ICN").put("destination","CDG").put("month",month).put("sourceUpdatedAt","2026-11-02T00:00:00Z").put("collectedAt","2026-11-02T00:05:00Z");JSONArray days=new JSONArray();YearMonth ym=YearMonth.parse(month);for(int n=1;n<=ym.lengthOfMonth();n++){LocalDate date=ym.atDay(n);JSONObject day=new JSONObject().put("date",date.toString()).put("status","OBSERVED");for(String field:Search.ALL_FIELDS)day.put(field,date.getDayOfWeek()==DayOfWeek.SUNDAY&&Set.of("economyAward","prestigeAward","prestigeUpgrade").contains(field));days.put(day);}return raw.put("dates",days);
 }
 JSONObject day(JSONObject snapshot,String date)throws Exception{JSONArray days=snapshot.getJSONArray("dates");for(int i=0;i<days.length();i++)if(date.equals(days.getJSONObject(i).getString("date")))return days.getJSONObject(i);throw new AssertionError(date);}
 @Test public void allFourSundaysExcludeWithoutChangingRawFacts()throws Exception{
  JSONObject raw=snapshot("2027-03");String before=raw.toString();JSONObject view=RepeatMarkerPolicy.apply(raw,raw,catalog());assertEquals(before,raw.toString());assertEquals("[0]",view.getJSONObject("repeatMarkerPolicy").getJSONArray("weekdays").toString());assertEquals(4,view.getJSONObject("repeatMarkerPolicy").getJSONArray("excludedDates").length());assertTrue(day(view,"2027-03-07").getBoolean("prestigeAward"));assertEquals("OBSERVED",day(view,"2027-03-07").getString("status"));assertTrue(RepeatMarkerPolicy.excluded(day(view,"2027-03-07")));assertFalse(RepeatMarkerPolicy.excluded(day(view,"2027-03-15")));
 }
 @Test public void fifthMismatchMakesWholeWeekdayReviewAndRecomputesStaleMetadata()throws Exception{
  JSONObject raw=snapshot("2026-11"),former=RepeatMarkerPolicy.apply(raw,raw,catalog());day(raw,"2026-11-29").put("prestigeAward",false);JSONObject view=RepeatMarkerPolicy.apply(raw,former,catalog());assertEquals(0,view.getJSONObject("repeatMarkerPolicy").getJSONArray("weekdays").length());for(int n:new int[]{1,8,15,22,29}){JSONObject day=day(view,String.format(Locale.ROOT,"2026-11-%02d",n));assertTrue(RepeatMarkerPolicy.review(day));assertFalse(RepeatMarkerPolicy.excluded(day));}assertEquals(5,view.getJSONObject("repeatMarkerPolicy").getJSONArray("reviews").getJSONObject(0).getJSONArray("dates").length());
 }
 @Test public void missingUnknownAndDuplicateDaysNeverEstablishExclusion()throws Exception{
  for(String fault:new String[]{"MISSING","UNKNOWN","DUPLICATE"}){JSONObject raw=snapshot("2027-03");JSONArray dates=raw.getJSONArray("dates");if(fault.equals("MISSING"))dates.remove(27);else if(fault.equals("UNKNOWN"))day(raw,"2027-03-28").put("status","UNKNOWN");else dates.put(new JSONObject(day(raw,"2027-03-28").toString()));JSONObject view=RepeatMarkerPolicy.apply(raw,raw,catalog());assertEquals(0,view.getJSONObject("repeatMarkerPolicy").getJSONArray("weekdays").length());assertTrue(RepeatMarkerPolicy.review(RepeatMarkerPolicy.missingDay(view,LocalDate.parse("2027-03-28"))));assertTrue(SeatMarks.text(28,RepeatMarkerPolicy.missingDay(view,LocalDate.parse("2027-03-28")),0,false,false).toString().contains("확인필요"));}
 }
 @Test public void scopeAndMonthAreIndependent()throws Exception{
  JSONObject raw=snapshot("2027-03");raw.put("destination","LAX");assertEquals("US",RepeatMarkerPolicy.rule(raw,raw,catalog()).getString("scope"));raw.put("destination","YVR");assertEquals("CA",RepeatMarkerPolicy.rule(raw,raw,catalog()).getString("scope"));raw.put("origin","YVR").put("destination","ICN");assertEquals("CA",RepeatMarkerPolicy.rule(raw,raw,catalog()).getString("scope"));raw.put("origin","ICN");raw.put("destination","CDG").put("carrier","OZ");assertNull(RepeatMarkerPolicy.rule(raw,raw,catalog()));JSONObject next=snapshot("2027-04");for(int n=1;n<=30;n++)day(next,String.format(Locale.ROOT,"2027-04-%02d",n)).put("economyAward",false);assertEquals(0,RepeatMarkerPolicy.rule(next,next,catalog()).getJSONArray("evidence").length());
 }
 @Test public void rawTripletDecisionDoesNotUsePerFlightOperatorsOrSeatRows()throws Exception{
  JSONObject raw=snapshot("2027-03").put("flightDetailPolicyVersion",1);JSONArray days=raw.getJSONArray("dates");for(int n=0;n<days.length();n++){JSONObject day=days.getJSONObject(n),row=new JSONObject().put("number","KE5901").put("departureTime","12:00").put("operatorCode","AF");for(String field:Search.ALL_FIELDS)row.put(field,day.getBoolean(field));day.put("flightDetailStatus","CAPTURED").put("flightObservations",new JSONArray().put(row));}assertEquals(4,RepeatMarkerPolicy.rule(raw,raw,catalog()).getJSONArray("candidateDates").length());for(int n=0;n<days.length();n++)days.getJSONObject(n).put("economyAward",false);assertEquals(0,RepeatMarkerPolicy.rule(raw,raw,catalog()).getJSONArray("candidateDates").length());
 }
 @Test public void patternExcludedDatesDoNotReviveAnyConfirmedCodeshareMarkers()throws Exception{
  JSONObject raw=snapshot("2027-03"),view=new JSONObject(raw.toString()),target=day(view,"2027-03-07");JSONArray rows=new JSONArray();for(String code:new String[]{"KE","AF"}){JSONObject row=new JSONObject().put("operationSchema",2).put("operationSource",OperationSchedule.SOURCE).put("operationStatus","OPERATING").put("operatorCode",code).put("operatorName",code.equals("KE")?"대한항공":"에어프랑스").put("serviceKind",code.equals("KE")?"DIRECT":"CODESHARE").put("number",code.equals("KE")?"KE901":"KE5901").put("departureTime","12:00").put("arrivalTime","18:00").put("awardDataStatus","CAPTURED");for(String field:Search.ALL_FIELDS)row.put(field,code.equals("KE")||field.equals("firstCombined"));rows.put(row);}target.put("flights",rows);JSONObject projected=RepeatMarkerPolicy.apply(raw,view,catalog()),day=day(projected,"2027-03-07");assertTrue(RepeatMarkerPolicy.excluded(day));assertFalse(SeatMarks.verified(day,false));assertFalse(SeatMarks.marked(day,"prestigeAward",false));assertFalse(SeatMarks.marked(day,"firstCombined",false));assertEquals("",SeatMarks.serviceDescription(day));
 }
 @Test public void sharedSnapshotAnnotationsAndRawObservationClocksRemainIntact()throws Exception{
  JSONObject raw=snapshot("2027-03"),stored=OperationPolicy.EMPTY.snapshot(raw);assertTrue(stored.has("repeatMarkerPolicy"));assertEquals(raw.getString("collectedAt"),stored.getString("collectedAt"));assertEquals(raw.getString("sourceUpdatedAt"),stored.getString("sourceUpdatedAt"));assertTrue(day(stored,"2027-03-07").has("repeatMarkerExclusion"));assertTrue(day(stored,"2027-03-07").getBoolean("economyAward"));
 }
 @Test public void actualParisDecemberMixedSundaysRetainOriginalMarksAndUnknown13()throws Exception{
  JSONObject raw;try(var stream=getClass().getResourceAsStream("/paris-december.json")){raw=new JSONObject(new String(stream.readAllBytes(),StandardCharsets.UTF_8));}String before=raw.toString();JSONObject operation=OperationPolicy.EMPTY.snapshot(raw),view=RouteWeekdayFilter.apply(operation,TimetableReference.load(app));int award=0,upgrade=0;
  for(int n:new int[]{6,13,20,27}){JSONObject day=day(view,String.format(Locale.ROOT,"2026-12-%02d",n));assertTrue(RepeatMarkerPolicy.review(day));assertFalse(RepeatMarkerPolicy.excluded(day));if(SeatMarks.marked(day,"prestigeAward",false))award++;if(SeatMarks.marked(day,"prestigeUpgrade",false))upgrade++;if(n==13){assertNull(SeatMarks.reviewFlags(day));assertFalse(SeatMarks.available(day,0,false));}else{assertNotNull(SeatMarks.reviewFlags(day));assertTrue(SeatMarks.marked(day,"economyAward",false));assertTrue(SeatMarks.description(day,0,false).contains("저장"));}}
  assertEquals(2,award);assertEquals(2,upgrade);assertEquals(before,raw.toString());
 }
 @Test public void originalCalendarSourceWinsOverOperationProjectionButGenuineSourceNoFlightStaysOff()throws Exception{
  JSONObject raw=snapshot("2027-03");day(raw,"2027-03-28").put("status","UNKNOWN");JSONObject source=new JSONObject(day(raw,"2027-03-07").toString());day(raw,"2027-03-07").put("legacyAwardFlags",source).put("status","NO_FLIGHT");day(raw,"2027-03-14").put("status","NO_FLIGHT");JSONObject view=RepeatMarkerPolicy.calendar(raw,catalog());assertTrue(SeatMarks.available(day(view,"2027-03-07"),0,false));assertFalse(SeatMarks.available(day(view,"2027-03-14"),0,false));assertEquals("NO_FLIGHT",day(view,"2027-03-14").getString("status"));
 }
 @Test public void v2FactsIgnoreChangedFlightAuditAndNeverPreferWeeklyRequeryMetadata()throws Exception{
  JSONObject first=snapshot("2027-03"),second=new JSONObject(first.toString());day(first,"2027-03-07").put("flightDetailStatus","CAPTURED").put("flightObservations",new JSONArray().put(new JSONObject().put("number","KE901").put("prestigeAward",true)));day(second,"2027-03-07").put("flightDetailStatus","UNKNOWN").put("operationRequiresRequery",true).put("flightObservations",new JSONArray());assertTrue(OperationPolicy.EMPTY.sameFacts(first,second));assertFalse(OperationPolicy.EMPTY.resolvesRequery(first,second));assertFalse(OperationPolicy.requiresRequery(second));JSONObject pair=OperationPolicy.EMPTY.reconcile(new JSONObject().put("current",first).put("success",1),new JSONObject().put("current",second).put("success",2)).first();for(int i=0;i<first.getJSONArray("dates").length();i++){JSONObject raw=first.getJSONArray("dates").getJSONObject(i),saved=pair.getJSONObject("current").getJSONArray("dates").getJSONObject(i);assertEquals(raw.getString("status"),saved.getString("status"));for(String field:Search.ALL_FIELDS)assertEquals(raw.getBoolean(field),saved.getBoolean(field));}assertEquals(first.getString("sourceUpdatedAt"),pair.getJSONObject("current").getString("sourceUpdatedAt"));
 }
 @Test public void validV2MonthlyCalendarReusesTodayWithoutAnyWeekOrOperatorProof()throws Exception{
  JSONObject current=snapshot("2027-03");day(current,"2027-03-07").put("operationRequiresRequery",true);long success=Instant.parse("2026-11-02T00:30:00Z").toEpochMilli(),now=Instant.parse("2026-11-02T01:00:00Z").toEpochMilli();JSONObject payload=new JSONObject().put("current",current).put("success",success);assertTrue(SharedCache.collectionFresh(payload,"KE",now,OperationPolicy.EMPTY));assertTrue(SharedCache.fresh(payload,now));assertFalse(SharedCache.collectionFresh(payload,"KE",SharedCache.expiresAt(success),OperationPolicy.EMPTY));
 }
 @Test public void actualParisFebruaryAllSundaysIncluding7HaveNoMarkersOrCodeshareRevival()throws Exception{
  JSONObject raw;try(var stream=getClass().getResourceAsStream("/paris-february.json")){raw=new JSONObject(new String(stream.readAllBytes(),StandardCharsets.UTF_8));}String before=raw.toString();JSONObject view=RouteWeekdayFilter.apply(raw);for(int n:new int[]{7,14,21,28}){JSONObject day=day(view,String.format(Locale.ROOT,"2027-02-%02d",n));assertTrue(RepeatMarkerPolicy.excluded(day));for(String field:Search.ALL_FIELDS){assertFalse(SeatMarks.marked(day,field,false));assertFalse(day.getBoolean(field));}assertFalse(SeatMarks.available(day,0,false));assertEquals("",SeatMarks.serviceDescription(day));}assertEquals(before,raw.toString());
 }
 @Test public void actualParisMarchHasFourExcludedSundaysAndPreserves15And18Upgrade()throws Exception{
  JSONObject raw;try(var stream=getClass().getResourceAsStream("/paris-march.json")){raw=new JSONObject(new String(stream.readAllBytes(),StandardCharsets.UTF_8));}String before=raw.toString();TimetableReference timetable=TimetableReference.load(app);JSONObject operation=OperationPolicy.EMPTY.snapshot(raw),view=RouteWeekdayFilter.apply(operation,timetable);assertEquals(before,raw.toString());assertEquals("[0]",view.getJSONObject("repeatMarkerPolicy").getJSONArray("weekdays").toString());for(int n:new int[]{7,14,21,28})assertFalse(SeatMarks.available(day(view,String.format(Locale.ROOT,"2027-03-%02d",n)),0,false));for(int n:new int[]{15,18}){JSONObject day=day(view,"2027-03-"+n);assertFalse(RepeatMarkerPolicy.excluded(day));assertNotNull("Original calendar must preserve upgrade "+n+": "+day,SeatMarks.calendarFlags(day));assertTrue(SeatMarks.marked(day,"prestigeUpgrade",false));}
 }
}

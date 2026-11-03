package kr.tong.sky;
import org.json.*;import org.junit.*;import static org.junit.Assert.*;import java.time.*;import java.nio.file.*;
@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner.class)
@org.robolectric.annotation.Config(manifest=org.robolectric.annotation.Config.NONE,packageName="kr.tong.sky",sdk=35)
public class RouteNetworkAuditTest {
 final LocalDate today=LocalDate.of(2026,11,3),limit=LocalDate.of(2027,10,28);
 JSONObject q(String o,String d,String m)throws Exception{return new JSONObject().put("origin",o).put("destination",d).put("month",m);}
 @Test public void suspensionDoesNotExpireAtFormerCalendarCutoffInEitherDirection()throws Exception{
  int checked=0;for(String d:new String[]{"VVO","SVO","LED","IKT","DXB"})for(YearMonth m=YearMonth.of(2026,11);!m.isAfter(YearMonth.of(2027,10));m=m.plusMonths(1))for(String[] pair:new String[][]{{"ICN",d},{d,"ICN"}}){assertFalse(d+" "+m,RouteRules.allowed("KE",q(pair[0],pair[1],m.toString()),today,limit));checked++;}assertEquals(120,checked);
  assertFalse(RouteRules.allowed("KE",q("VVO","ICN","2027-04"),LocalDate.of(2027,4,1),limit));
 }
 @Test public void historicalSnapshotAndOtherOriginPairsDoNotBecomeGlobalRestrictions()throws Exception{
  assertTrue(RouteRules.allowed("KE",q("ICN","VVO","2026-12"),LocalDate.of(2026,11,2),limit));
  for(String d:new String[]{"VVO","SVO","LED","IKT","DXB"})assertTrue(RouteRules.allowed("KE",q("LHR",d,"2027-01"),today,limit));
  assertTrue(RouteRules.allowed("OZ",q("ICN","DXB","2027-01"),today,limit));
 }
 @Test public void melbourneOnlyCollectsMonthsOverlappingPublishedSeasonInBothDirections()throws Exception{
  for(String[] pair:new String[][]{{"ICN","MEL"},{"MEL","ICN"}})for(YearMonth m=YearMonth.of(2026,11);!m.isAfter(YearMonth.of(2027,10));m=m.plusMonths(1)){
   boolean expected=!m.isBefore(YearMonth.of(2026,12))&&!m.isAfter(YearMonth.of(2027,3));assertEquals(m.toString(),expected,RouteRules.allowed("KE",q(pair[0],pair[1],m.toString()),today,limit));
  }
 }
 @Test public void melbournePartialMonthUsesActualClippedDateWindow()throws Exception{
  assertFalse(RouteRules.allowed("KE",q("ICN","MEL","2026-12"),today,LocalDate.of(2026,12,17)));
  assertTrue(RouteRules.allowed("KE",q("ICN","MEL","2026-12"),today,LocalDate.of(2026,12,18)));
  assertTrue(RouteRules.allowed("KE",q("MEL","ICN","2027-03"),LocalDate.of(2027,3,14),limit));
  assertFalse(RouteRules.allowed("KE",q("MEL","ICN","2027-03"),LocalDate.of(2027,3,15),limit));
 }
 @Test public void seasonalUncertainAndAsianaTransferRoutesAreNotBlanketBlacklisted()throws Exception{
  for(String d:new String[]{"ZRH","CRK","BKI","TAG","CMB","ALA","TLV"})assertTrue(d,RouteRules.allowed("KE",q("ICN",d,"2027-04"),today,limit));
  for(String d:new String[]{"LHR","MXP","ORD","CNX","HNL","UBN"})assertTrue(d,RouteRules.allowed("KE",q("ICN",d,"2027-01"),today,limit));
 }
 @Test public void queuedSuspensionsMapCursorPreserveOrderAndDoNotMutateSavedInput()throws Exception{
  JSONArray qs=new JSONArray().put(q("ICN","VVO","2026-12")).put(q("ICN","UBN","2026-12")).put(q("DXB","ICN","2027-01")).put(q("MEL","ICN","2027-04")).put(q("LHR","ICN","2027-04"));
  JSONObject old=new JSONObject().put("carrier","KE").put("origin","ICN").put("cursor",3).put("failures",2).put("completedAt",99).put("queries",qs).put("error","original diagnostic");String before=old.toString();JSONObject kept=RouteRules.filterBatch(old,"KE",today,limit);
  assertEquals(2,kept.getJSONArray("queries").length());assertEquals(1,kept.getInt("cursor"));assertEquals(1,kept.getInt("failures"));assertEquals(0,kept.getLong("completedAt"));assertEquals("ICN-UBN-2026-12",Search.key(kept.getJSONArray("queries").getJSONObject(0)));assertEquals("LHR-ICN-2027-04",Search.key(kept.getJSONArray("queries").getJSONObject(1)));assertEquals(before,old.toString());assertEquals("original diagnostic",kept.getString("error"));
 }
 @Test public void allCompletedFilteredQueueKeepsCompletionClock()throws Exception{
  JSONObject old=new JSONObject().put("carrier","KE").put("origin","ICN").put("cursor",2).put("completedAt",99).put("queries",new JSONArray().put(q("ICN","VVO","2027-04")).put(q("ICN","UBN","2027-04")));JSONObject kept=RouteRules.filterBatch(old,"KE",today,limit);assertEquals(1,kept.getInt("cursor"));assertEquals(99,kept.getLong("completedAt"));
 }
 @Test public void fullPlanRemoves124UnsupportedUnitsAndKeepsEachPublishedMelbourneMonth()throws Exception{
  Path assets=Path.of(System.getProperty("tongsky.assets"));JSONObject catalog=new JSONObject(Files.readString(assets.resolve("regions.json")));int count=0,mel=0;
  for(String region:ScanSchedule.REGIONS){JSONArray qs=RegionBatch.create("ICN",region,catalog,today,1000,limit,"KE").getJSONArray("queries");count+=qs.length();for(int i=0;i<qs.length();i++){JSONObject q=qs.getJSONObject(i);String d="ICN".equals(q.getString("origin"))?q.getString("destination"):q.getString("origin");assertFalse(java.util.Set.of("VVO","SVO","LED","IKT","DXB").contains(d));if(d.equals("MEL"))mel++;}}
  assertEquals(1314,count);assertEquals(8,mel);
 }
}

package kr.tong.sky;
import org.json.*;import org.junit.*;import static org.junit.Assert.*;import java.time.*;import java.nio.file.*;
@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner.class)
@org.robolectric.annotation.Config(manifest=org.robolectric.annotation.Config.NONE,packageName="kr.tong.sky",sdk=35)
public class CollectionEligibilityTest {
 JSONObject q(String o,String d,String m)throws Exception{return new JSONObject().put("origin",o).put("destination",d).put("month",m);}
 @Test public void all24ReportedAsianaChiangMaiQueriesAreExcludedWithoutExcludingKoreanAir()throws Exception{
  LocalDate today=LocalDate.of(2026,11,3),limit=Search.lastDate(true,today);int count=0;
  for(YearMonth month=YearMonth.of(2026,11);!month.isAfter(YearMonth.of(2027,10));month=month.plusMonths(1))for(String[] pair:new String[][]{{"ICN","CNX"},{"CNX","ICN"}}){JSONObject q=q(pair[0],pair[1],month.toString());assertFalse(RouteRules.allowed("OZ",q,today,limit));assertTrue(RouteRules.allowed("KE",q,today,limit));count++;}assertEquals(24,count);
 }
 @Test public void existingQueuedWorkMapsCursorWithoutChangingOriginalOrDeletingOtherRoutes()throws Exception{
  JSONObject old=new JSONObject().put("carrier","OZ").put("origin","ICN").put("region","동남아시아/서남아시아").put("cursor",3).put("failures",1).put("queries",new JSONArray().put(q("ICN","BKK","2026-11")).put(q("ICN","CNX","2026-11")).put(q("BKK","ICN","2026-11")).put(q("CNX","ICN","2026-11")).put(q("ICN","SIN","2026-11")));String before=old.toString();LocalDate day=LocalDate.of(2026,11,3);JSONObject filtered=RouteRules.filterBatch(old,"OZ",day,Search.lastDate(true,day));assertEquals(3,filtered.getJSONArray("queries").length());assertEquals(2,filtered.getInt("cursor"));assertEquals(before,old.toString());
 }
 @Test public void savedChiangMaiSearchRemainsAvailableWhileManualAndScheduledCollectionSkipIt()throws Exception{
  JSONObject search=new JSONObject().put("carrier","OZ").put("origin","ICN").put("destination","CNX").put("start",Search.today().toString()).put("end",Search.today().toString());assertFalse(Search.queries(search).isEmpty());assertTrue(Search.collectionQueries(search).isEmpty());assertEquals(0,AsianaData.plan("ICN","CNX",YearMonth.from(Search.today()).toString(),true,true).length());
  Path assets=Path.of(System.getProperty("tongsky.assets"));JSONObject ke=new JSONObject(Files.readString(assets.resolve("regions.json"))),oz=new JSONObject(Files.readString(assets.resolve("asiana-regions.json")));boolean retained=false;JSONArray cities=oz.getJSONObject("regions").getJSONArray("동남아시아/서남아시아");for(int i=0;i<cities.length();i++)retained|="CNX".equals(cities.getJSONObject(i).getString("code"));assertTrue(retained);
  JSONObject config=new JSONObject().put("enabled",true).put("hour",9).put("minute",0).put("origin","ICN").put("KE",new JSONArray()).put("OZ",new JSONArray(ScanSchedule.REGIONS));JSONArray batches=ScanSchedule.batches(config,ke,oz,LocalDate.of(2026,11,3),1000);int total=0;for(int i=0;i<batches.length();i++){JSONArray qs=batches.getJSONObject(i).getJSONArray("queries");total+=qs.length();for(int j=0;j<qs.length();j++){JSONObject query=qs.getJSONObject(j);assertFalse("CNX".equals(query.getString("origin"))||"CNX".equals(query.getString("destination")));}}assertEquals(528,total);
 }
}

package kr.tong.sky;

import org.json.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class PageReuseTest {
 JSONObject query(String carrier,String origin,String destination,String month)throws Exception{return new JSONObject().put("carrier",carrier).put("origin",origin).put("destination",destination).put("month",month);}
 JSONObject q(String origin,String destination,String month)throws Exception{return query("OZ",origin,destination,month);}
 @Test public void onlySuccessfulSameDocumentAndNextMonthSameDirectionAreCandidates()throws Exception{
  JSONObject prior=q("ICN","CDG","2026-11"),next=q("ICN","CDG","2026-12");
  assertEquals("reuse",PageReuse.stage("OZ",prior,next,true,true));
  assertEquals("cold",PageReuse.stage("OZ",prior,next,false,true));assertEquals("cold",PageReuse.stage("OZ",prior,next,true,false));assertEquals("cold",PageReuse.stage("OZ",null,next,true,true));
  assertEquals("cold",PageReuse.stage("OZ",prior,q("CDG","ICN","2026-12"),true,true));assertEquals("cold",PageReuse.stage("OZ",prior,q("ICN","LAX","2026-12"),true,true));
  assertEquals("cold",PageReuse.stage("OZ",prior,q("ICN","CDG","2026-11"),true,true));assertEquals("cold",PageReuse.stage("OZ",prior,q("ICN","CDG","2027-01"),true,true));
 }
 @Test public void yearBoundaryIsConsecutiveButWrongCarrierAndMalformedScopeStayCold()throws Exception{
  assertEquals("reuse",PageReuse.stage("OZ",q("ICN","CDG","2026-12"),q("ICN","CDG","2027-01"),true,true));
  assertEquals("cold",PageReuse.stage("OZ",q("ICN","CDG","2026-12"),query("KE","ICN","CDG","2027-01"),true,true));
  assertEquals("cold",PageReuse.stage("OZ",q("ICN","CDG","bad"),q("ICN","CDG","2027-01"),true,true));
  assertEquals("cold",PageReuse.stage("OZ",q("ICN","ICN","2026-12"),q("ICN","ICN","2027-01"),true,true));
  assertEquals("reuse",PageReuse.stage("KE",query("KE","ICN","CDG","2026-11"),query("KE","ICN","CDG","2026-12"),true,true));
 }
 @Test public void pendingGroupingPreservesCompletedPrefixCursorMetadataAndFullMultiset()throws Exception{
  JSONArray queries=new JSONArray().put(q("ICN","LAX","2026-11")).put(q("LAX","ICN","2026-11")).put(q("ICN","LAX","2027-01")).put(q("LAX","ICN","2026-12")).put(q("ICN","LAX","2026-12")).put(q("ICN","CDG","2026-12")).put(q("LAX","ICN","2027-01"));
  queries.getJSONObject(4).put("futureField",new JSONObject().put("keep",true));
  JSONObject batch=new JSONObject().put("carrier","OZ").put("queries",queries).put("cursor",2).put("failures",1).put("completedAt",0).put("error","prior diagnostic").put("futureMetadata",17);String before=batch.toString();
  JSONObject grouped=PageReuse.orderRemaining(batch);JSONArray out=grouped.getJSONArray("queries");assertEquals(queries.length(),out.length());assertEquals(2,grouped.getInt("cursor"));assertEquals(1,grouped.getInt("failures"));assertEquals("prior diagnostic",grouped.getString("error"));assertEquals(17,grouped.getInt("futureMetadata"));
  assertEquals(queries.getJSONObject(0).toString(),out.getJSONObject(0).toString());assertEquals(queries.getJSONObject(1).toString(),out.getJSONObject(1).toString());
  assertEquals("2026-12",out.getJSONObject(2).getString("month"));assertEquals("2027-01",out.getJSONObject(3).getString("month"));assertEquals("LAX",out.getJSONObject(4).getString("origin"));assertEquals("2026-12",out.getJSONObject(4).getString("month"));
  assertTrue(out.getJSONObject(2).getJSONObject("futureField").getBoolean("keep"));List<String> oldValues=new ArrayList<>(),newValues=new ArrayList<>();for(int i=0;i<queries.length();i++){oldValues.add(queries.getJSONObject(i).toString());newValues.add(out.getJSONObject(i).toString());}Collections.sort(oldValues);Collections.sort(newValues);assertEquals(oldValues,newValues);assertEquals(before,batch.toString());
 }
 @Test public void completedClockIsPreservedAndPendingKoreanAirMonthsAreGrouped()throws Exception{
  JSONObject complete=new JSONObject().put("carrier","OZ").put("cursor",2).put("completedAt",123).put("queries",new JSONArray().put(q("ICN","LAX","2026-12")).put(q("LAX","ICN","2026-11")));assertEquals(complete.toString(),PageReuse.orderRemaining(complete).toString());
  JSONObject ke=new JSONObject().put("carrier","KE").put("cursor",0).put("queries",new JSONArray().put(query("KE","ICN","LAX","2027-01")).put(query("KE","LAX","ICN","2026-12")).put(query("KE","ICN","LAX","2026-12")));JSONArray sorted=PageReuse.orderRemaining(ke).getJSONArray("queries");assertEquals("2026-12",sorted.getJSONObject(0).getString("month"));assertEquals("2027-01",sorted.getJSONObject(1).getString("month"));assertEquals("LAX",sorted.getJSONObject(2).getString("origin"));
 }
 @Test public void duplicatePendingItemsArePreservedAndForeignCarrierIsRejected()throws Exception{
  JSONObject item=q("ICN","LAX","2026-12");JSONObject batch=new JSONObject().put("carrier","OZ").put("cursor",0).put("queries",new JSONArray().put(item).put(new JSONObject(item.toString())));assertEquals(2,PageReuse.orderRemaining(batch).getJSONArray("queries").length());
  batch.getJSONArray("queries").put(query("KE","ICN","LAX","2026-12"));try{PageReuse.orderRemaining(batch);fail();}catch(JSONException expected){assertTrue(expected.getMessage().contains("항공사"));}
 }
}

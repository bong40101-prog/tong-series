package kr.tong.sky;

import org.json.*;
import java.time.YearMonth;
import java.util.*;

/** Query scheduling only. It never changes snapshots, freshness, routes or limits. */
final class PageReuse {
 private PageReuse(){}
 // Both carriers reuse only the same direction's next consecutive month.
 // The adapters independently verify the visible result has actually changed.
 static boolean supported(String carrier){return "OZ".equals(carrier)||"KE".equals(carrier);}
 static String stage(String carrier,JSONObject previous,JSONObject next,boolean allowedURL,boolean successfulDoc){
  if(!supported(carrier)||!allowedURL||!successfulDoc||previous==null||next==null)return "cold";
  try{
   if(!queryCarrier(previous,carrier)||!queryCarrier(next,carrier))return "cold";
   String origin=previous.getString("origin"),destination=previous.getString("destination");
   if(!origin.matches("[A-Z]{3}")||!destination.matches("[A-Z]{3}")||origin.equals(destination)||!origin.equals(next.getString("origin"))||!destination.equals(next.getString("destination")))return "cold";
   YearMonth prior=YearMonth.parse(previous.getString("month")),target=YearMonth.parse(next.getString("month"));
   return target.equals(prior.plusMonths(1))?"reuse":"cold";
  }catch(Exception invalid){return "cold";}
 }
 private static boolean queryCarrier(JSONObject query,String carrier){return carrier.equals(query.optString("carrier",carrier));}
 static JSONObject orderRemaining(JSONObject batch)throws Exception{
  JSONObject result=new JSONObject(batch.toString());String carrier=result.optString("carrier","KE");
  if(!supported(carrier))return result;
  JSONArray original=result.getJSONArray("queries"),ordered=new JSONArray();
  int cursor=Math.max(0,Math.min(original.length(),result.optInt("cursor")));
  // The completed prefix and its cursor are immutable. Only not-yet-run work
  // is grouped; stable ordering keeps the first pending route's priority.
  for(int i=0;i<cursor;i++)ordered.put(original.getJSONObject(i));
  LinkedHashMap<String,List<JSONObject>> groups=new LinkedHashMap<>();
  for(int i=cursor;i<original.length();i++){
   JSONObject query=original.getJSONObject(i);if(!queryCarrier(query,carrier))throw new JSONException("항공사가 다른 조회 항목입니다.");
   String origin=query.getString("origin"),destination=query.getString("destination");
   if(!origin.matches("[A-Z]{3}")||!destination.matches("[A-Z]{3}")||origin.equals(destination))throw new JSONException("잘못된 조회 노선입니다.");
   YearMonth.parse(query.getString("month"));groups.computeIfAbsent(origin+"-"+destination,key->new ArrayList<>()).add(query);
  }
  for(List<JSONObject> group:groups.values()){
   group.sort(Comparator.comparing(query->YearMonth.parse(query.optString("month"))));
   for(JSONObject query:group)ordered.put(query);
  }
  result.put("queries",ordered);return result;
 }
}

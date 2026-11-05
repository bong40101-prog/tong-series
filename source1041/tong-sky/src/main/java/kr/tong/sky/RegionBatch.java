package kr.tong.sky;
import org.json.*;import java.time.*;import java.util.*;import android.content.*;import java.nio.charset.StandardCharsets;
final class RegionBatch {
 static JSONObject catalog(Context c)throws Exception{return RegionScope.filteredCatalog(c,false);}
 static String key(String origin,String region){return origin+"-"+region;}
 static long remaining(JSONObject b,long now){long completed=b==null?0:b.optLong("completedAt");return completed<=0||completed>now?0:Math.max(0,SharedCache.expiresAt(completed)-now);}
 static JSONObject create(String origin,String region,JSONObject catalog,LocalDate today,long now)throws Exception{return create(origin,region,catalog,today,now,Search.lastDate(false,Instant.ofEpochMilli(now).atZone(Search.KST)),"KE");}
 static JSONObject create(String origin,String region,JSONObject catalog,LocalDate today,long now,LocalDate limit)throws Exception{return create(origin,region,catalog,today,now,limit,"KE");}
 static JSONObject create(String origin,String region,JSONObject catalog,LocalDate today,long now,LocalDate limit,String carrier)throws Exception{
  carrier=RouteRules.carrier(carrier);if(today.isAfter(limit))throw new Exception("현재 조회 가능 기간이 종료됐습니다.");if(!origin.matches("[A-Z]{3}"))throw new Exception("출발 공항을 선택하세요.");
  JSONArray airports=RegionScope.excludedRegion(region)?new JSONArray():catalog.getJSONObject("regions").getJSONArray(region),queries=new JSONArray();YearMonth first=YearMonth.from(today),last=YearMonth.from(limit);
  for(int i=0;i<airports.length();i++){String dest=airports.getJSONObject(i).getString("code");if(dest.equals(origin))continue;for(YearMonth month=first;!month.isAfter(last);month=month.plusMonths(1)){
   for(String[] pair:new String[][]{{origin,dest},{dest,origin}}){JSONObject q=new JSONObject().put("origin",pair[0]).put("destination",pair[1]).put("month",month.toString()).put("carrier",carrier).put("scopeOrigin",origin);if(RouteRules.allowed(carrier,q,today,limit))queries.put(q);}
  }}
  return new JSONObject().put("key",("OZ".equals(carrier)?"OZ-":"")+key(origin,region)).put("carrier",carrier).put("origin",origin).put("region",region).put("startedAt",now).put("completedAt",0).put("cursor",0).put("queries",queries).put("error","");
 }
 static JSONArray all(String origin,JSONObject catalog,LocalDate today,long now)throws Exception{
  JSONArray batches=new JSONArray();LinkedHashSet<String> ordered=new LinkedHashSet<>(Arrays.asList("유럽","미주","동남아시아/서남아시아","동북아시아","대양주/괌","러시아/몽골/중앙아시아","중동/아프리카","대한민국"));catalog.getJSONObject("regions").keys().forEachRemaining(ordered::add);Iterator<String> names=ordered.iterator();Set<String> seen=new HashSet<>();
  while(names.hasNext()){String region=names.next();if(RegionScope.excludedRegion(region)||!catalog.getJSONObject("regions").has(region))continue;JSONObject b=create(origin,region,catalog,today,now);JSONArray unique=new JSONArray(),qs=b.getJSONArray("queries");for(int i=0;i<qs.length();i++){JSONObject q=qs.getJSONObject(i);if(seen.add(Search.key(q)))unique.put(q);}if(unique.length()>0)batches.put(b.put("queries",unique));}
  return batches;
 }
 static boolean complete(JSONObject b){return b!=null&&b.optJSONArray("queries")!=null&&b.optInt("cursor")>=b.optJSONArray("queries").length();}
}

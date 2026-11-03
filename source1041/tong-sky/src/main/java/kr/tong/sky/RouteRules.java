package kr.tong.sky;

import org.json.*;
import java.time.*;
import java.util.*;

/** Current official carrier network restrictions; saved calendars are never removed. */
final class RouteRules {
 static final String CHECKED_AT="2026-11-03";
 // CNX: official /C/KR/KO/booking/route?airportCd=CNX reports no Asiana routes.
 private static final Set<String> OZ_UNSERVED=Set.of("BUD","CKG","DAD","SEA","ALA","TAS","CNX");
 private static final Set<String> OZ_ICN_UNLINKED=Set.of("GMP","CJU","CJJ","KWJ","TAE","RSU","SHA");
 // Reciprocal official route maps checked 2026-10-31, limited to the app's
 // existing Korean Air horizon. Transfer-only PUS/TAE are not standalone awards.
 private static final Set<String> KE_ICN_UNLINKED=Set.of("ZRH","KKJ","NGB","TAK","SHI","SHA","URC","WUX","WEH","ISG","HIJ","TAG","BKI","CMB","CRK","MEL","SVO","VVO","LED","ALA","IKT","DXB","TLV","GMP","PUS","CJU","KWJ","KUV","TAE","RSU","USN","WJU","HIN","CJJ","KPO");
 private static final LocalDate KE_NETWORK_FROM=LocalDate.of(2026,10,30),KE_NETWORK_UNTIL=LocalDate.of(2026,12,17);
 private static final LocalDate KE_HIJ_START=LocalDate.of(2026,12,23);
 // Asiana's 2026-06-17 notice suspends OZ232/231 from 2026-07-01,
 // with no announced end. This applies only to its ICN-HNL pair.
 private static final LocalDate OZ_HNL_SUSPENDED_FROM=LocalDate.of(2026,7,1);

 static String carrier(String value){String result=value==null||value.isEmpty()?"KE":value;if(!"KE".equals(result)&&!"OZ".equals(result))throw new IllegalArgumentException("알 수 없는 항공사입니다.");return result;}
 static boolean allowed(String carrier,JSONObject query,LocalDate today,LocalDate limit)throws Exception{return reason(carrier,query,today,limit).isEmpty();}
 static String reason(String carrier,JSONObject query,LocalDate today,LocalDate limit)throws Exception{
  carrier=carrier(carrier);String o=query.getString("origin"),d=query.getString("destination");YearMonth month=YearMonth.parse(query.getString("month"));
  if(!o.matches("[A-Z]{3}")||!d.matches("[A-Z]{3}")||o.equals(d))return "잘못된 노선";
  if(!RegionScope.allowed(query))return "동북아·한국 노선 조회 제외";
  LocalDate first=month.atDay(1).isBefore(today)?today:month.atDay(1),last=month.atEndOfMonth().isAfter(limit)?limit:month.atEndOfMonth();
  if(first.isAfter(last))return "조회 가능 기간 밖";
  if("OZ".equals(carrier)){
   if(("ICN".equals(o)&&"HNL".equals(d)||"HNL".equals(o)&&"ICN".equals(d))&&!month.atDay(1).isBefore(OZ_HNL_SUSPENDED_FROM))return "공식 운항 중단(2026-07-01~)";
   if(OZ_UNSERVED.contains(o)||OZ_UNSERVED.contains(d))return "공식 미취항지";
   if(icnOther(o,d,OZ_ICN_UNLINKED))return "공식 인천 운항 조합 없음";
  }else{
   if(("ICN".equals(o)&&"HIJ".equals(d)||"HIJ".equals(o)&&"ICN".equals(d))&&last.isBefore(KE_HIJ_START))return "조회 기간 이후 취항";
   if(!today.isBefore(KE_NETWORK_FROM)&&!last.isAfter(KE_NETWORK_UNTIL)&&icnOther(o,d,KE_ICN_UNLINKED))return "현재 공식 인천 직항 노선도 미포함";
  }
  return "";
 }
 private static boolean icnOther(String o,String d,Set<String> airports){return "ICN".equals(o)&&airports.contains(d)||"ICN".equals(d)&&airports.contains(o);}

 /** Normalize a queued pre-update batch before counts, leases, or requests are made. */
 static JSONObject filterBatch(JSONObject batch,String carrier,LocalDate today,LocalDate limit)throws Exception{
  carrier=carrier(carrier);String oldCarrier=batch.optString("carrier");if(!oldCarrier.isEmpty()&&!carrier.equals(oldCarrier))throw new Exception("항공사가 다른 수집 목록입니다.");
  JSONObject result=new JSONObject(batch.toString());JSONArray old=batch.getJSONArray("queries"),kept=new JSONArray();int oldCursor=Math.max(0,Math.min(old.length(),batch.optInt("cursor"))),cursor=0;
  for(int i=0;i<old.length();i++){JSONObject q=new JSONObject(old.getJSONObject(i).toString());String qc=q.optString("carrier");if(!qc.isEmpty()&&!carrier.equals(qc))throw new Exception("항공사가 다른 조회 항목입니다.");if(RegionScope.excludedRegion(batch.optString("region")))continue;q.put("scopeOrigin",batch.optString("origin"));if(allowed(carrier,q,today,limit)){kept.put(q.put("carrier",carrier));if(i<oldCursor)cursor++;}}
  result.put("carrier",carrier).put("queries",kept).put("cursor",cursor).put("failures",Math.min(Math.max(0,batch.optInt("failures")),cursor));
  if(cursor<kept.length())result.put("completedAt",0);
  return result;
 }
}

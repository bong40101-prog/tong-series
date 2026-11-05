package kr.tong.sky;

import org.json.*;
import java.time.*;
import java.util.*;

final class Search {
 static final String[] CABINS={"일반석 보너스","프리미엄석 보너스","프레스티지석 보너스"};
 static final String[] FIELDS={"economyAward","premiumAward","prestigeAward"};
 static final String[] ALL_FIELDS={"economyAward","premiumAward","prestigeAward","premiumUpgrade","prestigeUpgrade","firstCombined"};
 static final ZoneId KST=ZoneId.of("Asia/Seoul");
 static LocalDate today(){return LocalDate.now(KST);}
 static List<JSONObject> collectionQueries(JSONObject s)throws Exception{List<JSONObject> planned=new ArrayList<>(),out=new ArrayList<>();String carrier=RouteRules.carrier(s.optString("carrier"));LocalDate today=today(),limit=lastDate("OZ".equals(carrier));if(!RegionScope.allowed(s,s.optString("origin")))return out;String start=today.toString(),end=limit.toString();add(planned,s.getString("origin"),s.getString("destination"),start,end,limit,today);if(s.optBoolean("round"))add(planned,s.getString("destination"),s.getString("origin"),start,end,limit,today);for(JSONObject q:planned)if(RouteRules.allowed(carrier,q.put("scopeOrigin",s.optString("origin")),today,limit))out.add(q.put("carrier",carrier));return out;}
 static LocalDate lastDate(boolean oz){return lastDate(oz,ZonedDateTime.now(KST));}
 // The final Korean Air day opens at 09:00 KST, not midnight.
 static LocalDate lastDate(boolean oz,ZonedDateTime instant){ZonedDateTime now=instant.withZoneSameInstant(KST);return now.toLocalDate().plusDays(oz||now.toLocalTime().isBefore(LocalTime.of(9,0))?359:360);}
 // Korean Air's booking horizon is 360 days in advance; OZ keeps its existing
 // inclusive 360-day window. A public award result still needs validation.
 static LocalDate lastDate(boolean oz,LocalDate today){return today.plusDays(oz?359:360);}
 static String key(JSONObject q){return q.optString("origin")+"-"+q.optString("destination")+"-"+q.optString("month");}
 static void validate(JSONObject s) throws Exception {
  String o=s.getString("origin"),d=s.getString("destination");
  if(!o.matches("[A-Z]{3}")||!d.matches("[A-Z]{3}")||o.equals(d))throw new Exception("서로 다른 출발·도착 공항을 선택하세요.");
  if(!RegionScope.allowed(s,o))throw new Exception(RegionScope.NOTICE);
  LocalDate a=LocalDate.parse(s.getString("start")),b=LocalDate.parse(s.getString("end"));
  LocalDate today=today(),limit=lastDate("OZ".equals(s.optString("carrier")));range(a,b,limit,today);
  if(s.optBoolean("round")){LocalDate c=LocalDate.parse(s.getString("returnStart")),e=LocalDate.parse(s.getString("returnEnd"));range(c,e,limit,today);if(c.isBefore(a))throw new Exception("귀국 날짜는 출발 시작일 이후로 선택하세요.");}
  int adults=s.getInt("adults"),children=s.getInt("children"),infants=s.getInt("infants"),cabin=s.getInt("cabin");
  if(adults<1||children<0||infants<0||adults+children>9||infants>adults||cabin<0||cabin>2)throw new Exception("성인 최소 1명, 성인+소아 최대 9명, 유아는 성인 수 이하로 선택하세요.");
 }
 static void range(LocalDate a,LocalDate b)throws Exception{LocalDate today=today();range(a,b,lastDate(false),today);}
 static void range(LocalDate a,LocalDate b,LocalDate limit)throws Exception{range(a,b,limit,today());}
 static void range(LocalDate a,LocalDate b,LocalDate limit,LocalDate today)throws Exception{if(a.isBefore(today)||b.isBefore(a)||b.isAfter(limit))throw new Exception("조회 가능 기간은 오늘부터 "+limit+"까지입니다.");}
 static List<JSONObject> queries(JSONObject s)throws Exception{
  LocalDate today=today(),limit=lastDate("OZ".equals(s.optString("carrier")));List<JSONObject> out=new ArrayList<>();if(!RegionScope.allowed(s,s.optString("origin")))return out;add(out,s.getString("origin"),s.getString("destination"),s.getString("start"),s.getString("end"),limit,today);
  if(s.optBoolean("round"))add(out,s.getString("destination"),s.getString("origin"),s.getString("returnStart"),s.getString("returnEnd"),limit,today);return out;
 }
 static void add(List<JSONObject> out,String o,String d,String a,String b)throws Exception{LocalDate today=today();add(out,o,d,a,b,lastDate(false),today);}
 static void add(List<JSONObject> out,String o,String d,String a,String b,LocalDate limit)throws Exception{add(out,o,d,a,b,limit,today());}
 static void add(List<JSONObject> out,String o,String d,String a,String b,LocalDate limit,LocalDate today)throws Exception{LocalDate start=LocalDate.parse(a),end=LocalDate.parse(b);if(end.isBefore(today))return;if(start.isBefore(today))start=today;if(end.isAfter(limit))end=limit;if(start.isAfter(end))return;YearMonth m=YearMonth.from(start),last=YearMonth.from(end);while(!m.isAfter(last)){out.add(new JSONObject().put("origin",o).put("destination",d).put("month",m.toString()));m=m.plusMonths(1);}}
 static void validateSnapshot(JSONObject q,JSONObject v)throws Exception{
  if(!key(q).equals(key(v)))throw new Exception("저장할 결과의 노선·월이 요청과 다릅니다.");
  YearMonth month=YearMonth.parse(q.getString("month"));OffsetDateTime.parse(v.getString("sourceUpdatedAt"));OffsetDateTime.parse(v.getString("collectedAt"));
  JSONArray ds=v.getJSONArray("dates");if(ds.length()!=month.lengthOfMonth())throw new Exception("날짜가 빠져 저장하지 않았습니다.");Set<String> seen=new HashSet<>();
  for(int i=0;i<ds.length();i++){JSONObject day=ds.getJSONObject(i);String date=day.getString("date");if(!YearMonth.from(LocalDate.parse(date)).equals(month)||!seen.add(date))throw new Exception("잘못되거나 중복된 날짜입니다.");String status=day.getString("status");if(!Arrays.asList("OBSERVED","UNKNOWN","NO_FLIGHT").contains(status))throw new Exception("알 수 없는 운항 상태입니다.");for(String f:ALL_FIELDS){if(!(day.get(f) instanceof Boolean))throw new Exception("잘못된 좌석 정보입니다.");if(!"OBSERVED".equals(status)&&day.getBoolean(f))throw new Exception("미확인·비운항 날짜에 좌석 표식이 있어 저장하지 않았습니다.");}}
 }
 static boolean sameDates(JSONArray a,JSONArray b)throws Exception{
  if(a.length()!=b.length())return false;
  Map<String,JSONObject> days=new HashMap<>();
  for(int i=0;i<a.length();i++){JSONObject d=a.getJSONObject(i);if(days.put(d.getString("date"),d)!=null)return false;}
  for(int i=0;i<b.length();i++){JSONObject n=b.getJSONObject(i),p=days.remove(n.getString("date"));if(p==null||!p.getString("status").equals(n.getString("status")))return false;for(String f:ALL_FIELDS)if(p.getBoolean(f)!=n.getBoolean(f))return false;}
  return days.isEmpty();
 }
 static Set<String> changes(JSONObject before,JSONObject after,String field)throws Exception{
  Set<String> result=new HashSet<>();if(before==null||!OffsetDateTime.parse(after.getString("sourceUpdatedAt")).isAfter(OffsetDateTime.parse(before.getString("sourceUpdatedAt"))))return result;
  Map<String,JSONObject> old=new HashMap<>();JSONArray a=before.getJSONArray("dates"),b=after.getJSONArray("dates");for(int i=0;i<a.length();i++)old.put(a.getJSONObject(i).getString("date"),a.getJSONObject(i));
  for(int i=0;i<b.length();i++){JSONObject n=b.getJSONObject(i),p=old.get(n.getString("date"));if(p!=null&&"OBSERVED".equals(p.optString("status"))&&"OBSERVED".equals(n.optString("status"))&&!p.getBoolean(field)&&n.getBoolean(field))result.add(n.getString("date"));}return result;
 }
 static String people(JSONObject s){return "성인 "+s.optInt("adults")+" · 소아 "+s.optInt("children")+" · 유아 "+s.optInt("infants");}
}


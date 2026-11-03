package kr.tong.sky;

import android.content.Context;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

/** User-selected monthly display inference. Original observation facts are never changed. */
final class BaselineRepeatMarkerPolicy {
 static final String SOURCE="USER_ALL_WEEKDAYS_TRIPLET_V2";
 private static volatile JSONObject catalog;
 static synchronized void load(Context context){
  if(catalog!=null)return;
  try(InputStream stream=context.getAssets().open("repeat-marker-catalog.json")){
   byte[] bytes=stream.readNBytes(65537);if(bytes.length>65536)throw new IOException("판정 지역 자료 크기 초과");catalog=new JSONObject(new String(bytes,StandardCharsets.UTF_8));
  }catch(Exception ignored){/* A missing optional catalog must not block saved calendar access. */}
 }
 static String scope(JSONObject snapshot,JSONObject selected){
  if(snapshot==null||!"KE".equals(snapshot.optString("carrier","KE")))return null;
  JSONObject regions=selected==null?null:selected.optJSONObject("regions");if(regions==null)return null;
  Iterator<String> names=regions.keys();while(names.hasNext()){
   String region=names.next();JSONArray cities=regions.optJSONArray(region);if(cities==null)continue;
   for(int i=0;i<cities.length();i++){JSONObject city=cities.optJSONObject(i);if(city==null)continue;String code=city.optString("code");if(!code.equals(snapshot.optString("origin"))&&!code.equals(snapshot.optString("destination")))continue;if("유럽".equals(region))return "EUROPE";if("미주".equals(region))return city.optString("countryCode");}
  }return null;
 }
 private static LocalDate date(String value){try{if(value==null||!value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}"))return null;LocalDate date=LocalDate.parse(value);return date.toString().equals(value)?date:null;}catch(Exception ignored){return null;}}
 private static Map<String,JSONObject> days(JSONObject snapshot,Set<String> duplicates){
  Map<String,JSONObject> days=new TreeMap<>();JSONArray values=snapshot==null?null:snapshot.optJSONArray("dates");if(values!=null)for(int i=0;i<values.length();i++){JSONObject day=values.optJSONObject(i);String value=day==null?"":day.optString("date");if(date(value)==null||!value.startsWith(snapshot.optString("month")+"-"))continue;if(days.put(value,day)!=null&&duplicates!=null)duplicates.add(value);}return days;
 }
 static boolean applies(JSONObject snapshot){return scope(snapshot,catalog)!=null;}
 static boolean monthly(JSONObject snapshot){return snapshot!=null&&"KE".equals(snapshot.optString("carrier","KE"))&&"MONTHLY_CALENDAR".equals(snapshot.optString("collectionMethod"));}
 static boolean usesCalendar(JSONObject snapshot){return monthly(snapshot)||applies(snapshot);}
 static JSONObject calendarSource(JSONObject day)throws Exception{
  if(day==null)return null;JSONObject original=day.optJSONObject("awardCalendarFlags");if(original==null)original=day.optJSONObject("legacyAwardFlags");if(original==null)original=day;JSONObject source=new JSONObject(original.toString());if(!source.has("status"))source.put("status",day.optString("status","UNKNOWN"));return source;
 }
 private static JSONObject flags(JSONObject day,JSONObject raw,JSONObject projected)throws Exception{
  JSONObject original=calendarSource(day);if(original==null||!"OBSERVED".equals(original.optString("status")))return null;for(String field:Search.ALL_FIELDS)if(!(original.opt(field) instanceof Boolean))return null;return original;
 }
 /** A view copy of the original calendar; no operator or timetable proof gates are used. */
 static JSONObject calendar(JSONObject raw)throws Exception{return calendar(raw,catalog);}
 static JSONObject calendar(JSONObject raw,JSONObject selected)throws Exception{
  if(raw==null)return null;JSONObject view=new JSONObject(raw.toString());if(!monthly(raw)&&scope(raw,selected)==null)return view;Map<String,JSONObject> original=days(raw,null);JSONArray dates=view.optJSONArray("dates");if(dates==null)return view;
  for(int i=0;i<dates.length();i++){JSONObject day=dates.getJSONObject(i),source=calendarSource(original.get(day.optString("date")));String status=source==null?"UNKNOWN":source.optString("status","UNKNOWN");boolean observed=source!=null&&"OBSERVED".equals(status);for(String field:Search.ALL_FIELDS)observed&=source!=null&&source.opt(field) instanceof Boolean;if(!observed&&!"NO_FLIGHT".equals(status))status="UNKNOWN";day.put("status",status);JSONObject saved=new JSONObject().put("status",status).put("sourceKind","SAVED_CALENDAR");for(String field:Search.ALL_FIELDS){boolean marked=observed&&source.getBoolean(field);day.put(field,marked);saved.put(field,marked);}day.put("savedCalendarSource",saved);for(String key:new String[]{"savedAggregate","savedAggregatePendingReason","timetableReferenceConflict","timetableReferences","routeWeekdayLabel","routeWeekdaySource"})day.remove(key);}
  view=apply(raw,view,selected);JSONArray projected=view.getJSONArray("dates");for(int i=0;i<projected.length();i++){JSONObject day=projected.getJSONObject(i);if(excluded(day))for(String field:Search.ALL_FIELDS)day.put(field,false);}return view;
 }
 static JSONObject rule(JSONObject raw,JSONObject projected,JSONObject selected)throws Exception{
  String scope=scope(raw,selected);if(scope==null||date(raw.optString("month")+"-01")==null)return null;YearMonth month=YearMonth.parse(raw.getString("month"));Set<String> duplicates=new HashSet<>();Map<String,JSONObject> days=days(raw,duplicates),projectedDays=days(projected,null);SortedSet<String> candidates=new TreeSet<>();
  for(var entry:days.entrySet()){JSONObject f=flags(entry.getValue(),raw,projectedDays.get(entry.getKey()));if(!duplicates.contains(entry.getKey())&&f!=null&&f.optBoolean("economyAward")&&f.optBoolean("prestigeAward")&&f.optBoolean("prestigeUpgrade"))candidates.add(entry.getKey());}
  JSONArray evidence=new JSONArray(),reviews=new JSONArray(),weekdays=new JSONArray();
  for(int weekday=0;weekday<7;weekday++){
   JSONArray all=new JSONArray(),matched=new JSONArray(),check=new JSONArray(),issues=new JSONArray();for(int n=1;n<=month.lengthOfMonth();n++){LocalDate d=month.atDay(n);if(d.getDayOfWeek().getValue()%7!=weekday)continue;String value=d.toString();all.put(value);if(candidates.contains(value))matched.put(value);else{check.put(value);String reason=duplicates.contains(value)?"날짜 중복":!days.containsKey(value)?"날짜 누락":flags(days.get(value),raw,projectedDays.get(value))==null?"미확인":"세 표식이 모두 있지 않음";issues.put(new JSONObject().put("date",value).put("reason",reason));}}
   if(check.length()==0){weekdays.put(weekday);evidence.put(new JSONObject().put("weekday",weekday).put("dates",all));}else if(matched.length()>0)reviews.put(new JSONObject().put("weekday",weekday).put("dates",all).put("matchedDates",matched).put("checkDates",check).put("issues",issues));
  }
  return new JSONObject().put("source",SOURCE).put("scope",scope).put("month",month.toString()).put("origin",raw.getString("origin")).put("destination",raw.getString("destination")).put("candidateDates",new JSONArray(candidates)).put("weekdays",weekdays).put("evidence",evidence).put("reviews",reviews);
 }
 static JSONObject apply(JSONObject raw,JSONObject projected)throws Exception{return apply(raw,projected,catalog);}
 static JSONObject apply(JSONObject raw,JSONObject projected,JSONObject selected)throws Exception{
  if(projected==null)return null;JSONObject result=new JSONObject(projected.toString());result.remove("repeatMarkerPolicy");JSONArray dates=result.optJSONArray("dates");if(dates==null)return result;JSONObject rule=rule(raw,projected,selected);JSONArray excluded=new JSONArray();Map<String,JSONObject> rawDays=days(raw,null),projectedDays=days(projected,null);
  for(int i=0;i<dates.length();i++){JSONObject day=dates.optJSONObject(i);if(day==null)continue;day.remove("repeatMarkerExclusion");day.remove("repeatMarkerReview");day.remove("repeatMarkerReviewFlags");day.remove("displayStatus");day.remove("displayFlags");if(rule==null)continue;decorate(day,rule);JSONObject original=rawDays.get(day.optString("date"));if(review(day)&&original!=null){JSONObject observed=flags(original,raw,projectedDays.get(day.optString("date")));if(observed!=null){JSONObject stored=new JSONObject().put("status","OBSERVED").put("sourceKind","REVIEW_STORED_CALENDAR");for(String field:Search.ALL_FIELDS)stored.put(field,observed.getBoolean(field));day.put("repeatMarkerReviewFlags",stored);}}if(day.has("repeatMarkerExclusion"))excluded.put(day.optString("date"));}
  if(rule!=null)result.put("repeatMarkerPolicy",rule.put("excludedDates",excluded));return result;
 }
 private static void decorate(JSONObject day,JSONObject rule)throws Exception{
  LocalDate date=date(day.optString("date"));if(date==null||!YearMonth.from(date).toString().equals(rule.optString("month")))return;int weekday=date.getDayOfWeek().getValue()%7;JSONArray evidence=rule.optJSONArray("evidence"),reviews=rule.optJSONArray("reviews");
  if(evidence!=null)for(int n=0;n<evidence.length();n++){JSONObject found=evidence.getJSONObject(n);if(found.optInt("weekday",-1)==weekday)day.put("repeatMarkerExclusion",new JSONObject().put("source",SOURCE).put("kind","MONTH_WEEKDAY").put("month",rule.getString("month")).put("weekday",weekday).put("evidenceDates",new JSONArray(found.getJSONArray("dates").toString())));}
  if(reviews!=null)for(int n=0;n<reviews.length();n++){JSONObject found=reviews.getJSONObject(n);if(found.optInt("weekday",-1)==weekday)day.put("repeatMarkerReview",new JSONObject(found.toString()).put("source",SOURCE).put("month",rule.getString("month")));}
 }
 static JSONObject missingDay(JSONObject snapshot,LocalDate date)throws Exception{
  JSONObject day=new JSONObject().put("date",date.toString()).put("status","UNKNOWN");for(String field:Search.ALL_FIELDS)day.put(field,false);JSONObject rule=snapshot==null?null:snapshot.optJSONObject("repeatMarkerPolicy");if(rule!=null)decorate(day,rule);return day;
 }
 static boolean review(JSONObject day){return day!=null&&day.optJSONObject("repeatMarkerReview")!=null;}
 static boolean excluded(JSONObject day){return day!=null&&day.optJSONObject("repeatMarkerExclusion")!=null;}
 static String details(JSONObject day){
  JSONObject review=day==null?null:day.optJSONObject("repeatMarkerReview"),excluded=day==null?null:day.optJSONObject("repeatMarkerExclusion");if(review!=null){String text="확인필요 · 해당 월의 같은 요일에 세 표식과 다른 날짜가 섞여 있어 제외하지 않았습니다.\n세 표식 날짜: "+review.optJSONArray("matchedDates")+"\n확인 날짜: "+review.optJSONArray("checkDates")+"\n사유: "+review.optJSONArray("issues")+"\n";JSONObject stored=day.optJSONObject("repeatMarkerReviewFlags");if(stored!=null){text+="\n원래 저장 표식 · 실제 운항 확인과 구분\n";for(int i=0;i<Search.ALL_FIELDS.length;i++)if(stored.optBoolean(Search.ALL_FIELDS[i]))text+=SeatMarks.DETAIL_LABELS[i]+": 표시 있음\n";}return text;}if(excluded!=null)return "대한항공 비운항 추정 · 해당 월 같은 요일 전체에 이코노미·프레스티지·프레스티지 승급 표식이 있어 저장 달력의 해당 날짜 표식을 제외했습니다.\n판정 날짜: "+excluded.optJSONArray("evidenceDates")+"\n";return "";
 }
}

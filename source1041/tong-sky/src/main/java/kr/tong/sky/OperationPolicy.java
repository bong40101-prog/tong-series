package kr.tong.sky;

import android.content.Context;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;

/** Immutable packaged evidence, supplemented by validated per-observation weekly proofs. */
final class OperationPolicy {
 private static final int MAX_ASSET_BYTES=2*1024*1024;
 private final Map<String,List<String>> byRoute;
 final String fingerprint;
 static final OperationPolicy EMPTY=new OperationPolicy(Map.of(),"empty-v2-flight-attribution");
 private static volatile OperationPolicy current=EMPTY;
 private OperationPolicy(Map<String,List<String>> byRoute,String fingerprint){this.byRoute=byRoute;this.fingerprint=fingerprint;}
 static OperationPolicy current(){return current;}
 static OperationPolicy load(Context context){
  try(InputStream input=context.getAssets().open("operation-schedules.json")){
   byte[] bytes=input.readNBytes(MAX_ASSET_BYTES+1);if(bytes.length>MAX_ASSET_BYTES)throw new IOException("운항 근거 파일 크기 초과");OperationPolicy loaded=fromDocument(new JSONObject(new String(bytes,StandardCharsets.UTF_8)));current=loaded;return loaded;
  }catch(FileNotFoundException absent){return EMPTY;}catch(Exception invalid){throw new IllegalStateException("공식 운항 근거를 확인하지 못했습니다.",invalid);}
 }
 static OperationPolicy fromDocument(JSONObject document)throws Exception{
  if(!(document.opt("schema") instanceof Number)||((Number)document.get("schema")).doubleValue()!=1)throw new IllegalArgumentException("운항 근거 버전 오류");OffsetDateTime.parse(document.getString("updatedAt"));JSONArray weeks=document.getJSONArray("weeks");if(weeks.length()>1024)throw new IllegalArgumentException("운항 근거 개수 초과");Map<String,List<JSONObject>> grouped=new TreeMap<>();
  for(int i=0;i<weeks.length();i++){JSONObject week=weeks.getJSONObject(i);OperationSchedule.validateWeek(week,week);String route=week.getString("origin")+"-"+week.getString("destination");grouped.computeIfAbsent(route,key->new ArrayList<>()).add(week);}
  Map<String,List<String>> routes=new TreeMap<>();StringBuilder canonical=new StringBuilder("operation-policy-v2-flight-attribution\n");for(var entry:grouped.entrySet()){
   JSONObject first=entry.getValue().get(0);List<JSONObject> selected=OperationSchedule.union(first,entry.getValue());List<String> frozen=new ArrayList<>();for(JSONObject week:selected){String value=week.toString();frozen.add(value);canonical.append(entry.getKey()).append('\n').append(value).append('\n');}routes.put(entry.getKey(),List.copyOf(frozen));
  }
  StringBuilder hash=new StringBuilder();for(byte value:MessageDigest.getInstance("SHA-256").digest(canonical.toString().getBytes(StandardCharsets.UTF_8)))hash.append(String.format(Locale.ROOT,"%02x",value&255));return new OperationPolicy(Collections.unmodifiableMap(routes),hash.toString());
 }
 boolean isEmpty(){return byRoute.isEmpty();}
 private List<JSONObject> bundled(JSONObject snapshot)throws Exception{
  List<JSONObject> result=new ArrayList<>();if(snapshot==null||!"KE".equals(snapshot.optString("carrier","KE")))return result;List<String> stored=byRoute.get(snapshot.optString("origin")+"-"+snapshot.optString("destination"));if(stored==null)return result;YearMonth month=YearMonth.parse(snapshot.getString("month"));
  for(String value:stored){JSONObject week=new JSONObject(value);LocalDate start=LocalDate.parse(week.getString("weekStart"));if(!start.isAfter(month.atEndOfMonth())&&!start.plusDays(6).isBefore(month.atDay(1)))result.add(week);}return result;
 }
 private List<JSONObject> evidence(JSONObject... snapshots)throws Exception{
  List<JSONObject> result=new ArrayList<>();for(JSONObject snapshot:snapshots)if(snapshot!=null&&"KE".equals(snapshot.optString("carrier","KE"))){result.addAll(bundled(snapshot));result.addAll(OperationSchedule.embedded(snapshot));}return result;
 }
 JSONObject snapshot(JSONObject value)throws Exception{
  if(value==null||!"KE".equals(value.optString("carrier","KE")))return value;if(RepeatMarkerPolicy.usesCalendar(value))return RepeatMarkerPolicy.apply(value,value);List<JSONObject> proof=evidence(value);return RepeatMarkerPolicy.apply(value,OperationSchedule.apply(value,proof));
 }
 JSONObject payload(JSONObject value)throws Exception{
  if(value==null)return null;JSONObject current=value.getJSONObject("current"),previous=value.optJSONObject("previous");if(!"KE".equals(current.optString("carrier","KE")))return value;if(RepeatMarkerPolicy.usesCalendar(current)){JSONObject copy=new JSONObject(value.toString()).put("current",RepeatMarkerPolicy.apply(current,current));if(previous!=null)copy.put("previous",RepeatMarkerPolicy.apply(previous,previous));return copy;}List<JSONObject> proof=evidence(current,previous);List<JSONObject> latest=OperationSchedule.union(current,proof);JSONObject result=new JSONObject(value.toString()).put("current",RepeatMarkerPolicy.apply(current,OperationSchedule.apply(current,latest)));if(previous!=null)result.put("previous",RepeatMarkerPolicy.apply(previous,OperationSchedule.apply(previous,latest)));return result;
 }
 record Payloads(JSONObject first,JSONObject second){}
 Payloads reconcile(JSONObject first,JSONObject second)throws Exception{
  if(first==null)return new Payloads(null,payload(second));if(second==null)return new Payloads(payload(first),null);JSONObject a=first.getJSONObject("current"),b=second.getJSONObject("current");if(!Search.key(a).equals(Search.key(b))||!a.optString("carrier","KE").equals(b.optString("carrier","KE")))throw new IllegalArgumentException("운항 근거 비교 노선 오류");if(!"KE".equals(a.optString("carrier","KE")))return new Payloads(first,second);if(RepeatMarkerPolicy.usesCalendar(a)||RepeatMarkerPolicy.usesCalendar(b))return new Payloads(payload(first),payload(second));
  List<JSONObject> proof=evidence(a,first.optJSONObject("previous"),b,second.optJSONObject("previous"));List<JSONObject> latest=OperationSchedule.union(a,proof);return new Payloads(with(first,latest),with(second,latest));
 }
 OperationSchedule.Reconciled reconcileSnapshots(JSONObject first,JSONObject second)throws Exception{
  if(!Search.key(first).equals(Search.key(second))||!first.optString("carrier","KE").equals(second.optString("carrier","KE")))throw new IllegalArgumentException("운항 근거 비교 노선 오류");if(!"KE".equals(first.optString("carrier","KE"))||RepeatMarkerPolicy.usesCalendar(first)||RepeatMarkerPolicy.usesCalendar(second))return new OperationSchedule.Reconciled(first,second);List<JSONObject> proof=evidence(first,second);List<JSONObject> latest=OperationSchedule.union(first,proof);return new OperationSchedule.Reconciled(OperationSchedule.apply(first,latest),OperationSchedule.apply(second,latest));
 }
 private static JSONObject rawFlags(JSONObject day){JSONObject original=day.optJSONObject("awardCalendarFlags");if(original!=null)return original;JSONObject legacy=day.optJSONObject("legacyAwardFlags");return legacy==null?day:legacy;}
 private static boolean rawDetails(JSONObject day){return Set.of("CAPTURED","NO_MARKS").contains(day.optString("flightDetailStatus"))&&day.optJSONArray("flightObservations")!=null;}
 private static String flightFacts(JSONObject day)throws Exception{JSONArray rows=day.optJSONArray("flightObservations");if(rows==null)rows=day.optJSONArray("flights");List<String> values=new ArrayList<>();if(rows!=null)for(int i=0;i<rows.length();i++){JSONObject row=rows.getJSONObject(i);StringBuilder fact=new StringBuilder(row.getString("number")).append('|').append(row.getString("departureTime")).append('|').append(row.optString("operatorCode"));for(String field:Search.ALL_FIELDS)fact.append('|').append(row.getBoolean(field));values.add(fact.toString());}Collections.sort(values);return day.optString("flightDetailStatus")+"|"+values;}
 private static boolean exactCalendarDay(JSONObject first,JSONObject second)throws Exception{JSONObject a=RepeatMarkerPolicy.calendarSource(first),b=RepeatMarkerPolicy.calendarSource(second);if(a==null||b==null||!a.optString("status","UNKNOWN").equals(b.optString("status","UNKNOWN")))return false;for(String field:Search.ALL_FIELDS)if(!(a.opt(field) instanceof Boolean)||!(b.opt(field) instanceof Boolean)||a.getBoolean(field)!=b.getBoolean(field))return false;return true;}
 private static boolean exactDay(JSONObject first,JSONObject second)throws Exception{
  if(rawDetails(first)!=rawDetails(second))return false;if(rawDetails(first))return flightFacts(first).equals(flightFacts(second));JSONObject a=rawFlags(first),b=rawFlags(second);if(a.has("status")&&b.has("status")&&!a.getString("status").equals(b.getString("status")))return false;for(String field:Search.ALL_FIELDS)if(a.getBoolean(field)!=b.getBoolean(field))return false;return true;
 }
 private static boolean legacyMaskLost(JSONObject snapshot,JSONObject day)throws Exception{
  JSONObject original=rawFlags(day);if(!"NO_FLIGHT".equals(original.optString("status")))return false;for(String field:Search.ALL_FIELDS)if(original.getBoolean(field))return false;String date=day.getString("date");for(JSONObject week:OperationSchedule.embedded(snapshot))if(week.getInt("schema")==1){OperationSchedule.validateWeek(snapshot,week);JSONArray dates=week.getJSONArray("dates");for(int i=0;i<dates.length();i++){JSONObject d=dates.getJSONObject(i);if(date.equals(d.getString("date"))&&"NO_FLIGHT".equals(d.getString("status")))return true;}}return false;
 }
 private static boolean resolvedDay(JSONObject oldDay,JSONObject freshDay,JSONObject old,JSONObject fresh)throws Exception{
  if(!"KE".equals(old.optString("carrier","KE"))||!OffsetDateTime.parse(old.getString("sourceUpdatedAt")).toInstant().equals(OffsetDateTime.parse(fresh.getString("sourceUpdatedAt")).toInstant())||OffsetDateTime.parse(fresh.getString("collectedAt")).toInstant().isBefore(OffsetDateTime.parse(old.getString("collectedAt")).toInstant()))return false;
  if(!rawDetails(oldDay)&&rawDetails(freshDay)&&freshDay.optInt("operationSchema")==2&&freshDay.optBoolean("operationCompleteOperators")&&OperationSchedule.SOURCE.equals(freshDay.optString("operationSource")))return true;
  return !rawDetails(oldDay)&&!rawDetails(freshDay)&&legacyMaskLost(old,oldDay);
 }
 boolean sameFacts(JSONObject first,JSONObject second)throws Exception{
  OperationSchedule.Reconciled pair=reconcileSnapshots(first,second);first=pair.first();second=pair.second();JSONArray a=first.getJSONArray("dates"),b=second.getJSONArray("dates");if(a.length()!=b.length())return false;Map<String,JSONObject> days=new HashMap<>();for(int i=0;i<b.length();i++){JSONObject day=b.getJSONObject(i);if(days.put(day.getString("date"),day)!=null)return false;}
  for(int i=0;i<a.length();i++){JSONObject day=a.getJSONObject(i),other=days.remove(day.getString("date"));if(other==null)return false;if(RepeatMarkerPolicy.usesCalendar(first)||RepeatMarkerPolicy.usesCalendar(second)){if(!exactCalendarDay(day,other))return false;continue;}if(!exactDay(day,other)&&!resolvedDay(day,other,first,second)&&!resolvedDay(other,day,second,first))return false;}return days.isEmpty();
 }
 boolean resolvesRequery(JSONObject old,JSONObject fresh)throws Exception{
  if(RepeatMarkerPolicy.usesCalendar(old)||RepeatMarkerPolicy.usesCalendar(fresh))return false;OperationSchedule.Reconciled pair=reconcileSnapshots(old,fresh);old=pair.first();fresh=pair.second();if(!sameFacts(old,fresh))return false;JSONArray before=old.getJSONArray("dates"),after=fresh.getJSONArray("dates");Map<String,JSONObject> days=new HashMap<>();for(int i=0;i<after.length();i++){JSONObject day=after.getJSONObject(i);days.put(day.getString("date"),day);}for(int i=0;i<before.length();i++){JSONObject day=before.getJSONObject(i),next=days.get(day.getString("date"));if(next!=null&&!exactDay(day,next)&&resolvedDay(day,next,old,fresh))return true;}return false;
 }
 static boolean requiresRequery(JSONObject snapshot){if(RepeatMarkerPolicy.usesCalendar(snapshot))return false;JSONArray days=snapshot==null?null:snapshot.optJSONArray("dates");if(days!=null)for(int i=0;i<days.length();i++){JSONObject day=days.optJSONObject(i);if(day!=null&&day.opt("operationRequiresRequery") instanceof Boolean&&day.optBoolean("operationRequiresRequery"))return true;}return false;}
 private JSONObject with(JSONObject value,List<JSONObject> proof)throws Exception{JSONObject result=new JSONObject(value.toString()).put("current",RepeatMarkerPolicy.apply(value.getJSONObject("current"),OperationSchedule.apply(value.getJSONObject("current"),proof)));JSONObject previous=value.optJSONObject("previous");if(previous!=null)result.put("previous",RepeatMarkerPolicy.apply(previous,OperationSchedule.apply(previous,proof)));return result;}
}

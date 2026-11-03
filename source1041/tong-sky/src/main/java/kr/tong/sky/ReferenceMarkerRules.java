package kr.tong.sky;

import org.json.*;
import java.time.*;
import java.util.*;

/** Display-only counterpart of the website's reference-month policy. */
final class ReferenceMarkerRules {
 static final String SOURCE="USER_REFERENCE_MONTH_TRIPLET_V3", ORIGINAL_END="2026-12-17";
 static final class Inventory {
  final Map<String,JSONObject> days=new TreeMap<>();final List<String> expected=new ArrayList<>();boolean invalid,duplicate,complete;
  Inventory(JSONObject record)throws Exception {
   YearMonth month=YearMonth.parse(record.getString("month"));for(int n=1;n<=month.lengthOfMonth();n++)expected.add(month.atDay(n).toString());
   JSONArray values=record.optJSONArray("dates");if(values!=null)for(int n=0;n<values.length();n++){JSONObject day=values.optJSONObject(n);String date=day==null?"":day.optString("date");if(!expected.contains(date)){invalid=true;continue;}if(days.put(date,day)!=null)duplicate=true;}
   complete=!invalid&&!duplicate;for(String date:expected)complete&=known(days.get(date));
  }
 }
 static boolean known(JSONObject day)throws Exception{JSONObject flags=RepeatMarkerPolicy.calendarView(day);if(flags==null||!Set.of("OBSERVED","NO_FLIGHT").contains(flags.optString("status")))return false;for(String key:Search.ALL_FIELDS)if(!(flags.opt(key) instanceof Boolean)||("NO_FLIGHT".equals(flags.optString("status"))&&flags.optBoolean(key)))return false;return true;}
 static boolean triplet(JSONObject day)throws Exception{JSONObject flags=RepeatMarkerPolicy.flags(day);return flags!=null&&flags.optBoolean("economyAward")&&flags.optBoolean("prestigeAward")&&flags.optBoolean("prestigeUpgrade");}
 static String route(JSONObject r){return r.optString("carrier","KE")+"|"+r.optString("origin")+"|"+r.optString("destination");}
 static final class Context {
  final Map<String,TreeMap<String,JSONObject>> routes=new HashMap<>();
  final Map<JSONObject,Inventory> inventories=new IdentityHashMap<>();
  // Retain only original calendar flags, not large flight/audit details.
  void add(JSONObject raw,long success)throws Exception{
   if(!"KE".equals(raw.optString("carrier","KE")))return;
   TreeMap<String,JSONObject> months=routes.computeIfAbsent(route(raw),key->new TreeMap<>());String month=raw.getString("month");JSONObject old=months.get(month);if(old!=null&&old.optLong("success")>=success)return;
   JSONObject record=new JSONObject().put("carrier","KE").put("origin",raw.getString("origin")).put("destination",raw.getString("destination")).put("month",month).put("success",success);JSONArray dates=new JSONArray();
   for(int i=0;i<raw.getJSONArray("dates").length();i++){JSONObject day=raw.getJSONArray("dates").getJSONObject(i),source=RepeatMarkerPolicy.calendarView(day),copy=new JSONObject().put("date",day.optString("date"));if(source!=null){copy.put("status",source.optString("status","UNKNOWN"));for(String key:Search.ALL_FIELDS)if(source.has(key))copy.put(key,source.get(key));}dates.put(copy);}record.put("dates",dates);months.put(month,record);
  }
  Inventory inspect(JSONObject record)throws Exception{Inventory found=inventories.get(record);if(found==null){found=new Inventory(record);inventories.put(record,found);}return found;}
  JSONObject reference(JSONObject record,boolean december)throws Exception{TreeMap<String,JSONObject> months=routes.get(route(record));if(months==null)return null;if(december)return months.get("2027-01");for(JSONObject candidate:months.headMap(record.getString("month"),false).descendingMap().values())if(!"2026-12".equals(candidate.optString("month"))&&inspect(candidate).complete)return candidate;return null;}
 }
 static JSONObject empty(JSONObject base)throws Exception{return base.put("candidateDates",new JSONArray()).put("weekdays",new JSONArray()).put("evidence",new JSONArray()).put("reviews",new JSONArray());}
 static JSONObject proof(JSONArray array,int weekday){for(int i=0;i<array.length();i++){JSONObject row=array.optJSONObject(i);if(row!=null&&row.optInt("weekday",-1)==weekday)return row;}return null;}
 static void remove(JSONArray array,int weekday){for(int i=array.length()-1;i>=0;i--)if(array.optJSONObject(i).optInt("weekday",-1)==weekday)array.remove(i);}
 static int weekday(String date){return LocalDate.parse(date).getDayOfWeek().getValue()%7;}
 static JSONArray sorted(JSONArray array)throws Exception{JSONArray result=new JSONArray();for(int wd=0;wd<7;wd++){JSONObject row=proof(array,wd);if(row!=null)result.put(row);}return result;}
 static JSONObject evaluate(JSONObject raw,JSONObject selected,Context context)throws Exception{
  JSONObject base=RepeatMarkerPolicy.ownRule(raw,selected);if(base==null)return null;String month=raw.getString("month");if(month.compareTo("2026-12")<0)return empty(base);
  Inventory current=new Inventory(raw);boolean december=month.equals("2026-12");if(current.invalid||current.duplicate)return december?empty(base):base;if(!december&&current.complete)return base;
  JSONObject reference=context==null?null:context.reference(raw,december);if(!december&&reference==null)return base;boolean complete=reference!=null&&context.inspect(reference).complete;
  JSONObject policy=complete?RepeatMarkerPolicy.ownRule(reference,selected):null;String mode=december?"JANUARY_FOR_DECEMBER":"PREVIOUS_COMPLETE_MONTH",referenceMonth=december?"2027-01":reference.getString("month");
  JSONArray evidence=december?new JSONArray():base.getJSONArray("evidence"),reviews=december?new JSONArray():base.getJSONArray("reviews"),candidates=new JSONArray();
  for(String date:current.expected)if((!december||date.compareTo(ORIGINAL_END)>0)&&triplet(current.days.get(date)))candidates.put(date);
  for(int wd=0;wd<7;wd++){
   JSONArray dates=new JSONArray(),matched=new JSONArray();boolean conflict=false;for(String date:current.expected)if(weekday(date)==wd&&(!december||date.compareTo(ORIGINAL_END)>0)){dates.put(date);JSONObject day=current.days.get(date);if(triplet(day))matched.put(date);else if(known(day))conflict=true;}
   JSONObject proof=policy==null?null:proof(policy.getJSONArray("evidence"),wd);if(!december&&proof(evidence,wd)!=null)continue;if(matched.length()==0&&!(december&&proof!=null))continue;
   JSONObject row=new JSONObject().put("weekday",wd).put("referenceMode",mode).put("referenceMonth",referenceMonth).put("referenceStatus",complete?"COMPLETE":reference!=null?"INCOMPLETE":"MISSING").put("referenceDates",proof==null?new JSONArray():proof.getJSONArray("dates")).put("referenceSuccess",reference==null?JSONObject.NULL:reference.opt("success"));
   boolean valid=proof!=null&&matched.length()>0&&!conflict&&(!december||matched.length()==dates.length());remove(reviews,wd);
   if(valid)evidence.put(row.put("dates",matched));else{
    JSONArray check=new JSONArray(),issues=new JSONArray();for(int i=0;i<dates.length();i++){String date=dates.getString(i);if(proof!=null&&triplet(current.days.get(date)))continue;check.put(date);String reason=proof==null?(complete?"참고월에 같은 비운항 요일이 확인되지 않음":reference!=null?"참고월 자료 미완성":"참고월 자료 없음"):!current.days.containsKey(date)?"날짜 누락":!known(current.days.get(date))?"미확인":"세 표식이 모두 있지 않음";issues.put(new JSONObject().put("date",date).put("reason",reason));}
    if(check.length()>0)reviews.put(row.put("dates",dates).put("matchedDates",matched).put("checkDates",check).put("issues",issues));
   }
  }
  evidence=sorted(evidence);reviews=sorted(reviews);JSONArray weekdays=new JSONArray();for(int i=0;i<evidence.length();i++)weekdays.put(evidence.getJSONObject(i).getInt("weekday"));return base.put("source",SOURCE).put("referenceMode",mode).put("referenceMonth",referenceMonth).put("candidateDates",candidates).put("weekdays",weekdays).put("evidence",evidence).put("reviews",reviews);
 }
}

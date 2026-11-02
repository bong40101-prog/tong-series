package kr.tong.sky;
import org.json.*;
/** Applies view-only timetable and full-month repeated-marker decisions. */
final class RouteWeekdayFilter {
 static final String SOURCE=RepeatMarkerPolicy.SOURCE,LABEL="대한항공 비운항 일정은 직접 운항편 기준이며, 공동운항편은 제외됩니다.";
 static boolean applies(JSONObject snapshot){return false;}
 static JSONObject apply(JSONObject snapshot)throws Exception{return apply(snapshot,TimetableReference.current());}
 static JSONObject apply(JSONObject snapshot,TimetableReference reference)throws Exception{
  if(snapshot==null)return null;if(RepeatMarkerPolicy.usesCalendar(snapshot))return RepeatMarkerPolicy.calendar(snapshot);JSONObject view=new JSONObject(snapshot.toString());if(!"KE".equals(view.optString("carrier","KE")))return view;
  view.remove("routeWeekdayLabel");view.remove("routeWeekdaySource");JSONArray dates=view.optJSONArray("dates");if(dates!=null)for(int i=0;i<dates.length();i++){JSONObject day=dates.getJSONObject(i);for(String key:new String[]{"routeWeekdayAllowed","routeWeekdayExcluded","routeWeekdaySource","routeWeekdayLabel","savedAggregate","savedAggregatePendingReason"})day.remove(key);JSONArray rows=day.optJSONArray("flights");if(rows!=null)for(int n=0;n<rows.length();n++){JSONObject row=rows.getJSONObject(n);row.remove("weekdayAllowed");row.remove("weekdayExcluded");row.remove("weekdaySource");}}
  view=reference.apply(view);view=reference.legacy(snapshot,view);return RepeatMarkerPolicy.apply(snapshot,view);
 }
}

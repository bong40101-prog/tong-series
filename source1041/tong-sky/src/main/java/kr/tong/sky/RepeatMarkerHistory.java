package kr.tong.sky;

import android.content.*;
import android.database.*;
import android.database.sqlite.*;
import org.json.*;
import java.util.*;

/** Local audit ledger; recording a display decision never rewrites a collected calendar. */
final class RepeatMarkerHistory extends SQLiteOpenHelper {
 static final String DATABASE="tong-sky-repeat-decisions.db";
 private static final Object OPEN_LOCK=new Object();
 RepeatMarkerHistory(Context context){super(context,DATABASE,null,1);setWriteAheadLoggingEnabled(true);}
 @Override public SQLiteDatabase getWritableDatabase(){synchronized(OPEN_LOCK){return super.getWritableDatabase();}}
 @Override public SQLiteDatabase getReadableDatabase(){synchronized(OPEN_LOCK){return super.getReadableDatabase();}}
 @Override public void onCreate(SQLiteDatabase db){db.execSQL("CREATE TABLE decisions(id TEXT PRIMARY KEY, origin TEXT NOT NULL, destination TEXT NOT NULL, data TEXT NOT NULL, recorded_at INTEGER NOT NULL)");db.execSQL("CREATE INDEX decisions_route ON decisions(origin,destination,recorded_at)");}
 @Override public void onUpgrade(SQLiteDatabase db,int from,int to){}
 void record(JSONObject snapshot,long success)throws Exception{
  if(snapshot==null||!"KE".equals(snapshot.optString("carrier","KE")))return;JSONObject policy=snapshot.optJSONObject("repeatMarkerPolicy");if(policy==null)return;JSONArray evidence=policy.optJSONArray("evidence"),reviews=policy.optJSONArray("reviews");if((evidence==null||evidence.length()==0)&&(reviews==null||reviews.length()==0))return;
  SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{for(String type:new String[]{"evidence","reviews"}){JSONArray records=policy.optJSONArray(type);if(records==null)continue;for(int i=0;i<records.length();i++){
   JSONObject decision=new JSONObject(records.getJSONObject(i).toString()).put("kind",type.equals("reviews")?"REVIEW":"EXCLUDED").put("policy",policy.optString("source")).put("origin",snapshot.getString("origin")).put("destination",snapshot.getString("destination")).put("month",snapshot.getString("month")).put("sourceUpdatedAt",snapshot.optString("sourceUpdatedAt")).put("collectedAt",snapshot.optString("collectedAt")).put("success",success);
   String id=decision.getString("policy")+"|"+Search.key(snapshot)+"|"+decision.optInt("weekday")+"|"+decision.getString("kind")+"|"+decision.optString("sourceUpdatedAt")+"|"+decision.optString("collectedAt")+"|"+success;
   ContentValues values=new ContentValues();values.put("id",id);values.put("origin",snapshot.getString("origin"));values.put("destination",snapshot.getString("destination"));values.put("data",decision.toString());values.put("recorded_at",System.currentTimeMillis());db.insertWithOnConflict("decisions",null,values,SQLiteDatabase.CONFLICT_IGNORE);
  }}db.setTransactionSuccessful();}finally{db.endTransaction();}
 }
 static void record(Context context,JSONObject snapshot,long success)throws Exception{try(RepeatMarkerHistory history=new RepeatMarkerHistory(context)){history.record(snapshot,success);}}
 List<JSONObject> records(String origin,String destination)throws Exception{
  String where=origin==null||destination==null?"":" WHERE (origin=? AND destination=?) OR (origin=? AND destination=?)";String[] args=where.isEmpty()?null:new String[]{origin,destination,destination,origin};List<JSONObject> result=new ArrayList<>();
  try(Cursor rows=getReadableDatabase().rawQuery("SELECT data,recorded_at FROM decisions"+where+" ORDER BY recorded_at DESC,id DESC",args)){while(rows.moveToNext()&&result.size()<500){JSONObject decision=new JSONObject(rows.getString(0));if(Set.of(RepeatMarkerPolicy.SOURCE,ReferenceMarkerRules.SOURCE).contains(decision.optString("policy")))result.add(decision.put("recordedAt",rows.getLong(1)));}}return result;
 }
 static String weekday(int value){return value>=0&&value<7?new String[]{"일","월","화","수","목","금","토"}[value]+"요일":"요일 미확인";}
 static String dates(JSONArray dates){if(dates==null||dates.length()==0)return "없음";List<String> values=new ArrayList<>();for(int i=0;i<dates.length();i++)values.add(dates.optString(i));return String.join(", ",values);}
 static String details(JSONObject decision){
  StringBuilder out=new StringBuilder("REVIEW".equals(decision.optString("kind"))?"확인필요 · 해당 요일은 비운항으로 제외하지 않습니다.\n":"비운항 추정 · 원본 달력에서 해당 요일 전체의 좌석 표식을 제외합니다.\n");
  if(decision.has("referenceMonth"))out.append("참고월: ").append(decision.optString("referenceMonth")).append(" · ").append(decision.optString("referenceStatus")).append('\n');out.append("판정 날짜: ").append(dates(decision.optJSONArray("dates"))).append('\n');if("REVIEW".equals(decision.optString("kind"))){out.append("세 표식 날짜: ").append(dates(decision.optJSONArray("matchedDates"))).append("\n확인 날짜: ").append(dates(decision.optJSONArray("checkDates"))).append('\n');JSONArray issues=decision.optJSONArray("issues");if(issues!=null)for(int i=0;i<issues.length();i++){JSONObject issue=issues.optJSONObject(i);if(issue!=null)out.append(issue.optString("date")).append(" · ").append(issue.optString("reason")).append('\n');}}
  return out.append("원본 기준: ").append(decision.optString("sourceUpdatedAt","미확인")).append("\n원본 수집: ").append(decision.optString("collectedAt","미확인")).append("\n원본 달력의 세 표식 반복만으로 판정합니다.").toString();
 }
}

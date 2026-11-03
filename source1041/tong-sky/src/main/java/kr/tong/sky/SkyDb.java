package kr.tong.sky;
import android.content.*;import android.database.*;import android.database.sqlite.*;import org.json.*;import java.util.*;import java.time.*;
final class SkyDb extends SQLiteOpenHelper {
 // Separate helpers can open the shared metadata DB from both carrier workers.
 // Hold this lock before SQLiteOpenHelper's instance lock so first creation is atomic.
 private static final Object OPEN_LOCK=new Object();
 static final int DATABASE_VERSION=7;
 final String carrier;final OperationPolicy operationPolicy;
 SkyDb(Context c){this(c,false);}
 SkyDb(Context c,boolean asiana){this(c,asiana,null);}
 SkyDb(Context c,boolean asiana,OperationPolicy policy){super(c,asiana?"tong-sky-asiana.db":"tong-sky.db",null,DATABASE_VERSION);carrier=asiana?"OZ":"KE";RegionScope.load(c);if(!asiana)RepeatMarkerPolicy.load(c);operationPolicy=policy!=null?policy:asiana?OperationPolicy.EMPTY:OperationPolicy.load(c);setWriteAheadLoggingEnabled(true);}
 @Override public SQLiteDatabase getWritableDatabase(){synchronized(OPEN_LOCK){return super.getWritableDatabase();}}
 @Override public SQLiteDatabase getReadableDatabase(){synchronized(OPEN_LOCK){return super.getReadableDatabase();}}
 public void onCreate(SQLiteDatabase d){d.execSQL("CREATE TABLE batches(k TEXT PRIMARY KEY, spec TEXT NOT NULL)");d.execSQL("CREATE TABLE watches(id INTEGER PRIMARY KEY AUTOINCREMENT, spec TEXT NOT NULL)");d.execSQL("CREATE TABLE snapshots(k TEXT PRIMARY KEY, data TEXT NOT NULL, previous TEXT, success INTEGER NOT NULL)");d.execSQL("CREATE TABLE errors(k TEXT PRIMARY KEY, message TEXT NOT NULL, attempted INTEGER NOT NULL)");createSharedOutbox(d);createOperationState(d);}
 public void onUpgrade(SQLiteDatabase d,int a,int b){if(a<2)d.execSQL("CREATE TABLE batches(k TEXT PRIMARY KEY, spec TEXT NOT NULL)");if(a<4){d.delete("snapshots",null,null);d.delete("errors",null,null);d.delete("batches",null,null);}if(a<5)createSharedOutbox(d);if(a<6)createOperationState(d);}
 private static void createOperationState(SQLiteDatabase d){d.execSQL("CREATE TABLE IF NOT EXISTS operation_migrations(k TEXT PRIMARY KEY,policy TEXT NOT NULL)");}
 @Override public void onOpen(SQLiteDatabase d){super.onOpen(d);if(!"KE".equals(carrier))return;String previous=null;try(Cursor c=d.rawQuery("SELECT policy FROM operation_migrations WHERE k='KE'",null)){if(c.moveToFirst())previous=c.getString(0);}if(operationPolicy.fingerprint.equals(previous))return;
  d.beginTransaction();try{
   try(Cursor c=d.rawQuery("SELECT k,data,previous,success FROM snapshots ORDER BY k",null)){while(c.moveToNext()){JSONObject payload=new JSONObject().put("current",new JSONObject(c.getString(1))).put("previous",c.isNull(2)?JSONObject.NULL:new JSONObject(c.getString(2))).put("success",c.getLong(3));JSONObject changed=operationPolicy.payload(payload);if(!changed.toString().equals(payload.toString()))writeExistingOperation(d,c.getString(0),changed);}}
   try(Cursor c=d.rawQuery("SELECT scope,k,token,payload FROM shared_outbox ORDER BY scope,k",null)){while(c.moveToNext()){JSONObject payload=new JSONObject(c.getString(3)),changed=operationPolicy.payload(payload);String encoded=changed.toString();if(!encoded.equals(payload.toString())){if(encoded.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>MAX_SHARED_PAYLOAD_BYTES)throw new IllegalArgumentException("공유 대기 자료 크기 초과");ContentValues values=new ContentValues();values.put("payload",encoded);values.put("token",UUID.randomUUID().toString());if(d.update("shared_outbox",values,"scope=? AND k=? AND token=?",new String[]{c.getString(0),c.getString(1),c.getString(2)})!=1)throw new SQLiteException("운항 근거 공유 대기 갱신 실패");}}}
   ContentValues marker=new ContentValues();marker.put("k","KE");marker.put("policy",operationPolicy.fingerprint);if(d.insertWithOnConflict("operation_migrations",null,marker,SQLiteDatabase.CONFLICT_REPLACE)<0)throw new SQLiteException("운항 근거 적용 기록 실패");d.setTransactionSuccessful();
  }catch(Exception error){throw new IllegalStateException("저장 달력의 공식 운항 근거를 반영하지 못했습니다.",error);}finally{d.endTransaction();}
 }
 private void writeExistingOperation(SQLiteDatabase d,String key,JSONObject payload)throws Exception{ContentValues values=new ContentValues();values.put("data",payload.getJSONObject("current").toString());JSONObject previous=payload.optJSONObject("previous");if(previous==null)values.putNull("previous");else values.put("previous",previous.toString());if(d.update("snapshots",values,"k=?",new String[]{key})!=1)throw new SQLiteException("저장 달력 운항 근거 갱신 실패");}
 long add(JSONObject s)throws Exception{Search.validate(s);if(watches().size()>=10)throw new Exception("관심 검색은 최대 10개까지 저장할 수 있습니다.");ContentValues v=new ContentValues();v.put("spec",s.toString());return getWritableDatabase().insertOrThrow("watches",null,v);}
 List<JSONObject> watches(){List<JSONObject> out=new ArrayList<>();try(Cursor c=getReadableDatabase().rawQuery("SELECT id,spec FROM watches ORDER BY id DESC",null)){while(c.moveToNext())try{out.add(new JSONObject(c.getString(1)).put("id",c.getLong(0)));}catch(JSONException e){throw new IllegalStateException(e);}}return out;}
 void delete(long id){getWritableDatabase().delete("watches","id=?",new String[]{""+id});}
 JSONObject data(String key){try(Cursor c=getReadableDatabase().rawQuery("SELECT data,previous,success FROM snapshots WHERE k=?",new String[]{key})){if(c.moveToFirst())try{return operationPolicy.payload(new JSONObject().put("current",new JSONObject(c.getString(0))).put("previous",c.isNull(1)?JSONObject.NULL:new JSONObject(c.getString(1))).put("success",c.getLong(2)));}catch(Exception e){throw new IllegalStateException(e);}return null;}}
 /** Reference context for the two directions of one route, scoped to this read. */
 ReferenceMarkerRules.Context repeatContext(JSONObject snapshot)throws Exception{
  ReferenceMarkerRules.Context context=new ReferenceMarkerRules.Context();if(!"KE".equals(carrier))return context;
  String a=snapshot.getString("origin"),b=snapshot.getString("destination");
  try(Cursor rows=getReadableDatabase().rawQuery("SELECT data,success FROM snapshots WHERE k LIKE ? OR k LIKE ?",new String[]{a+"-"+b+"-%",b+"-"+a+"-%"})){while(rows.moveToNext())context.add(new JSONObject(rows.getString(0)),rows.getLong(1));}return context;
 }
 JSONObject calendarView(JSONObject snapshot,ReferenceMarkerRules.Context references)throws Exception{return "KE".equals(carrier)&&RepeatMarkerPolicy.usesCalendar(snapshot)?RepeatMarkerPolicy.calendar(snapshot,references):RouteWeekdayFilter.apply(snapshot);}
 /** Save locally without creating a shared write (including imports and private searches). */
 void save(JSONObject q,JSONObject next)throws Exception{
  next=operationPolicy.snapshot(next);validateSaved(q,next);SQLiteDatabase d=getWritableDatabase();d.beginTransaction();try{saveSnapshot(d,q,next);d.setTransactionSuccessful();}finally{d.endTransaction();}
 }
 private void validateSaved(JSONObject q,JSONObject next)throws Exception{SharedCache.validateCarrier(q,carrier);SharedCache.validateCarrier(next,carrier);Search.validateSnapshot(q,next);}
 private void saveSnapshot(SQLiteDatabase d,JSONObject q,JSONObject next)throws Exception{
  String k=Search.key(q);JSONObject old=data(k);if(old!=null){OperationPolicy.Payloads reconciled=operationPolicy.reconcile(old,new JSONObject().put("current",next).put("success",System.currentTimeMillis()));old=reconciled.first();next=reconciled.second().getJSONObject("current");}JSONObject prev=old==null?null:old.optJSONObject("previous"),current=old==null?null:old.getJSONObject("current");
  if(current!=null){int cmp=OffsetDateTime.parse(next.getString("sourceUpdatedAt")).toInstant().compareTo(OffsetDateTime.parse(current.getString("sourceUpdatedAt")).toInstant());if(cmp<0)throw new Exception("이전보다 오래된 대한항공 자료라 저장하지 않았습니다.");if(cmp==0&&!operationPolicy.sameFacts(current,next))throw new Exception("같은 기준 시각의 결과가 달라 저장하지 않았습니다.");if(cmp==0&&operationPolicy.resolvesRequery(next,current))throw new Exception("더 최근에 다시 확인한 저장 결과를 유지합니다.");if(cmp>0)prev=current;}
  ContentValues v=new ContentValues();v.put("k",k);v.put("data",next.toString());if(prev==null)v.putNull("previous");else v.put("previous",prev.toString());v.put("success",System.currentTimeMillis());if(d.insertWithOnConflict("snapshots",null,v,SQLiteDatabase.CONFLICT_REPLACE)<0)throw new SQLiteException("조회 결과 저장 실패");d.delete("errors","k=?",new String[]{k});
 }
 /** One durable payload per scope/key. A UUID makes acknowledgements safe across replacements. */
 record SharedItem(String key,String token,JSONObject payload,long queuedAt){}
 static final int MAX_SHARED_ITEMS=20,MAX_SHARED_PAYLOAD_BYTES=RegionDocument.MAX_RECORD_BYTES;
 private static void createSharedOutbox(SQLiteDatabase d){
  d.execSQL("CREATE TABLE IF NOT EXISTS shared_outbox(scope TEXT NOT NULL,k TEXT NOT NULL,token TEXT NOT NULL,payload TEXT NOT NULL,queued_at INTEGER NOT NULL CHECK(queued_at>0),PRIMARY KEY(scope,k),CHECK(length(CAST(payload AS BLOB))<="+MAX_SHARED_PAYLOAD_BYTES+"))");
  d.execSQL("CREATE INDEX IF NOT EXISTS shared_outbox_order ON shared_outbox(scope,queued_at,k)");
 }
 private String sharedOrigin(String scope){
  String prefix="OZ".equals(carrier)?"OZ-":"";if(scope==null||scope.length()>120||!scope.startsWith(prefix))throw new IllegalArgumentException("공유 대기 지역 오류");String plain=scope.substring(prefix.length());
  if(!plain.matches("[A-Z]{3}-.{1,96}")||plain.matches(".*[\\p{Cntrl}].*"))throw new IllegalArgumentException("공유 대기 지역 오류");return plain.substring(0,3);
 }
 private void pruneExcludedShared(String scope){
  String origin=sharedOrigin(scope),plain="OZ".equals(carrier)?scope.substring(3):scope,region=plain.substring(4);SQLiteDatabase db=getWritableDatabase();List<String> remove=new ArrayList<>();
  try(Cursor rows=db.rawQuery("SELECT k FROM shared_outbox WHERE scope=?",new String[]{scope})){while(rows.moveToNext()){String key=rows.getString(0);String[] route=key.split("-",3);String other=route.length==3?origin.equals(route[0])?route[1]:origin.equals(route[1])?route[0]:"":"";if(RegionScope.excludedRegion(region)||(!other.isEmpty()&&RegionScope.blockedAirport(other,carrier)))remove.add(key);}}
  for(String key:remove)db.delete("shared_outbox","scope=? AND k=?",new String[]{scope,key});
 }
 private void validateSharedPayload(String scope,String key,JSONObject payload)throws Exception{
  String origin=sharedOrigin(scope);JSONObject current=payload.getJSONObject("current");String o=current.getString("origin"),d=current.getString("destination");
  if(!o.matches("[A-Z]{3}")||!d.matches("[A-Z]{3}")||o.equals(d)||!(origin.equals(o)||origin.equals(d))||!key.equals(Search.key(current)))throw new IllegalArgumentException("공유 대기 노선 오류");
  validateSaved(current,current);long success=payload.getLong("success"),collected=OffsetDateTime.parse(current.getString("collectedAt")).toInstant().toEpochMilli();if(success<collected||success>System.currentTimeMillis()+300000L)throw new IllegalArgumentException("공유 대기 수집 시각 오류");
  JSONObject previous=payload.optJSONObject("previous");if(previous!=null){validateSaved(current,previous);if(!OffsetDateTime.parse(previous.getString("sourceUpdatedAt")).isBefore(OffsetDateTime.parse(current.getString("sourceUpdatedAt"))))throw new IllegalArgumentException("공유 대기 이전 자료 시각 오류");}
 }
 /** Snapshot, previous observation, success clock and pending shared payload commit together. */
 void saveForShare(JSONObject q,JSONObject next,String scope)throws Exception{
  sharedOrigin(scope);next=operationPolicy.snapshot(next);validateSaved(q,next);SQLiteDatabase d=getWritableDatabase();d.beginTransaction();try{
   saveSnapshot(d,q,next);String key=Search.key(q);String plain="OZ".equals(carrier)?scope.substring(3):scope;if(RegionScope.excludedRegion(plain.substring(4))||!RegionScope.allowed(q,sharedOrigin(scope))){d.delete("shared_outbox","scope=? AND k=?",new String[]{scope,key});d.setTransactionSuccessful();return;}JSONObject payload=data(key);validateSharedPayload(scope,key,payload);String encoded=payload.toString();if(encoded.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>MAX_SHARED_PAYLOAD_BYTES)throw new IllegalArgumentException("공유 대기 자료 크기 초과");
   long queued=System.currentTimeMillis();try(Cursor c=d.rawQuery("SELECT queued_at FROM shared_outbox WHERE scope=? AND k=?",new String[]{scope,key})){if(c.moveToFirst()&&c.getLong(0)>0)queued=Math.min(queued,c.getLong(0));}ContentValues v=new ContentValues();v.put("scope",scope);v.put("k",key);v.put("token",UUID.randomUUID().toString());v.put("payload",encoded);v.put("queued_at",queued);if(d.insertWithOnConflict("shared_outbox",null,v,SQLiteDatabase.CONFLICT_REPLACE)<0)throw new SQLiteException("공유 대기 저장 실패");d.setTransactionSuccessful();
  }finally{d.endTransaction();}
 }
 List<SharedItem> sharedPending(String scope,int limit)throws Exception{
  sharedOrigin(scope);pruneExcludedShared(scope);if(limit<1||limit>MAX_SHARED_ITEMS)throw new IllegalArgumentException("공유 대기 읽기 개수 오류");List<SharedItem> rows=new ArrayList<>();
  try(Cursor c=getReadableDatabase().rawQuery("SELECT k,token,payload,queued_at FROM shared_outbox WHERE scope=? ORDER BY queued_at,k LIMIT "+limit,new String[]{scope})){while(c.moveToNext()){
   String key=c.getString(0),token=c.getString(1),encoded=c.getString(2);if(token==null||!token.matches("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}")||encoded.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>MAX_SHARED_PAYLOAD_BYTES)throw new IllegalArgumentException("공유 대기 자료 오류");JSONObject payload=operationPolicy.payload(new JSONObject(encoded));validateSharedPayload(scope,key,payload);rows.add(new SharedItem(key,token,payload,c.getLong(3)));
  }}return rows;
 }
 int sharedPendingCount(String scope){sharedOrigin(scope);pruneExcludedShared(scope);try(Cursor c=getReadableDatabase().rawQuery("SELECT COUNT(*) FROM shared_outbox WHERE scope=?",new String[]{scope})){c.moveToFirst();return c.getInt(0);}}
 long oldestSharedAt(String scope){sharedOrigin(scope);pruneExcludedShared(scope);try(Cursor c=getReadableDatabase().rawQuery("SELECT MIN(queued_at) FROM shared_outbox WHERE scope=?",new String[]{scope})){return c.moveToFirst()&&!c.isNull(0)?c.getLong(0):0;}}
 void acknowledgeShared(String scope,List<SharedItem> sent){
  sharedOrigin(scope);if(sent==null||sent.size()>MAX_SHARED_ITEMS)throw new IllegalArgumentException("공유 완료 확인 개수 오류");for(SharedItem item:sent)if(item==null||item.key()==null||!item.key().matches("[A-Z]{3}-[A-Z]{3}-[0-9]{4}-[0-9]{2}")||item.token()==null||!item.token().matches("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}"))throw new IllegalArgumentException("공유 완료 확인 항목 오류");
  SQLiteDatabase d=getWritableDatabase();d.beginTransaction();try{for(SharedItem item:sent)d.delete("shared_outbox","scope=? AND k=? AND token=?",new String[]{scope,item.key(),item.token()});d.setTransactionSuccessful();}finally{d.endTransaction();}
 }
 /** Import by official source order; preserve the original observation clock and nearest valid history. */
 void importSnapshot(JSONObject q,JSONObject payload)throws Exception{
  payload=operationPolicy.payload(payload);JSONObject current=payload.getJSONObject("current"),incomingPrevious=payload.optJSONObject("previous");validateSaved(q,current);
  if(incomingPrevious!=null){validateSaved(q,incomingPrevious);if(!OffsetDateTime.parse(incomingPrevious.getString("sourceUpdatedAt")).isBefore(OffsetDateTime.parse(current.getString("sourceUpdatedAt"))))throw new Exception("이전 자료 시각 오류");}
  long success=payload.getLong("success");if(success<OffsetDateTime.parse(current.getString("collectedAt")).toInstant().toEpochMilli()||success>System.currentTimeMillis()+300000)throw new Exception("수집 시각 오류");
  SQLiteDatabase d=getWritableDatabase();d.beginTransaction();try{
   String key=Search.key(q);JSONObject old=data(key);OperationPolicy.Payloads reconciled=operationPolicy.reconcile(old,payload);old=reconciled.first();payload=reconciled.second();current=payload.getJSONObject("current");incomingPrevious=payload.optJSONObject("previous");JSONObject local=old==null?null:old.getJSONObject("current");int basis=local==null?1:OffsetDateTime.parse(current.getString("sourceUpdatedAt")).toInstant().compareTo(OffsetDateTime.parse(local.getString("sourceUpdatedAt")).toInstant());
   if(basis<0){writeExistingOperation(d,key,old);d.setTransactionSuccessful();return;}if(basis==0&&!operationPolicy.sameFacts(local,current))throw new Exception("같은 기준 시각의 결과가 달라 저장하지 않았습니다.");
   boolean incoming=local==null||basis>0||!operationPolicy.resolvesRequery(current,local)&&(operationPolicy.resolvesRequery(local,current)||success>=old.getLong("success"));JSONObject selected=incoming?current:local,previous=null;
   previous=laterPrevious(q,selected,previous,incomingPrevious);previous=laterPrevious(q,selected,previous,local);if(old!=null)previous=laterPrevious(q,selected,previous,old.optJSONObject("previous"));
   if(incoming){ContentValues v=new ContentValues();v.put("k",key);v.put("data",selected.toString());if(previous==null)v.putNull("previous");else v.put("previous",previous.toString());v.put("success",success);if(d.insertWithOnConflict("snapshots",null,v,SQLiteDatabase.CONFLICT_REPLACE)<0)throw new SQLiteException("조회 결과 가져오기 실패");d.delete("errors","k=?",new String[]{key});}
   else {ContentValues v=new ContentValues();v.put("data",selected.toString());if(previous==null)v.putNull("previous");else v.put("previous",previous.toString());if(d.update("snapshots",v,"k=?",new String[]{key})!=1)throw new SQLiteException("이전 조회 결과 가져오기 실패");}
   d.setTransactionSuccessful();
  }finally{d.endTransaction();}
 }
 private JSONObject laterPrevious(JSONObject q,JSONObject current,JSONObject selected,JSONObject candidate)throws Exception{
  if(candidate==null)return selected;validateSaved(q,candidate);java.time.Instant source=OffsetDateTime.parse(candidate.getString("sourceUpdatedAt")).toInstant();if(!source.isBefore(OffsetDateTime.parse(current.getString("sourceUpdatedAt")).toInstant()))return selected;if(selected==null)return candidate;
  int order=source.compareTo(OffsetDateTime.parse(selected.getString("sourceUpdatedAt")).toInstant());if(order>0)return candidate;if(order<0)return selected;
  if(!operationPolicy.sameFacts(candidate,selected))throw new Exception("같은 이전 기준 시각의 결과가 달라 저장하지 않았습니다.");if(operationPolicy.resolvesRequery(selected,candidate))return candidate;if(operationPolicy.resolvesRequery(candidate,selected))return selected;return OffsetDateTime.parse(candidate.getString("collectedAt")).isAfter(OffsetDateTime.parse(selected.getString("collectedAt")))?candidate:selected;
 }
 void error(JSONObject q,String message){ContentValues v=new ContentValues();v.put("k",Search.key(q));v.put("message",message);v.put("attempted",System.currentTimeMillis());getWritableDatabase().insertWithOnConflict("errors",null,v,SQLiteDatabase.CONFLICT_REPLACE);}
 String error(String k){try(Cursor c=getReadableDatabase().rawQuery("SELECT message FROM errors WHERE k=?",new String[]{k})){return c.moveToFirst()?c.getString(0):null;}}
 List<JSONObject> collectionTargets()throws Exception{LinkedHashMap<String,JSONObject> items=new LinkedHashMap<>();for(JSONObject s:watches())for(JSONObject q:Search.collectionQueries(s))items.put(Search.key(q),q);return new ArrayList<>(items.values());}
 JSONObject batch(String key){try(Cursor c=getReadableDatabase().rawQuery("SELECT spec FROM batches WHERE k=?",new String[]{key})){return c.moveToFirst()?new JSONObject(c.getString(0)):null;}catch(Exception e){throw new IllegalStateException(e);}}
 void batch(JSONObject batch)throws Exception{ContentValues v=new ContentValues();v.put("k",batch.getString("key"));v.put("spec",batch.toString());getWritableDatabase().insertWithOnConflict("batches",null,v,SQLiteDatabase.CONFLICT_REPLACE);}
 List<JSONObject> pending(boolean force)throws Exception{List<JSONObject> out=new ArrayList<>();for(JSONObject q:collectionTargets()){JSONObject saved=data(Search.key(q));if(force||!SharedCache.collectionFresh(saved,carrier,System.currentTimeMillis(),operationPolicy))out.add(q);}return out;}
}

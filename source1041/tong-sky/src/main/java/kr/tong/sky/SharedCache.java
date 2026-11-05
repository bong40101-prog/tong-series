package kr.tong.sky;

import org.json.*;
import java.io.IOException;
import java.time.*;
import java.util.*;

/** One atomic GitHub document per departure/region. No passenger or account data. */
final class SharedCache {
 static final long LEASE_MS=15*60*1000L;
 record Document(String sha,JSONObject body){}
 record RegionRead(String sha,RegionDocument document) implements AutoCloseable {public void close()throws Exception{if(document!=null)document.close();}}
 static final class Decision implements AutoCloseable {
  final boolean cached,busy;final RegionDocument document;final List<JSONObject> pending;final RegionRead owner;
  Decision(boolean cached,boolean busy,JSONObject body,List<JSONObject> pending){this(cached,busy,RegionDocument.memory(body),pending,null);}
  Decision(boolean cached,boolean busy,RegionDocument document,List<JSONObject> pending,RegionRead owner){this.cached=cached;this.busy=busy;this.document=document;this.pending=pending;this.owner=owner;}
  boolean cached(){return cached;}boolean busy(){return busy;}RegionDocument document(){return document;}List<JSONObject> pending(){return pending;}
  JSONObject body(){return document.memoryBody();}
  long oldestSuccess(JSONArray queries)throws Exception{long oldest=Long.MAX_VALUE;for(int i=0;i<queries.length();i++){JSONObject p=document.get(Search.key(queries.getJSONObject(i)));if(p==null)throw new IOException("공유 조회 자료가 없습니다.");oldest=Math.min(oldest,p.getLong("success"));}return oldest;}
  public void close()throws Exception{try{if(owner!=null)owner.close();}finally{if(owner==null||owner.document()!=document)document.close();}}
 }
 interface Backend {Document read()throws Exception;void write(JSONObject body,String expectedSha)throws Exception;
  default RegionRead openRegion()throws Exception{Document old=read();return new RegionRead(old.sha(),RegionDocument.memory(old.body()));}
  default void writeRegion(RegionDocument document,String expectedSha)throws Exception{write(document.memoryBody(),expectedSha);}
 }
 interface LocalRecords {JSONObject data(String key)throws Exception;}
 static final class Conflict extends IOException {Conflict(){super("저장소가 변경되어 다시 확인합니다.");}}
 final Backend backend;final String origin,region,owner,carrier;final OperationPolicy operationPolicy;
 SharedCache(Backend backend,String origin,String region,String owner){this(backend,origin,region,owner,"KE");}
 SharedCache(Backend backend,String origin,String region,String owner,String carrier){this(backend,origin,region,owner,carrier,OperationPolicy.current());}
 SharedCache(Backend backend,String origin,String region,String owner,String carrier,OperationPolicy policy){this.backend=backend;this.origin=origin;this.region=region;this.owner=owner;this.carrier=RouteRules.carrier(carrier);this.operationPolicy=policy==null?OperationPolicy.EMPTY:policy;}
 static void validateCarrier(JSONObject record,String expected)throws Exception{String actual=record.optString("carrier","KE");if(!expected.equals(actual))throw new IOException("다른 항공사 자료는 저장하거나 재사용할 수 없습니다.");}
 JSONObject empty()throws Exception{return new JSONObject().put("schema",1).put("origin",origin).put("region",region).put("records",new JSONObject());}
 JSONObject checked(JSONObject body,long now)throws Exception{
  if(body==null)return empty();
  if(body.optInt("schema")!=1||!origin.equals(body.optString("origin"))||!region.equals(body.optString("region")))throw new IOException("다른 출발지·지역의 공유 자료입니다.");
  JSONObject records=body.getJSONObject("records");if(records.length()>5000)throw new IOException("공유 자료 개수 초과");
  Iterator<String> keys=records.keys();while(keys.hasNext()){String key=keys.next();JSONObject original=records.getJSONObject(key),normalized=operationPolicy.payload(original);validatePair(key,normalized,now);if(normalized!=original)records.put(key,normalized);}
  return body;
 }
 // Collection reuse ends at the next Korean calendar-day boundary. Imports,
 // app launches and lease writes never extend the original success clock.
 static long expiresAt(long success){if(success<=0)return 0;try{return Instant.ofEpochMilli(success).atZone(Search.KST).toLocalDate().plusDays(1).atStartOfDay(Search.KST).toInstant().toEpochMilli();}catch(DateTimeException|ArithmeticException e){return 0;}}
 static boolean freshTimestamp(long success,long now){return success>0&&success<=now&&now<expiresAt(success);}
 static boolean fresh(JSONObject payload,long now){return payload!=null&&!OperationPolicy.requiresRequery(payload.optJSONObject("current"))&&freshTimestamp(payload.optLong("success",0),now);}
 static boolean collectionFresh(JSONObject payload,long now)throws Exception{return collectionFresh(payload,payload==null?"KE":payload.getJSONObject("current").optString("carrier","KE"),now,OperationPolicy.current());}
 static boolean collectionFresh(JSONObject payload,String carrier,long now,OperationPolicy policy)throws Exception{
  if(payload==null||!freshTimestamp(payload.optLong("success",0),now))return false;if(!"KE".equals(carrier))return fresh(payload,now);JSONObject original=payload.getJSONObject("current");if(RepeatMarkerPolicy.usesCalendar(original)){Search.validateSnapshot(original,original);JSONArray calendar=original.getJSONArray("dates");for(int n=0;n<calendar.length();n++){JSONObject source=RepeatMarkerPolicy.calendarSource(calendar.getJSONObject(n));if(source==null||!Set.of("OBSERVED","UNKNOWN","NO_FLIGHT").contains(source.optString("status")))return false;for(String field:Search.ALL_FIELDS)if(!(source.opt(field) instanceof Boolean)||!"OBSERVED".equals(source.optString("status"))&&source.getBoolean(field))return false;}return true;}JSONObject normalized=policy.payload(payload),snapshot=normalized.getJSONObject("current");List<JSONObject> weeks=OperationSchedule.embedded(snapshot);if(weeks.isEmpty())return false;OperationSchedule.Applied applied=OperationSchedule.applyWithCoverage(snapshot,List.of());Set<String> explicitDates=new HashSet<>();for(JSONObject week:OperationSchedule.union(snapshot,weeks))if(week.getInt("schema")==2)for(int i=0;i<7;i++)explicitDates.add(week.getJSONArray("dates").getJSONObject(i).getString("date"));LocalDate today=Instant.ofEpochMilli(now).atZone(Search.KST).toLocalDate(),limit=Search.lastDate(false,Instant.ofEpochMilli(now).atZone(Search.KST));YearMonth month=YearMonth.parse(snapshot.getString("month"));LocalDate first=month.atDay(1).isBefore(today)?today:month.atDay(1),last=month.atEndOfMonth().isAfter(limit)?limit:month.atEndOfMonth();if(first.isAfter(last))return true;
  JSONArray days=applied.snapshot().getJSONArray("dates");for(int i=0;i<days.length();i++){JSONObject day=days.getJSONObject(i);LocalDate date=LocalDate.parse(day.getString("date"));if(date.isBefore(first)||date.isAfter(last))continue;if(day.optInt("operationSchema")!=2||!day.optBoolean("operationCompleteOperators")||!Set.of("CAPTURED","NO_MARKS").contains(day.optString("flightDetailStatus"))||!explicitDates.contains(day.getString("date"))||!OperationSchedule.SOURCE.equals(day.optString("operationSource"))||!("OPERATING".equals(day.optString("operationStatus"))||"NO_FLIGHT".equals(day.optString("operationStatus")))||day.optBoolean("operationRequiresRequery"))return false;}return true;
 }
 boolean reusable(JSONObject payload,long now)throws Exception{return payload!=null&&RegionScope.allowed(payload.optJSONObject("current"),origin)&&collectionFresh(payload,carrier,now,operationPolicy);}
 List<JSONObject> pending(JSONObject body,JSONArray queries,long now)throws Exception{List<JSONObject> out=new ArrayList<>();JSONObject records=body.getJSONObject("records");for(int i=0;i<queries.length();i++){JSONObject q=queries.getJSONObject(i);validateCarrier(q,carrier);if(RegionScope.allowed(q,origin)&&!reusable(records.optJSONObject(Search.key(q)),now))out.add(q);}return out;}
 boolean otherLease(JSONObject body,long now){JSONObject lease=body.optJSONObject("lease");return lease!=null&&lease.optLong("until")>now&&!owner.equals(lease.optString("owner"));}
 RegionDocument checkedRegion(RegionDocument document,long now)throws Exception {
  if(document==null||document.metadata()==null)return RegionDocument.memory(empty());JSONObject body=document.metadata();
  if(body.optInt("schema")!=1||!origin.equals(body.optString("origin"))||!region.equals(body.optString("region"))||!document.hasRecords())throw new IOException("다른 출발지·지역의 공유 자료입니다.");
  if(document.size()>5000)throw new IOException("공유 자료 개수 초과");for(String key:document.keys()){JSONObject original=document.get(key),normalized=operationPolicy.payload(original);validatePair(key,normalized,now);if(!normalized.toString().equals(original.toString()))document.put(key,normalized);}return document;
 }
 void validatePair(String key,JSONObject p,long now)throws Exception {
  JSONObject c=p.getJSONObject("current");validateCarrier(c,carrier);Search.validateSnapshot(c,c);if(!key.equals(Search.key(c))||!(origin.equals(c.getString("origin"))||origin.equals(c.getString("destination"))))throw new IOException("공유 노선 불일치");
  long t=p.getLong("success"),collected=OffsetDateTime.parse(c.getString("collectedAt")).toInstant().toEpochMilli();if(t<collected||t>now+300000)throw new IOException("공유 수집 시각 오류");JSONObject previous=p.optJSONObject("previous");if(previous!=null){validateCarrier(previous,carrier);Search.validateSnapshot(c,previous);if(!OffsetDateTime.parse(previous.getString("sourceUpdatedAt")).isBefore(OffsetDateTime.parse(c.getString("sourceUpdatedAt"))))throw new IOException("공유 이전 자료 시각 오류");}
 }
 List<JSONObject> pendingRegion(RegionDocument document,JSONArray queries,long now)throws Exception {List<JSONObject> out=new ArrayList<>();for(int i=0;i<queries.length();i++){JSONObject q=queries.getJSONObject(i);validateCarrier(q,carrier);if(RegionScope.allowed(q,origin)&&!reusable(document.get(Search.key(q)),now))out.add(q);}return out;}
 // A held repository-wide lease proves no other current scanner owns a region.
 void recoverRegionLease(ScannerLease global,long now,long elapsed)throws Exception{
  if(global==null||!global.valid(elapsed))throw new IOException("공통 잠금 확인 전에는 지역 잠금을 복구할 수 없습니다.");
  for(int n=0;n<3;n++)try(RegionRead read=backend.openRegion()){
   RegionDocument document=checkedRegion(read.document(),now);try{JSONObject b=document.metadata();if(!otherLease(b,now))return;b.remove("lease");try{writeScopedRegion(document,read.sha());return;}catch(Conflict e){if(n==2)throw e;}}finally{if(document!=read.document())document.close();}
  }
 }
 Decision inspect(JSONArray queries,long now)throws Exception{
  RegionRead read=backend.openRegion();RegionDocument document=null;boolean transferred=false;try{document=checkedRegion(read.document(),now);List<JSONObject> todo=pendingRegion(document,queries,now);Decision result=new Decision(todo.isEmpty(),!todo.isEmpty()&&otherLease(document.metadata(),now),document,todo,read);transferred=true;return result;}finally{if(!transferred){try{read.close();}finally{if(document!=null&&document!=read.document())document.close();}}}
 }
 Decision claim(JSONArray queries,long now)throws Exception{return claim(queries,now,false);}
 // A restarted scanner may have durable local observations to publish even
 // when the remote query list is already fresh. It still needs the same lease.
 Decision claim(JSONArray queries,long now,boolean requireLease)throws Exception{
  for(int n=0;n<3;n++){
   RegionRead read=backend.openRegion();RegionDocument document=null;boolean transferred=false;
   try{document=checkedRegion(read.document(),now);JSONObject b=document.metadata();List<JSONObject> todo=pendingRegion(document,queries,now);boolean cached=todo.isEmpty(),needsLease=!cached||requireLease,busy=needsLease&&otherLease(b,now);
    if(needsLease&&!busy){b.put("lease",new JSONObject().put("owner",owner).put("until",now+LEASE_MS)).put("updatedAt",now);writeScopedRegion(document,read.sha());}
    Decision result=new Decision(cached,busy,document,todo,read);transferred=true;return result;
   }catch(Conflict e){if(n==2)throw e;}finally{if(!transferred){try{read.close();}finally{if(document!=null&&document!=read.document())document.close();}}}
  }throw new IOException("공유 수집 시작 실패");
 }
 private void writeScopedRegion(RegionDocument document,String sha)throws Exception {
  if(RegionScope.excludedRegion(region))throw new IOException("동북아·한국 지역은 공유 대상에서 제외했습니다.");
  for(String key:document.keys()){JSONObject payload=document.get(key);if(payload!=null&&!RegionScope.allowed(payload.optJSONObject("current"),origin))document.remove(key);}
  backend.writeRegion(document,sha);
 }
 private void merge(RegionDocument document,String key,JSONObject payload,long now)throws Exception {
  mergeObservation(document,key,payload,now,false);
 }
 private static final class OwnershipLost extends IOException {OwnershipLost(){super("공유 수집 권한을 다시 확인했으나 일치하지 않아 중단합니다. 기존 자료는 유지합니다.");}}
 private void owned(RegionDocument document)throws OwnershipLost {JSONObject metadata=document==null?null:document.metadata(),lease=metadata==null?null:metadata.optJSONObject("lease");if(lease==null||!owner.equals(lease.optString("owner")))throw new OwnershipLost();}
 // Retry reads only. Never reclaim a foreign lease or write from a stale body.
 private RegionRead openOwnedRegion()throws Exception {
  for(int attempt=0;;attempt++){
   RegionRead read=backend.openRegion();try{owned(read.document());return read;}catch(OwnershipLost e){read.close();if(attempt==2)throw e;Thread.sleep(attempt==0?250:750);}catch(Exception|Error e){read.close();throw e;}
  }
 }
 private void finishCheckpoint(RegionDocument document,String sha,boolean release,long now)throws Exception {checkedRegion(document,now);JSONObject b=document.metadata();if(release)b.remove("lease");else b.put("lease",new JSONObject().put("owner",owner).put("until",now+LEASE_MS));b.put("updatedAt",now);writeScopedRegion(document,sha);}
 void checkpoint(Map<String,JSONObject> saved,boolean release,long now)throws Exception{
  for(int n=0;n<3;n++)try(RegionRead read=openOwnedRegion()){
   RegionDocument document=checkedRegion(read.document(),now);try{owned(document);for(var item:saved.entrySet())merge(document,item.getKey(),item.getValue(),now);try{finishCheckpoint(document,read.sha(),release,now);return;}catch(Conflict e){if(n==2)throw e;}}finally{if(document!=read.document())document.close();}
  }
 }
 /** Publish a bounded durable batch, acknowledging only observations covered
  * by the successful CAS. Source time outranks an unrelated device wall clock. */
 Set<String> checkpointPending(Map<String,JSONObject> saved,boolean release,long now)throws Exception{
  if(saved==null||saved.size()>20)throw new IOException("공유 대기 묶음 크기 오류");
  for(int n=0;n<3;n++)try(RegionRead read=openOwnedRegion()){
   RegionDocument document=checkedRegion(read.document(),now);try{
    owned(document);Set<String> covered=new LinkedHashSet<>();
    for(var item:saved.entrySet()){mergePending(document,item.getKey(),item.getValue(),now);covered.add(item.getKey());}
    try{finishCheckpoint(document,read.sha(),release,now);return covered;}catch(Conflict e){if(n==2)throw e;}
   }finally{if(document!=read.document())document.close();}
  }
  throw new IOException("공유 대기 자료 저장 실패");
 }
 private static int sourceOrder(JSONObject first,JSONObject second)throws Exception{return OffsetDateTime.parse(first.getString("sourceUpdatedAt")).toInstant().compareTo(OffsetDateTime.parse(second.getString("sourceUpdatedAt")).toInstant());}
 private JSONObject latestPrevious(JSONObject chosen,JSONObject candidate,JSONObject current)throws Exception{
  if(candidate==null||sourceOrder(candidate,current)>=0)return chosen;
  if(chosen==null)return candidate;
  int order=sourceOrder(candidate,chosen);
  if(order==0&&!operationPolicy.sameFacts(candidate,chosen))throw new IOException("같은 항공사 기준 시각의 이전 결과가 달라 공유하지 않았습니다.");
  if(order==0&&operationPolicy.resolvesRequery(chosen,candidate))return candidate;if(order==0&&operationPolicy.resolvesRequery(candidate,chosen))return chosen;return order>0?candidate:chosen;
 }
 private void mergePending(RegionDocument document,String key,JSONObject payload,long now)throws Exception{
  mergeObservation(document,key,payload,now,true);
 }
 private void mergeObservation(RegionDocument document,String key,JSONObject payload,long now,boolean allowSuperseded)throws Exception{
  if(key==null||key.length()>160||payload==null)throw new IOException("공유 대기 노선 형식 오류");
  JSONObject original=document.get(key);OperationPolicy.Payloads reconciled=operationPolicy.reconcile(original,payload);JSONObject old=reconciled.first();payload=reconciled.second();String serialized=payload.toString();if(serialized==null||serialized.length()>RegionDocument.MAX_RECORD_BYTES||serialized.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>RegionDocument.MAX_RECORD_BYTES)throw new IOException("공유 대기 노선 자료 크기 초과");
  validatePair(key,payload,now);if(!RegionScope.allowed(payload.getJSONObject("current"),origin)){document.remove(key);return;}if(old!=null){validatePair(key,old,now);if(!old.toString().equals(original.toString()))document.put(key,old);}
  if(old==null){document.put(key,new JSONObject(serialized));return;}
  JSONObject incoming=payload.getJSONObject("current"),remote=old.getJSONObject("current");int order=sourceOrder(incoming,remote);
  // A verified more recent source supersedes this queued observation. Leave
  // its current, history and original success untouched.
  if(order<0){if(allowSuperseded)return;throw new IOException("더 최신인 공유 달력이 있어 이전 자료로 덮어쓰지 않습니다.");}
  if(order==0){
   if(!operationPolicy.sameFacts(incoming,remote))throw new IOException("같은 항공사 기준 시각의 결과가 달라 공유하지 않았습니다.");
   if(operationPolicy.resolvesRequery(incoming,remote))return;if(!operationPolicy.resolvesRequery(remote,incoming)&&payload.getLong("success")<=old.getLong("success"))return;
  }
  JSONObject next=new JSONObject(serialized),previous=null;
  previous=latestPrevious(previous,payload.optJSONObject("previous"),incoming);
  previous=latestPrevious(previous,remote,incoming);
  previous=latestPrevious(previous,old.optJSONObject("previous"),incoming);
  if(previous==null)next.remove("previous");else next.put("previous",new JSONObject(previous.toString()));
  // Preserve unknown record fields while retaining the incoming observation's
  // exact success timestamp. Sharing must not invent a new collection time.
  for(Iterator<String> keys=old.keys();keys.hasNext();){String field=keys.next();if(!next.has(field)&&!"previous".equals(field))next.put(field,old.get(field));}
  validatePair(key,next,now);String merged=next.toString();if(merged.length()>RegionDocument.MAX_RECORD_BYTES||merged.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>RegionDocument.MAX_RECORD_BYTES)throw new IOException("공유 대기 노선 자료 크기 초과");document.put(key,next);
 }
 int reuseLocalRegion(SkyDb db,List<JSONObject> pending,long now)throws Exception{return reuseLocalRegion(db::data,pending,now);}
 int reuseLocalRegion(LocalRecords local,List<JSONObject> pending,long now)throws Exception {
  for(int n=0;n<3;n++)try(RegionRead read=openOwnedRegion()){
   RegionDocument document=checkedRegion(read.document(),now);try{owned(document);int count=0;for(JSONObject q:pending){validateCarrier(q,carrier);String key=Search.key(q);JSONObject payload=local.data(key);if(reusable(payload,now)){merge(document,key,payload,now);count++;}}if(count==0)return 0;try{finishCheckpoint(document,read.sha(),false,now);return count;}catch(Conflict e){if(n==2)throw e;}}finally{if(document!=read.document())document.close();}
  }throw new IOException("휴대폰 공유 자료 재사용 실패");
 }
 // Only seed missing/expired remote records from a still-fresh local observation.
 // checkpoint validates conflicts and preserves the original success timestamp.
 Map<String,JSONObject> localReuseCandidates(SkyDb db,List<JSONObject> pending,long now)throws Exception{return localReuseCandidates(db::data,pending,now);}
 Map<String,JSONObject> localReuseCandidates(LocalRecords local,List<JSONObject> pending,long now)throws Exception{
  Map<String,JSONObject> saved=new LinkedHashMap<>();
  for(JSONObject q:pending){JSONObject p=local.data(Search.key(q));if(reusable(p,now))saved.put(Search.key(q),p);}
  return saved;
 }
 // Compatibility helper for small documents. Collection services stage candidates separately
 // so their previous Decision and its full cache can be released before another backend read.
 Decision reuseLocal(SkyDb db,JSONArray queries,Decision decision,long now)throws Exception{return reuseLocal(db::data,queries,decision,now);}
 Decision reuseLocal(LocalRecords local,JSONArray queries,Decision decision,long now)throws Exception{
  Map<String,JSONObject> saved=localReuseCandidates(local,decision.pending(),now);
  if(saved.isEmpty())return decision;
  decision.close();checkpoint(saved,false,now);
  return inspect(queries,now);
 }
 static void importAll(SkyDb db,JSONObject body)throws Exception{JSONObject records=body.getJSONObject("records");db.getWritableDatabase().beginTransaction();try{Iterator<String> keys=records.keys();while(keys.hasNext()){JSONObject p=records.getJSONObject(keys.next());db.importSnapshot(p.getJSONObject("current"),p);}db.getWritableDatabase().setTransactionSuccessful();}finally{db.getWritableDatabase().endTransaction();}}
 static void importAllRegion(SkyDb db,RegionDocument document)throws Exception{db.getWritableDatabase().beginTransaction();try{for(String key:document.keys()){JSONObject payload=document.get(key);if(RegionScope.allowed(payload.getJSONObject("current"),document.metadata().optString("origin")))db.importSnapshot(payload.getJSONObject("current"),payload);}db.getWritableDatabase().setTransactionSuccessful();}finally{db.getWritableDatabase().endTransaction();}}

}

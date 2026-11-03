package kr.tong.sky;

import android.content.Context;
import android.database.Cursor;
import org.json.*;
import java.time.*;
import java.util.*;

/** Read-only joins over the same projected calendars used by the dashboard. */
final class TripSearch {
 static final String[] REGIONS={"유럽","미주"};
 static final String[] LABELS={"보너스 왕복","보너스＋승급","승급＋보너스"};
 static final String[] IDS={"award-award","award-upgrade","upgrade-award"};
 static final String[] OUT={"prestigeAward","prestigeAward","prestigeUpgrade"};
 static final String[] BACK={"prestigeAward","prestigeUpgrade","prestigeAward"};
 static final class Snapshot {final JSONObject data;final long success;Snapshot(JSONObject data,long success){this.data=data;this.success=success;}}
 static final class Dataset {
  final List<Snapshot> records;final JSONObject catalog;final String origin;final boolean oz;
  Dataset(List<Snapshot> records,JSONObject catalog,String origin,boolean oz){this.records=records;this.catalog=catalog;this.origin=origin;this.oz=oz;}
 }
 static final class Leg {
  final String code,name,country,date;final boolean award,upgrade;final long success;
  Leg(String code,JSONObject city,String date,boolean award,boolean upgrade,long success){this.code=code;this.name=city.optString("name",code);this.country=city.optString("country","");this.date=date;this.award=award;this.upgrade=upgrade;this.success=success;}
  boolean marked(String field){return "prestigeAward".equals(field)?award:upgrade;}
 }
 static final class Match {final Leg out,back;Match(Leg out,Leg back){this.out=out;this.back=back;}}
 static final class Block {
  final List<Leg> out,back;final int start,count;
  Block(List<Leg> out,List<Leg> back,int start){this.out=out;this.back=back;this.start=start;this.count=Math.multiplyExact(out.size(),back.size());}
 }
 static final class Group {
  final int type;final List<Block> blocks=new ArrayList<>();int total;
  Group(int type){this.type=type;}
  void add(List<Leg> out,List<Leg> back){if(out.isEmpty()||back.isEmpty())return;Block block=new Block(out,back,total);blocks.add(block);total=Math.addExact(total,block.count);}
  Match get(int position){if(position<0||position>=total)throw new IndexOutOfBoundsException();int low=0,high=blocks.size()-1;while(low<=high){int mid=(low+high)>>>1;Block b=blocks.get(mid);if(position<b.start)high=mid-1;else if(position>=b.start+b.count)low=mid+1;else{int i=position-b.start;return new Match(b.out.get(i/b.back.size()),b.back.get(i%b.back.size()));}}throw new IndexOutOfBoundsException();}
 }
 static final class Result {final Group[] groups={new Group(0),new Group(1),new Group(2)};int months;long latest;int total(){return groups[0].total+groups[1].total+groups[2].total;}}

 static Dataset load(Context context,String origin,boolean oz)throws Exception{
  JSONObject catalog=RegionScope.filteredCatalog(context,oz);List<Snapshot> raw=new ArrayList<>();
  try(SkyDb db=new SkyDb(context,oz);Cursor rows=db.getReadableDatabase().rawQuery("SELECT data,success FROM snapshots WHERE k LIKE ? OR k LIKE ?",new String[]{origin+"-%","%-"+origin+"-%"})){
   while(rows.moveToNext()){if(Thread.currentThread().isInterrupted())throw new InterruptedException();JSONObject data=new JSONObject(rows.getString(0));if(RegionScope.allowed(data,origin))raw.add(new Snapshot(data,rows.getLong(1)));}
  }
  return prepare(raw,catalog,origin,oz);
 }
 static Dataset prepare(List<Snapshot> raw,JSONObject catalog,String origin,boolean oz)throws Exception{
  ReferenceMarkerRules.Context references=new ReferenceMarkerRules.Context();if(!oz)for(Snapshot r:raw)references.add(r.data,r.success);
  List<Snapshot> projected=new ArrayList<>();for(Snapshot r:raw){if(Thread.currentThread().isInterrupted())throw new InterruptedException();JSONObject data=oz?RouteWeekdayFilter.apply(r.data):RepeatMarkerPolicy.calendar(r.data,catalog,references);if(oz)AsianaData.validate(data,data);projected.add(new Snapshot(data,r.success));}
  return new Dataset(projected,catalog,origin,oz);
 }
 static Result find(Dataset dataset,String region,int days,LocalDate today,LocalDate end)throws Exception{return find(dataset,region,days,today,end,"","");}
 static Result find(Dataset dataset,String region,int days,LocalDate today,LocalDate end,String inCode,String outCode)throws Exception{
  if(inCode==null||outCode==null)throw new IllegalArgumentException("IN·OUT 도시를 선택해 주세요.");
  if(!Arrays.asList(REGIONS).contains(region)||days<1||days>360||today.isAfter(end))throw new IllegalArgumentException("지역과 여행 일수(1~360일)를 선택해 주세요.");
  Map<String,JSONObject> cities=new TreeMap<>();JSONArray entries=dataset.catalog.getJSONObject("regions").optJSONArray(region);if(entries!=null)for(int i=0;i<entries.length();i++){JSONObject city=entries.getJSONObject(i);if(!dataset.origin.equals(city.getString("code")))cities.put(city.getString("code"),city);}
  Map<String,Snapshot> newest=new TreeMap<>();for(Snapshot r:dataset.records){JSONObject d=r.data;String key=Search.key(d);Snapshot previous=newest.get(key);if(previous==null||previous.success<r.success)newest.put(key,r);}
  Map<String,List<Leg>> outgoing=new TreeMap<>(),incoming=new TreeMap<>();Result result=new Result();String start=today.toString(),last=end.toString();
  for(Snapshot snapshot:newest.values()){
   if(Thread.currentThread().isInterrupted())throw new InterruptedException();JSONObject record=snapshot.data;String origin=record.optString("origin"),destination=record.optString("destination");if(!dataset.origin.equals(origin)&&!dataset.origin.equals(destination))continue;
   if(!(dataset.oz?"OZ":"KE").equals(record.optString("carrier","KE")))continue;
   boolean out=dataset.origin.equals(origin);String code=out?destination:origin,month=record.optString("month");JSONObject city=cities.get(code);if(city==null||month.compareTo(start.substring(0,7))<0||month.compareTo(last.substring(0,7))>0)continue;
   if(out&&!inCode.isEmpty()&&!inCode.equals(code)||!out&&!outCode.isEmpty()&&!outCode.equals(code))continue;
   result.months++;result.latest=Math.max(result.latest,snapshot.success);JSONArray dates=record.getJSONArray("dates");
   for(int i=0;i<dates.length();i++){JSONObject day=dates.getJSONObject(i);String date=day.getString("date");if(date.compareTo(start)<0||date.compareTo(last)>0||RepeatMarkerPolicy.excluded(day)||RepeatMarkerPolicy.review(day)||!SeatMarks.verified(day,dataset.oz))continue;
    boolean award=SeatMarks.marked(day,"prestigeAward",dataset.oz),upgrade=SeatMarks.marked(day,"prestigeUpgrade",dataset.oz);if(!award&&!upgrade)continue;
    List<Leg> list=(out?outgoing:incoming).computeIfAbsent(date,k->new ArrayList<>());if(list.stream().noneMatch(x->x.code.equals(code)))list.add(new Leg(code,city,date,award,upgrade,snapshot.success));
   }
  }
  for(List<Leg> legs:outgoing.values())legs.sort(Comparator.comparing(x->x.code));for(List<Leg> legs:incoming.values())legs.sort(Comparator.comparing(x->x.code));
  for(Map.Entry<String,List<Leg>> row:outgoing.entrySet()){String backDate=LocalDate.parse(row.getKey()).plusDays(days-1).toString();if(backDate.compareTo(last)>0)continue;List<Leg> backs=incoming.getOrDefault(backDate,List.of());for(Group group:result.groups){List<Leg> out=new ArrayList<>(),back=new ArrayList<>();for(Leg leg:row.getValue())if(leg.marked(OUT[group.type]))out.add(leg);for(Leg leg:backs)if(leg.marked(BACK[group.type]))back.add(leg);group.add(out,back);}}
  return result;
 }
}

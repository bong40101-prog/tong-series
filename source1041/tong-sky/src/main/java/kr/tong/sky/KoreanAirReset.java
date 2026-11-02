package kr.tong.sky;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.io.File;

/** One-time user-requested reset, before any Activity or collector can start. */
final class KoreanAirReset {
 static final String GENERATION="KE-20261103-1041";
 static final String PREFERENCES="calendar-reset";
 interface Action {void run(SQLiteDatabase db);}
 static synchronized void apply(Context context) {
  SharedPreferences state=context.getSharedPreferences(PREFERENCES,0);
  if(GENERATION.equals(state.getString("completed",null)))return;
  // Open the existing files directly: malformed old observations must not run
  // through SkyDb's operation-policy migration before they can be discarded.
  existing(context,"tong-sky.db",db->{
   for(String table:new String[]{"snapshots","errors","shared_outbox","operation_migrations"})clear(db,table,null,null);
   // Both carriers' collection plans live in the KE metadata database.
   clear(db,"batches","k NOT LIKE ?",new String[]{"OZ-%"});
  });
  existing(context,"tong-sky-repeat-decisions.db",db->clear(db,"decisions",null,null));
  existing(context,"tong-sky-diagnostics.db",db->{
   for(String table:new String[]{"events","active","imported"})clear(db,table,"carrier=?",new String[]{"KE"});
  });
  SharedPreferences times=context.getSharedPreferences("received-times",0);
  SharedPreferences.Editor edit=times.edit();
  for(String key:times.getAll().keySet())if(key.startsWith("KE:"))edit.remove(key);
  if(!edit.commit())throw new IllegalStateException("대한항공 수신 기록 초기화 실패");
  if(!context.getSharedPreferences("remote",0).edit().remove("syncedAt").commit())throw new IllegalStateException("자료 동기화 기록 초기화 실패");
  // Written last. An interrupted reset is retried before new data can be saved.
  if(!state.edit().putString("completed",GENERATION).commit())throw new IllegalStateException("대한항공 초기화 완료 기록 실패");
 }
 private static void existing(Context context,String name,Action action){
  File file=context.getDatabasePath(name);if(!file.exists())return;
  try(SQLiteDatabase db=SQLiteDatabase.openDatabase(file.getPath(),null,SQLiteDatabase.OPEN_READWRITE)){
   db.beginTransaction();try{action.run(db);db.setTransactionSuccessful();}finally{db.endTransaction();}
  }
 }
 private static void clear(SQLiteDatabase db,String table,String where,String[] args){
  try(Cursor cursor=db.rawQuery("SELECT 1 FROM sqlite_master WHERE type='table' AND name=?",new String[]{table})){
   if(cursor.moveToFirst())db.delete(table,where,args);
  }
 }
}

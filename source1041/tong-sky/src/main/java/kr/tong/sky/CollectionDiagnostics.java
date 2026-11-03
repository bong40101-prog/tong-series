package kr.tong.sky;

import android.content.Context;import android.os.SystemClock;import org.json.*;import java.util.UUID;

/** A logging failure is visible but must not change airline results or retry/security decisions. */
final class CollectionDiagnostics implements AutoCloseable {
 final String carrier,session=UUID.randomUUID().toString();ScanJournal journal;String warning="",batch="",stage="prepare",detail="";JSONObject query;long started=0;int done,total;boolean stopRecorded=false;
 CollectionDiagnostics(Context context,String carrier,SkyDb previous){this.carrier=carrier;try{journal=new ScanJournal(context);journal.importLegacy(previous);journal.begin(carrier,session);}catch(Exception e){warning="오류 기록 저장 실패 · 휴대폰 저장 공간을 확인하세요.";}}
 JSONObject event(String type,String code,String message)throws Exception{return DiagnosticEvent.make(carrier,session,batch,query,stage,type,code,message,detail,started==0?0:SystemClock.elapsedRealtime()-started,done,total,System.currentTimeMillis());}
 void start(JSONObject q,String batch,String stage,int done,int total){query=q;this.batch=batch;this.stage=stage;this.done=done;this.total=total;detail="";started=SystemClock.elapsedRealtime();track();}
 void prepare(String batch,int done,int total){query=null;this.batch=batch;stage="prepare";this.done=done;this.total=total;detail="";started=SystemClock.elapsedRealtime();track();}
 void step(String stage){if(this.stage.equals(stage))return;this.stage=stage;detail="";track();}
 void progress(int done,int total){this.done=done;this.total=total;}
 void detail(String detail){this.detail=detail==null?"":detail;}
 void exception(Throwable error){StringBuilder text=new StringBuilder(detail).append('\n').append(error.getClass().getName()).append(": ").append(error.getMessage());StackTraceElement[] trace=error.getStackTrace();for(int i=0;i<Math.min(12,trace.length);i++)text.append("\n  at ").append(trace[i]);detail=DiagnosticEvent.clean(text.toString());}
 void track(){try{if(journal!=null)journal.track(event("attempt","IN_PROGRESS","조회 진행 중"));}catch(Exception e){warning="오류 기록 저장 실패 · 휴대폰 저장 공간을 확인하세요.";}}
 void failure(String code,String message,boolean skipped){try{if(journal!=null)journal.append(event("USER_STOP".equals(code)?"stop":skipped?"skip":"failure",code,message),true);}catch(Exception e){warning="오류 기록 저장 실패 · 휴대폰 저장 공간을 확인하세요.";}}
 void retry(String code,String message){try{if(journal!=null)journal.append(event("retry",code,message),true);}catch(Exception e){warning="오류 기록 저장 실패 · 휴대폰 저장 공간을 확인하세요.";}}
 void stop(String message){if(stopRecorded)return;stopRecorded=true;failure(DiagnosticEvent.code(message),message,false);}
 void success(boolean recovered){try{if(journal!=null)journal.success(event("recovery","RECOVERED","이전 오류 이후 같은 노선·월 조회에 성공했습니다."),recovered);}catch(Exception e){warning="오류 기록 저장 실패 · 휴대폰 저장 공간을 확인하세요.";}}
 void finish(boolean complete,String message){try{if(journal!=null)journal.append(event("session",complete?"COMPLETE":"STOPPED",message),true);}catch(Exception e){warning="오류 기록 저장 실패 · 휴대폰 저장 공간을 확인하세요.";}}
 @Override public void close(){if(journal!=null)journal.close();}
}

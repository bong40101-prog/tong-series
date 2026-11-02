package kr.tong.sky;
import android.app.Application;
import android.content.Context;
/** Installs crash evidence before any Activity or collection Service starts. */
public final class SkyApplication extends Application {
 private CrashCapture.Handler crashHandler;
 @Override protected void attachBaseContext(Context base){super.attachBaseContext(base);WebViewProcessPolicy.configure(this);}
 @Override public void onCreate(){
  super.onCreate();
  if(getPackageName().equals(Application.getProcessName()))KoreanAirReset.apply(this);
  try{crashHandler=CrashCapture.install(this);}catch(RuntimeException ignored){}
  // Only a newly started main process can remove abandoned region spools.
  // Opening the independent AF process must never touch active KE/OZ work.
  try{if(getPackageName().equals(Application.getProcessName()))GitHubStore.cleanupStaleCache(this);}catch(RuntimeException ignored){}
 }
 @Override public void onTerminate(){CrashCapture.uninstall(crashHandler);super.onTerminate();}
}

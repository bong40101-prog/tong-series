package kr.tong.sky;

import android.app.Application;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.*;
import android.util.AtomicFile;
import android.webkit.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.json.*;

/** Debug-only local HTTP fixture wrapper. All navigation/capture/controller work is inherited. */
public final class RemoteAFProbeActivity extends AirFranceActivity {
 static final String REPORT="af132-remote-progress.json",CRASH_FLAG="af132-remote-crash.flag",CRASH_RECEIPT="af132-remote-crash-request.json";
 final Handler reporter=new Handler(Looper.getMainLooper());
 String page="",calendar="",unknown="",day="",fixtureFailure="";
 final Runnable pulse=new Runnable(){public void run(){
  try{File flag=new File(getFilesDir(),CRASH_FLAG);if(flag.exists()){String nonce=read(CRASH_FLAG).trim();if(nonce.matches("[a-f0-9-]{36}")){flag.delete();write(CRASH_RECEIPT,new JSONObject().put("pid",android.os.Process.myPid()).put("nonce",nonce).put("expectedException",OfflineIntentionalCrash.class.getName()));throw new OfflineIntentionalCrash("OFFLINE_AF_CHILD_CRASH_"+nonce);}}}catch(OfflineIntentionalCrash deliberate){throw deliberate;}catch(Exception ignored){}
  report();if(!closed)reporter.postDelayed(this,300);
 }};
 public static final class OfflineIntentionalCrash extends RuntimeException {OfflineIntentionalCrash(String message){super(message);}}
 @Override public void onCreate(Bundle saved){try{JSONObject c=new JSONObject(read("af132-remote-config.json"));day=c.getString("day");page=read("af132-remote-page.html");calendar=read("af132-remote-calendar.json");unknown=read("af132-remote-unknown.json");}catch(Exception e){fixtureFailure="REMOTE_FIXTURE_UNAVAILABLE";}super.onCreate(saved);reporter.post(pulse);if(!fixtureFailure.isEmpty())stopAutomatic(fixtureFailure);}
 String read(String name)throws Exception{File file=new File(getFilesDir(),name);if(file.length()>1048576)throw new IOException("Fixture size");try(InputStream in=new FileInputStream(file)){return new String(in.readAllBytes(),StandardCharsets.UTF_8);}}
 void write(String name,JSONObject value)throws Exception{AtomicFile atomic=new AtomicFile(new File(getFilesDir(),name));FileOutputStream out=null;try{out=atomic.startWrite();out.write(value.toString().getBytes(StandardCharsets.UTF_8));atomic.finishWrite(out);}catch(Exception e){if(out!=null)atomic.failWrite(out);throw e;}}
 void report(){try{JSONObject value=new JSONObject().put("schema",1).put("appVersion",BuildConfig.VERSION_NAME).put("pid",android.os.Process.myPid()).put("processName",Application.getProcessName()).put("resumed",resumed).put("focused",hasWindowFocus()).put("closed",closed).put("browserCreated",browser!=null).put("profileReady",WebViewProcessPolicy.isReadyForAirFrance(getPackageName(),Application.getProcessName())).put("profileDirectoryExists",new File(getDataDir(),"app_webview_airfrance").isDirectory()).put("fixtureFailure",fixtureFailure).put("state",autoState==null?JSONObject.NULL:autoState.json());write(REPORT,value);}catch(Exception ignored){}}
 @Override protected void onResume(){super.onResume();report();}
 @Override protected void onPause(){super.onPause();report();}
 @Override protected void onDestroy(){reporter.removeCallbacksAndMessages(null);super.onDestroy();report();}
 WebResourceResponse local(String mime,int status,String body){return new WebResourceResponse(mime,"UTF-8",status,status==200?"OK":"Bad Request",Map.of("Cache-Control","no-store"),new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));}
 @Override void configure(WebView view){super.configure(view);WebViewClient delegate=view.getWebViewClient();view.setWebViewClient(new WebViewClient(){
  @Override public WebResourceResponse shouldInterceptRequest(WebView v,WebResourceRequest request){Uri u=request.getUrl();String path=u.getPath();if(request.isForMainFrame()){String expected="SEL:C:"+day.replace("-","")+">PAR:C-PAR:C>SEL:C";boolean valid="https".equals(u.getScheme())&&"wwws.airfrance.co.kr".equals(u.getHost())&&"/search/offers".equals(path)&&expected.equals(u.getQueryParameter("connections"))&&"BUSINESS".equals(u.getQueryParameter("cabinClass"))&&"REWARD".equals(u.getQueryParameter("bookingFlow"));return local("text/html",valid?200:400,valid?page:"<!doctype html><title>Offline fixture mismatch</title>");}if("/gql/v1".equals(path)){String kind=u.getQueryParameter("fixtureKind");return local("application/json",200,"calendar".equals(kind)?calendar:unknown);}if("/favicon.ico".equals(path))return local("image/x-icon",200,"");return local("text/plain",200,"");}
  @Override public void onPageStarted(WebView v,String url,Bitmap icon){delegate.onPageStarted(v,url,icon);}
  @Override public void onPageCommitVisible(WebView v,String url){delegate.onPageCommitVisible(v,url);}
  @Override public void onPageFinished(WebView v,String url){delegate.onPageFinished(v,url);}
  @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){return delegate.shouldOverrideUrlLoading(v,r);}
  @Override public boolean shouldOverrideUrlLoading(WebView v,String url){return delegate.shouldOverrideUrlLoading(v,url);}
  @Override public void onReceivedHttpError(WebView v,WebResourceRequest r,WebResourceResponse e){delegate.onReceivedHttpError(v,r,e);}
  @Override public void onReceivedError(WebView v,WebResourceRequest r,WebResourceError e){delegate.onReceivedError(v,r,e);}
  @Override public boolean onRenderProcessGone(WebView v,RenderProcessGoneDetail detail){return delegate.onRenderProcessGone(v,detail);}
 });}
}

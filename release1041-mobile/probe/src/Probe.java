package kr.tong.sky.mobileqa;
import android.app.*;import android.os.*;import android.content.*;import android.webkit.*;import android.media.ImageReader;import android.hardware.display.*;import java.util.concurrent.*;import org.json.*;
public class Probe extends Instrumentation {
 Activity activity;WebView web;ImageReader images;VirtualDisplay display;Presentation presentation;Handler handler;
 String source,stage;int polls;JSONObject result;CountDownLatch done;
 static final String Q="{origin:'ICN',destination:'LHR',month:'2026-11',captureStart:'2026-11-03',captureEnd:'2026-11-04',testSource:'2026-11-02T23:00:00+09:00'}";
 public void onCreate(Bundle b){super.onCreate(b);start();}
 String asset(String name)throws Exception{try(var in=getTargetContext().getAssets().open(name)){return new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);}}
 static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 public void onStart(){Bundle out=new Bundle();try{
  activity=startActivitySync(new Intent(getTargetContext(),ProbeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
  runOnMainSync(()->{handler=new Handler(Looper.getMainLooper());images=ImageReader.newInstance(640,1680,android.graphics.PixelFormat.RGBA_8888,2);images.setOnImageAvailableListener(r->{try(var frame=r.acquireLatestImage()){}},handler);display=activity.getSystemService(DisplayManager.class).createVirtualDisplay("TongSKY mobile adapter QA",640,1680,320,images.getSurface(),DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY|DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION);presentation=new Presentation(activity,display.getDisplay());web=new WebView(presentation.getContext());web.getSettings().setJavaScriptEnabled(true);web.getSettings().setBlockNetworkLoads(true);presentation.setContentView(web);presentation.show();web.onResume();});
  runCase("baseline.js","normal",false,"parse_flights");runCase("candidate.js","normal",true,"");runCase("candidate.js","no-confirm",false,"parse_flights_confirm");runCase("candidate.js","bad-date",false,"parse_flights");
  out.putString("stream","PASS: API35 WebView 320css private-display mobile lifecycle; baseline reproduces wait; candidate captures two dates; unconfirmed and wrong-date panels never save\n");finish(Activity.RESULT_OK,out);
 }catch(Throwable e){e.printStackTrace();out.putString("stream","FAIL: "+e);finish(Activity.RESULT_CANCELED,out);}finally{runOnMainSync(()->{if(web!=null)web.destroy();if(presentation!=null)presentation.dismiss();if(display!=null)display.release();if(images!=null)images.close();if(activity!=null)activity.finish();});}}
 void runCase(String script,String mode,boolean success,String expectedStage)throws Exception{
  source=asset(script);String html=asset("fixture.html").replace("window.actions=","window.testMode="+JSONObject.quote(mode)+";window.actions=");stage="parse";polls=0;result=null;done=new CountDownLatch(1);
  runOnMainSync(()->{web.setWebViewClient(new WebViewClient(){public void onPageFinished(WebView v,String url){handler.postDelayed(()->tick(),150);}});web.loadDataWithBaseURL("https://offline.invalid/",html,"text/html","UTF-8",null);});
  check(done.await(25,TimeUnit.SECONDS),"probe deadline: "+script+" "+mode);check(result!=null,"missing result");check(!result.has("error"),result.toString());
  if(success){check(result.has("data"),result.toString());JSONArray dates=result.getJSONObject("data").getJSONArray("dates");for(int i=2;i<=3;i++){JSONObject day=dates.getJSONObject(i);check("CAPTURED".equals(day.optString("flightDetailStatus")),day.toString());check(day.getJSONArray("flights").length()==1,day.toString());check(day.getJSONArray("flights").getJSONObject(0).getBoolean("prestigeUpgrade"),day.toString());}check(result.getInt("confirms")==2,"confirmation count");}
  else{check(!result.has("data"),"unsafe snapshot");check(expectedStage.equals(stage),stage+" "+result);if("baseline.js".equals(script))check(result.optString("detail").contains("선택 날짜 반영"),result.toString());}
  check(result.getInt("width")==320,"wrong private-display width: "+result);System.out.println("MOBILE_CASE "+script+" "+mode+" "+result);
 }
 void tick(){if(done.getCount()==0)return;String js="JSON.stringify((()=>{const r=("+source+")("+JSONObject.quote(stage)+","+Q+");r.width=innerWidth;r.confirms=window.actions.confirms;return r;})())";web.evaluateJavascript(js,raw->{try{result=new JSONObject(new JSONArray("["+raw+"]").getString(0));polls++;if(result.has("error")||result.has("data")||polls>=18){done.countDown();return;}if(result.has("next"))stage=result.getString("next");handler.postDelayed(()->tick(),300);}catch(Exception e){try{result=new JSONObject().put("error",e.toString());}catch(Exception ignored){}done.countDown();}});}
}

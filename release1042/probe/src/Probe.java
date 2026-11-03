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
  runCase("baseline135.js","normal",true,"");runCase("baseline.js","normal",false,"parse_flights");runCase("candidate.js","normal",true,"");runCase("candidate.js","no-confirm",true,"");runCase("candidate.js","bad-date",true,"");
  runReuseCases();
  out.putString("stream","MOBILE_REUSE_PASS: 16 cases / 105 assertions / 320 CSS px\nPASS: API35 WebView 320css private-display mobile lifecycle; baseline reproduces wait; 1.0.35 and candidate save monthly marks without any date click or detail confirmation\n");finish(Activity.RESULT_OK,out);
 }catch(Throwable e){e.printStackTrace();out.putString("stream","FAIL: "+e);finish(Activity.RESULT_CANCELED,out);}finally{runOnMainSync(()->{if(web!=null)web.destroy();if(presentation!=null)presentation.dismiss();if(display!=null)display.release();if(images!=null)images.close();if(activity!=null)activity.finish();});}}
 void runReuseCases()throws Exception{
  String harness=asset("reuse-harness.js");String js="JSON.stringify((()=>{"+harness+";return testKoreanAirReuse("+JSONObject.quote(asset("candidate.js"))+","+JSONObject.quote(asset("baseline135.js"))+","+JSONObject.quote(asset("december.html"))+","+JSONObject.quote(asset("january.html"))+","+JSONObject.quote(asset("unsupported.html"))+");})())";
  CountDownLatch ready=new CountDownLatch(1);final String[] response={null};
  runOnMainSync(()->{web.setWebViewClient(new WebViewClient(){public void onPageFinished(WebView v,String url){web.evaluateJavascript(js,raw->{response[0]=raw;ready.countDown();});}});web.loadDataWithBaseURL("https://www.koreanair.com/booking/book-and-manage/award-seat-availability","<html><head><meta name=viewport content='width=device-width,initial-scale=1'><style>html,body{margin:0;width:100%;overflow-x:hidden;font:12px sans-serif}*{box-sizing:border-box;min-width:0;overflow-wrap:anywhere}table{width:100%;table-layout:fixed}td{height:35px}.bonus-calendar__icons{padding:0;font-size:8px}button{max-width:100%;padding:8px}</style></head><body></body></html>","text/html","UTF-8",null);});
  check(ready.await(30,TimeUnit.SECONDS),"reuse mobile deadline");JSONObject r=new JSONObject(new JSONArray("["+response[0]+"]").getString(0));check(r.getInt("cases")==16&&r.getInt("assertions")==105&&r.getInt("width")==320,"reuse browser cases: "+r);System.out.println("MOBILE_REUSE_PASS "+r);
 }
 void runCase(String script,String mode,boolean success,String expectedStage)throws Exception{
  source=asset(script);String html=asset("fixture.html").replace("window.actions=","window.testMode="+JSONObject.quote(mode)+";window.actions=");stage="parse";polls=0;result=null;done=new CountDownLatch(1);
  runOnMainSync(()->{web.setWebViewClient(new WebViewClient(){public void onPageFinished(WebView v,String url){handler.postDelayed(()->tick(),150);}});web.loadDataWithBaseURL("https://offline.invalid/",html,"text/html","UTF-8",null);});
  check(done.await(25,TimeUnit.SECONDS),"probe deadline: "+script+" "+mode);check(result!=null,"missing result");check(!result.has("error"),result.toString());
  if(success){check(result.has("data"),result.toString());JSONArray dates=result.getJSONObject("data").getJSONArray("dates");for(int i=2;i<=3;i++){JSONObject day=dates.getJSONObject(i);check(day.getBoolean("economyAward")&&day.getBoolean("prestigeUpgrade"),day.toString());check(!day.has("flightDetailStatus")&&!day.has("flights"),"unexpected detail data");}check(result.getInt("confirms")==0&&result.getInt("clicks")==0,"monthly collection must not open a date or confirm details");}
  
  else{check(!result.has("data"),"unsafe snapshot");check(expectedStage.equals(stage),stage+" "+result);if("baseline.js".equals(script))check(result.optString("detail").contains("선택 날짜 반영"),result.toString());}
  check(result.getInt("width")==320,"wrong private-display width: "+result);System.out.println("MOBILE_CASE "+script+" "+mode+" "+result);
 }
 void tick(){if(done.getCount()==0)return;String js="JSON.stringify((()=>{const r=("+source+")("+JSONObject.quote(stage)+","+Q+");r.width=innerWidth;r.confirms=window.actions.confirms;r.clicks=window.actions.clicks;return r;})())";web.evaluateJavascript(js,raw->{try{result=new JSONObject(new JSONArray("["+raw+"]").getString(0));polls++;if(result.has("error")||result.has("data")||polls>=18){done.countDown();return;}if(result.has("next"))stage=result.getString("next");handler.postDelayed(()->tick(),300);}catch(Exception e){try{result=new JSONObject().put("error",e.toString());}catch(Exception ignored){}done.countDown();}});}
}

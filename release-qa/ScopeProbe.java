import java.lang.reflect.*;
import java.util.*;
import java.io.*;
import java.util.zip.*;
import org.json.*;
public class ScopeProbe {
 static final String[] F={"economyAward","premiumAward","prestigeAward","premiumUpgrade","prestigeUpgrade","firstCombined"};
 static void ok(boolean b,String m){if(!b)throw new AssertionError(m);}
 static JSONObject entry(ZipFile z,String name)throws Exception{try(InputStream in=z.getInputStream(z.getEntry("assets/"+name))){ByteArrayOutputStream b=new ByteArrayOutputStream();byte[] t=new byte[8192];int n;while((n=in.read(t))>0)b.write(t,0,n);return new JSONObject(b.toString("UTF-8"));}}
 public static void main(String[] args)throws Exception{
  JSONObject cat,regions;try(ZipFile z=new ZipFile(args[0])){cat=entry(z,"repeat-marker-catalog.json");regions=entry(z,"regions.json").getJSONObject("regions");}
  Class<?> p=Class.forName("kr.tong.sky.RepeatMarkerPolicy");Method cal=p.getDeclaredMethod("calendar",JSONObject.class,JSONObject.class),scope=p.getDeclaredMethod("scope",JSONObject.class,JSONObject.class);cal.setAccessible(true);scope.setAccessible(true);
  int tests=0,canada=0,control=0;Set<String> routeSet=new HashSet<>();for(String r:new String[]{"미주","유럽"}){JSONArray cities=regions.getJSONArray(r);for(int i=0;i<cities.length();i++){String city=cities.getJSONObject(i).getString("code");boolean ca=city.equals("YVR")||city.equals("YYZ");String expect=ca?"CA":r.equals("유럽")?"EUROPE":"US";
  for(int direction=0;direction<2;direction++){String o=direction==0?"ICN":city,d=direction==0?city:"ICN";ok(routeSet.add(o+"-"+d),"duplicate");if(ca)canada++;
  for(int mode=0;mode<3;mode++){JSONObject raw=new JSONObject().put("carrier","KE").put("origin",o).put("destination",d).put("month","2027-02");JSONArray dates=new JSONArray();for(int n=1;n<=28;n++){boolean sun=n%7==0,unknown=mode==2&&n==14,marked=sun&&!(mode==1&&n==14)&&!unknown;JSONObject a=new JSONObject().put("date",String.format(Locale.ROOT,"2027-02-%02d",n)).put("status",unknown?"UNKNOWN":"OBSERVED");for(String f:F)a.put(f,marked&&(f.equals(F[0])||f.equals(F[2])||f.equals(F[4])));dates.put(a);}raw.put("dates",dates);String before=raw.toString();ok(expect.equals(scope.invoke(null,raw,cat)),"scope:"+o+d);JSONObject view=(JSONObject)cal.invoke(null,raw,cat);ok(before.equals(raw.toString()),"mutated source");JSONArray cells=view.getJSONArray("dates");ok(cells.length()==28,"count");for(int n=1;n<=28;n++){JSONObject a=cells.getJSONObject(n-1);boolean sun=n%7==0,ex=sun&&mode==0,review=sun&&mode>0;ok(a.has("repeatMarkerExclusion")==ex&&a.has("repeatMarkerReview")==review,"marker:"+o+d+"/"+mode+"/"+n);if(ex){for(String f:F)ok(!a.getBoolean(f),"false availability");}else{for(String f:F)ok(a.getBoolean(f)==dates.getJSONObject(n-1).getBoolean(f),"changed original flag");}if(mode==2&&n==14)ok("UNKNOWN".equals(a.getString("status")),"missing data promoted");}tests++;
  raw.put("carrier","OZ");ok(scope.invoke(null,raw,cat)==null,"KE inference leaked to OZ");control++;}
  }}}ok(routeSet.size()==52&&canada==4&&tests==156&&control==156,"route coverage");System.out.println("PASS released DEX: 52 directions, 4 Canada directions, 156 synthetic calendars, 156 OZ carrier-isolation controls");
 }
}

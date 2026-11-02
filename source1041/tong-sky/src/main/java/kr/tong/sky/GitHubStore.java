package kr.tong.sky;

import android.content.Context;
import android.util.Base64;
import org.json.*;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;

final class GitHubStore implements SharedCache.Backend {
 static final String REPO="bong40101-prog/tong-sky-data",BRANCH="tong-sky-data",API="https://api.github.com/repos/"+REPO;
 static SharedCache.Backend testBackend;
 static final java.util.Map<String,SharedCache.Backend> testBackends=new java.util.concurrent.ConcurrentHashMap<>();
 static boolean hasTestBackend(String region){return testBackend!=null||testBackends.containsKey(region);}
 interface Connections {HttpURLConnection open(URL url)throws IOException;}
 interface Pause {void waitFor(long millis)throws InterruptedException;}
 static final int NETWORK_ATTEMPTS=3;
 final String path,token;final Connections connections;final Pause pause;
 private File cacheDirectory;
 static final int MAX_COMPAT_BYTES=512*1024,MAX_TRANSPORT_BYTES=48*1024*1024;
 static final int MAX_PLAIN_BYTES=32*1024*1024,MAX_JSON_DEPTH=64;
 /** Encode into gzip incrementally instead of retaining the complete JSON string and UTF-8 copy. */
 static JSONObject packed(JSONObject body)throws Exception{
  ByteArrayOutputStream out=new ByteArrayOutputStream();
  try(Writer writer=new OutputStreamWriter(new PlainLimitOutputStream(new java.util.zip.GZIPOutputStream(out)),StandardCharsets.UTF_8)){writeJson(writer,body,0);}
  return new JSONObject().put("encoding","gzip-base64").put("data",Base64.encodeToString(out.toByteArray(),Base64.NO_WRAP));
 }
 private static final class PlainLimitOutputStream extends FilterOutputStream {
  int count;final int limit;
  PlainLimitOutputStream(OutputStream out){this(out,MAX_PLAIN_BYTES);}
  PlainLimitOutputStream(OutputStream out,int limit){super(out);this.limit=limit;}
  void reserve(int size)throws IOException{if(size>limit-count)throw new IOException("공유 저장 자료 크기 초과");count+=size;}
  @Override public void write(int value)throws IOException{reserve(1);out.write(value);}
  @Override public void write(byte[] bytes,int offset,int size)throws IOException{reserve(size);out.write(bytes,offset,size);}
 }
 private static void writeJson(Writer writer,Object value,int depth)throws Exception{
  if(value==null||value==JSONObject.NULL){writer.write("null");return;}
  if(value instanceof JSONObject object){
   if(depth>=MAX_JSON_DEPTH)throw new IOException("공유 저장 자료 중첩 깊이 초과");
   writer.write('{');boolean first=true;java.util.Iterator<String> keys=object.keys();
   while(keys.hasNext()){String key=keys.next();if(!first)writer.write(',');first=false;writer.write(JSONObject.quote(key));writer.write(':');writeJson(writer,object.get(key),depth+1);}
   writer.write('}');return;
  }
  if(value instanceof JSONArray array){
   if(depth>=MAX_JSON_DEPTH)throw new IOException("공유 저장 자료 중첩 깊이 초과");
   writer.write('[');for(int i=0;i<array.length();i++){if(i>0)writer.write(',');writeJson(writer,array.opt(i),depth+1);}writer.write(']');return;
  }
  if(value instanceof Number number){writer.write(JSONObject.numberToString(number));return;}
  if(value instanceof Boolean bool){writer.write(bool.toString());return;}
  // JSONObject's standard quoting also preserves control-character and Unicode escaping.
  writer.write(JSONObject.quote(String.valueOf(value)));
 }
 static JSONObject unpack(JSONObject body)throws Exception{if(!body.optString("encoding").equals("gzip-base64"))return body;byte[] compressed=Base64.decode(body.getString("data"),Base64.DEFAULT);try(InputStream in=new RemoteCache.Bounded(new java.util.zip.GZIPInputStream(new ByteArrayInputStream(compressed)),32*1024*1024)){return new JSONObject(new String(in.readAllBytes(),StandardCharsets.UTF_8));}}
 GitHubStore(String origin,String regionId,String token){this(origin,regionId,token,url->(HttpURLConnection)url.openConnection(),Thread::sleep);}
 GitHubStore(String origin,String regionId,String token,Connections connections,Pause pause){if(!origin.matches("[A-Z]{3}")||!regionId.matches("[a-z_]+"))throw new IllegalArgumentException("공유 저장 경로 오류");path=(regionId.equals("scanner_lock")?"data/mobile/v1/" : (regionId.equals("asiana")||regionId.startsWith("asiana_"))?"data/mobile/v101/":"data/mobile/v103/")+origin+"/"+regionId+".json";this.token=token;this.connections=connections;this.pause=pause;}
 GitHubStore usingCache(File directory){cacheDirectory=directory;return this;}
 /** Main-process Application startup only, before any cache workers can own a spool. */
 static void cleanupStaleCache(Context context){try{cleanupStaleCache(context.getCacheDir(),android.app.Application.getProcessName(),context.getPackageName());}catch(Exception ignored){}}
 static void cleanupStaleCache(File cache,String process,String packageName){
  if(cache==null||packageName==null||!packageName.equals(process))return;File directory=new File(cache,"tong-sky-shared");File[] files=directory.listFiles();if(files==null)return;
  for(File file:files)if((file.getName().startsWith("region-")||file.getName().startsWith("tong-region-"))&&java.nio.file.Files.isRegularFile(file.toPath(),java.nio.file.LinkOption.NOFOLLOW_LINKS))file.delete();
 }
 private File temporary(String suffix)throws IOException{File root=cacheDirectory==null?new File(System.getProperty("java.io.tmpdir")):cacheDirectory;File directory=new File(root,"tong-sky-shared");if(!directory.isDirectory()&&!directory.mkdirs())throw new IOException("공유 자료 임시 저장 공간을 만들 수 없습니다.");return File.createTempFile("region-",suffix,directory);}
 HttpURLConnection connection(String endpoint,String method)throws Exception{HttpURLConnection c=connections.open(new URL(API+endpoint));c.setConnectTimeout(15000);c.setReadTimeout(25000);c.setInstanceFollowRedirects(false);c.setUseCaches(false);c.setRequestMethod(method);c.setRequestProperty("User-Agent","TongSKY-Android/"+BuildConfig.VERSION_NAME);c.setRequestProperty("Accept","application/vnd.github+json");c.setRequestProperty("X-GitHub-Api-Version","2022-11-28");if(token!=null&&!token.isEmpty())c.setRequestProperty("Authorization","Bearer "+token);return c;}
 static String failure(int status,String method,String message,String remaining,String retryAfter,String reset,long now){
  String action="GET".equals(method)?"자료 읽기":"저장",m=message==null?"":message.toLowerCase(java.util.Locale.ROOT);
  if(status==401)return "GitHub 인증 실패(401) · 토큰이 만료·폐기됐거나 잘못 입력됐습니다. 설정 → GitHub 저장 연결에서 새 토큰을 연결하세요.";
  if(status==429||status==403&&("0".equals(remaining)||retryAfter!=null||m.contains("rate limit")||m.contains("abuse detection"))){long seconds=0;try{seconds=Long.parseLong(retryAfter);}catch(Exception ignored){}if(seconds<=0)try{seconds=Long.parseLong(reset)-now/1000;}catch(Exception ignored){}return "GitHub 요청 횟수 제한("+status+") · 대한항공 차단이 아닙니다."+(seconds>0?" 약 "+Math.max(1,(seconds+59)/60)+"분 후 다시 시도하세요.":" 잠시 후 다시 시도하세요.")+" 자동 재시도하지 않습니다.";}
  if(status==403){if(m.contains("user agent")||m.contains("user-agent"))return "GitHub 클라이언트 식별 거부(403) · 통SKY를 최신 버전으로 교체하세요.";return "GitHub "+action+" 접근 거부(403) · 토큰의 소유자 bong40101-prog, 선택 저장소 tong-sky-data, Contents: Read and write 권한을 확인하세요. 계정·저장소 정책에 의해서도 거부될 수 있습니다. 대한항공 조회 전 중단됐습니다.";}
  return "GitHub "+action+" 연결 실패(HTTP "+status+") · 기존 달력은 유지합니다.";
 }
 JSONObject response(HttpURLConnection c)throws Exception{int status=c.getResponseCode();if(status==409||status==422)throw new SharedCache.Conflict();if(status!=200&&status!=201){String message="";try(InputStream raw=c.getErrorStream()){if(raw!=null)try(InputStream in=new RemoteCache.Bounded(raw,65536)){message=new JSONObject(new String(in.readAllBytes(),StandardCharsets.UTF_8)).optString("message");}}catch(Exception ignored){}throw new HttpFailure(failure(status,c.getRequestMethod(),message,c.getHeaderField("X-RateLimit-Remaining"),c.getHeaderField("Retry-After"),c.getHeaderField("X-RateLimit-Reset"),System.currentTimeMillis()));}try(InputStream in=new RemoteCache.Bounded(c.getInputStream(),48*1024*1024)){return new JSONObject(new String(in.readAllBytes(),StandardCharsets.UTF_8));}}
 // Retry only interrupted transport operations. Server denials, rate limits,
 // certificate errors and malformed data are never retried.
 static final class HttpFailure extends IOException {HttpFailure(String message){super(message);}}
 static boolean transientFailure(IOException error){
  if(error instanceof HttpFailure||error instanceof SharedCache.Conflict||error instanceof javax.net.ssl.SSLException||error instanceof ProtocolException)return false;
  if(error instanceof EOFException||error instanceof SocketTimeoutException||error instanceof ConnectException||error instanceof UnknownHostException||error instanceof NoRouteToHostException)return true;
  String message=error.getMessage();if(message==null)return false;message=message.toLowerCase(java.util.Locale.ROOT);
  return message.contains("unexpected end of stream")||message.contains("connection reset")||message.contains("connection aborted")||message.contains("broken pipe");
 }
 static String blobSha(byte[] content)throws Exception{java.security.MessageDigest digest=java.security.MessageDigest.getInstance("SHA-1");digest.update(("blob "+content.length+"\0").getBytes(StandardCharsets.UTF_8));byte[] hash=digest.digest(content);StringBuilder out=new StringBuilder();for(byte value:hash)out.append(String.format(java.util.Locale.ROOT,"%02x",value&255));return out.toString();}
 static void verifyBlob(byte[] content,String expectedSha)throws Exception{if(!blobSha(content).equals(expectedSha))throw new IOException("GitHub 저장 파일 내용 검증 실패 · 기존 달력은 유지합니다.");}
 boolean committed(String contentSha)throws Exception{
  if((path.startsWith("data/mobile/v101/")||path.startsWith("data/mobile/v103/")))try(Blob latest=download("/contents/"+path+"?ref="+BRANCH,true)){return latest!=null&&contentSha.equals(latest.sha);}
  JSONObject latest=request("/contents/"+path+"?ref="+BRANCH,"GET",null,true,null);return latest!=null&&contentSha.equals(latest.optString("sha"));
 }
 JSONObject request(String endpoint,String method,byte[] data,boolean missing,String contentSha)throws Exception{
  boolean uncertainWrite=false;
  for(int attempt=0;attempt<NETWORK_ATTEMPTS;attempt++){
   if(Thread.currentThread().isInterrupted())throw new InterruptedException();
   HttpURLConnection c=null;
   try{
    c=connection(endpoint,method);
    if(data!=null){c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json; charset=utf-8");c.setFixedLengthStreamingMode(data.length);try(OutputStream out=c.getOutputStream()){out.write(data);}}
    if(missing&&c.getResponseCode()==404)return null;
    return response(c);
   }catch(SharedCache.Conflict conflict){
    // A lost PUT response may mean GitHub already committed this exact blob.
    // Never drop the expected-SHA precondition or overwrite a newer document.
    if(uncertainWrite&&contentSha!=null&&committed(contentSha))return new JSONObject();
    throw conflict;
   }catch(IOException error){
    if(!transientFailure(error)||Thread.currentThread().isInterrupted())throw error;
    uncertainWrite=data!=null;
    if(attempt==NETWORK_ATTEMPTS-1){
     if(uncertainWrite&&contentSha!=null&&committed(contentSha))return new JSONObject();
     throw new IOException("GitHub 연결이 일시적으로 끊겨 3회 확인 후 중단했습니다. 완료한 휴대폰 자료는 유지되며 다시 검색하면 이어집니다.",error);
    }
   }finally{if(c!=null)c.disconnect();}
   pause.waitFor(attempt==0?300:900);
  }
  throw new IOException("GitHub 연결 확인 실패 · 완료한 자료는 유지합니다.");
 }
 JSONObject required(String endpoint)throws Exception{return request(endpoint,"GET",null,false,null);}
 void verifyConnection()throws Exception{if(token==null||!token.matches("[A-Za-z0-9_]{20,512}"))throw new IOException("GitHub에서 발급한 토큰 전체를 입력하세요.");JSONObject repo=required("");if(!REPO.equals(repo.optString("full_name")))throw new IOException("토큰의 선택 저장소에 tong-sky-data를 포함하세요.");JSONObject ref=required("/git/ref/heads/"+BRANCH);required("/git/trees/"+ref.getJSONObject("object").getString("sha"));}

 /** The JSON API's base64 strings are spooled rather than returned by nextString(). */
 private static final class Envelope implements Closeable {
  String sha,encoding;boolean value;final File literal;
  Envelope(File literal){this.literal=literal;}
  @Override public void close(){literal.delete();}
 }
 private static final class Capture extends Writer {
  final StringBuilder text=new StringBuilder();final int limit;boolean overflow;
  Capture(int limit){this.limit=limit;}
  @Override public void write(char[] chars,int off,int length){for(int i=0;i<length;i++){if(text.length()<limit)text.append(chars[off+i]);else overflow=true;}}
  @Override public void write(int value){if(text.length()<limit)text.append((char)value);else overflow=true;}
  String value(){return overflow?null:text.toString();}
  @Override public void flush(){}@Override public void close(){}
 }
 /** Strict bounded-memory envelope scanner. Unknown JSON values are validated and skipped. */
 private static final class JsonStream {
  final Reader reader;int next=-2;
  JsonStream(Reader reader){this.reader=reader;}
  int peek()throws IOException{if(next==-2)next=reader.read();return next;}
  int take()throws IOException{int value=peek();next=-2;return value;}
  IOException malformed(){return new IOException("GitHub JSON 자료 형식 오류 · 기존 달력은 유지합니다.");}
  void space()throws IOException{while(peek()==32||peek()==9||peek()==10||peek()==13)take();}
  void expect(int value)throws IOException{space();if(take()!=value)throw malformed();}
  void string(Writer sink)throws IOException{
   expect('"');for(;;){int value=take();if(value<0||value<32)throw malformed();if(value=='"')return;
    if(value=='\\'){int escape=take();switch(escape){case '"':case '\\':case '/':value=escape;break;case 'b':value='\b';break;case 'f':value='\f';break;case 'n':value='\n';break;case 'r':value='\r';break;case 't':value='\t';break;case 'u':value=0;for(int i=0;i<4;i++){int h=Character.digit((char)take(),16);if(h<0)throw malformed();value=(value<<4)|h;}break;default:throw malformed();}}
    if(sink!=null)sink.write(value);
   }
  }
  String name()throws IOException{Capture c=new Capture(256);string(c);return c.value();}
  void skip(int depth)throws IOException{
   if(depth>MAX_JSON_DEPTH)throw new IOException("공유 저장 자료 중첩 깊이 초과");space();int value=peek();
   if(value=='"'){string(null);return;}
   if(value=='{'||value=='['){int close=value=='{'?'}':']';take();space();if(peek()==close){take();return;}for(;;){if(value=='{'){string(null);expect(':');}skip(depth+1);space();int delimiter=take();if(delimiter==close)return;if(delimiter!=',')throw malformed();} }
   if(value=='t'||value=='f'||value=='n'){String token=value=='t'?"true":value=='f'?"false":"null";for(int i=0;i<token.length();i++)if(take()!=token.charAt(i))throw malformed();return;}
   if(peek()=='-')take();value=take();if(value=='0'){if(peek()>='0'&&peek()<='9')throw malformed();}else{if(value<'1'||value>'9')throw malformed();while(peek()>='0'&&peek()<='9')take();}
   if(peek()=='.'){take();if(peek()<'0'||peek()>'9')throw malformed();while(peek()>='0'&&peek()<='9')take();}
   if(peek()=='e'||peek()=='E'){take();if(peek()=='+'||peek()=='-')take();if(peek()<'0'||peek()>'9')throw malformed();while(peek()>='0'&&peek()<='9')take();}
  }
  void envelope(Envelope result,String target,Writer literal,boolean api)throws IOException{
   space();if(peek()==0xfeff)take();expect('{');space();java.util.Set<String> seen=new java.util.HashSet<>();
   if(peek()=='}')take();else for(;;){String key=name();expect(':');space();boolean selected=target.equals(key)||"encoding".equals(key)||(api&&"sha".equals(key));
    if(selected&&!seen.add(key))throw malformed();
    if(selected&&peek()=='"'){
     if(target.equals(key)){string(literal);result.value=true;}else{Capture capture=new Capture(api?128:32);string(capture);if(api&&capture.overflow)throw malformed();if("encoding".equals(key))result.encoding=capture.value();else result.sha=capture.value();}
    }else{if(api&&("encoding".equals(key)||"sha".equals(key)))throw malformed();skip(1);}
    space();int delimiter=take();if(delimiter=='}')break;if(delimiter!=',')throw malformed();
   }
   space();if(take()!=-1)throw malformed();
  }
 }
 private Envelope envelope(InputStream input,String target,boolean api)throws Exception{
  Envelope result=new Envelope(temporary(".literal"));boolean success=false;
  try{try(Reader reader=new BufferedReader(new InputStreamReader(new RemoteCache.Bounded(input,MAX_TRANSPORT_BYTES),StandardCharsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT).onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)));Writer out=new BufferedWriter(new OutputStreamWriter(new PlainLimitOutputStream(new FileOutputStream(result.literal),MAX_TRANSPORT_BYTES),StandardCharsets.UTF_8))){new JsonStream(reader).envelope(result,target,out,api);}success=true;return result;}finally{if(!success)result.close();}
 }
 private static void copy(InputStream input,OutputStream output)throws IOException{byte[] buffer=new byte[8192];for(int count;(count=input.read(buffer))!=-1;){if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("공유 자료 작업 중단");output.write(buffer,0,count);}}
 private static final class Blob implements Closeable {
  final String sha,encoding;final File file;
  Blob(String sha,String encoding,File file){this.sha=sha;this.encoding=encoding;this.file=file;}
  @Override public void close(){if(file!=null)file.delete();}
 }
 private Blob download(String endpoint,boolean missing)throws Exception{
  for(int attempt=0;attempt<NETWORK_ATTEMPTS;attempt++){
   if(Thread.currentThread().isInterrupted())throw new InterruptedException();HttpURLConnection connection=null;
   try{
    connection=connection(endpoint,"GET");int status=connection.getResponseCode();if(missing&&status==404)return null;
    if(status!=200&&status!=201){response(connection);throw new IOException("GitHub 자료 읽기 실패");}
    try(Envelope envelope=envelope(connection.getInputStream(),"content",true)){
     if(envelope.sha==null)throw new IOException("GitHub 저장 파일 검증 실패");
     if(!"base64".equals(envelope.encoding))return new Blob(envelope.sha,envelope.encoding,null);
     if(!envelope.value)throw new IOException("GitHub 저장 파일 내용 누락");File file=temporary(".blob");boolean success=false;
     try{try(InputStream in=new android.util.Base64InputStream(new FileInputStream(envelope.literal),Base64.DEFAULT);OutputStream out=new PlainLimitOutputStream(new FileOutputStream(file))){copy(in,out);}success=true;return new Blob(envelope.sha,envelope.encoding,file);}finally{if(!success)file.delete();}
    }
   }catch(IOException error){if(!transientFailure(error)||Thread.currentThread().isInterrupted())throw error;if(attempt==NETWORK_ATTEMPTS-1)throw new IOException("GitHub 연결이 일시적으로 끊겨 3회 확인 후 중단했습니다. 완료한 휴대폰 자료는 유지되며 다시 검색하면 이어집니다.",error);}
   finally{if(connection!=null)connection.disconnect();}
   pause.waitFor(attempt==0?300:900);
  }
  throw new IOException("GitHub 연결 확인 실패 · 완료한 자료는 유지합니다.");
 }
 static String blobSha(File content)throws Exception{java.security.MessageDigest digest=java.security.MessageDigest.getInstance("SHA-1");digest.update(("blob "+content.length()+"\0").getBytes(StandardCharsets.UTF_8));try(InputStream in=new FileInputStream(content)){byte[] buffer=new byte[8192];for(int count;(count=in.read(buffer))!=-1;)digest.update(buffer,0,count);}StringBuilder out=new StringBuilder();for(byte value:digest.digest())out.append(String.format(java.util.Locale.ROOT,"%02x",value&255));return out.toString();}
 private SharedCache.RegionRead region(Blob blob)throws Exception{
  if(blob.file==null||!"base64".equals(blob.encoding)||!blobSha(blob.file).equals(blob.sha))throw new IOException("GitHub 저장 파일 내용 검증 실패 · 기존 달력은 유지합니다.");
  try(Envelope wrapper=envelope(new FileInputStream(blob.file),"data",false)){
   if("gzip-base64".equals(wrapper.encoding)){
    if(!wrapper.value)throw new IOException("공유 저장 자료 내용 누락");
    try(InputStream in=new RemoteCache.Bounded(new java.util.zip.GZIPInputStream(new android.util.Base64InputStream(new FileInputStream(wrapper.literal),Base64.DEFAULT)),MAX_PLAIN_BYTES)){return new SharedCache.RegionRead(blob.sha,RegionDocument.read(in,temporaryDirectory()));}
   }
   try(InputStream in=new RemoteCache.Bounded(new FileInputStream(blob.file),MAX_PLAIN_BYTES)){return new SharedCache.RegionRead(blob.sha,RegionDocument.read(in,temporaryDirectory()));}
  }
 }
 private File temporaryDirectory()throws IOException{File file=temporary(".probe");File directory=file.getParentFile();file.delete();return directory;}
 @Override public SharedCache.RegionRead openRegion()throws Exception{
  try(Blob first=download("/contents/"+path+"?ref="+BRANCH,true)){
   if(first==null)return new SharedCache.RegionRead(null,RegionDocument.memory(null));if(!first.sha.matches("[0-9a-f]{40}"))throw new IOException("GitHub 저장 파일 검증 실패");if("base64".equals(first.encoding))return region(first);
   try(Blob blob=download("/git/blobs/"+first.sha,false)){if(blob==null||!first.sha.equals(blob.sha))throw new IOException("GitHub 저장 파일 검증 실패");return region(blob);}
  }
 }
 private static final class KeepOpen extends FilterOutputStream {KeepOpen(OutputStream out){super(out);}@Override public void write(byte[] bytes,int offset,int length)throws IOException{out.write(bytes,offset,length);}@Override public void close()throws IOException{flush();}}
 private static void base64(File file,OutputStream out)throws IOException{try(InputStream in=new FileInputStream(file);OutputStream encoded=new android.util.Base64OutputStream(new KeepOpen(out),Base64.NO_WRAP)){copy(in,encoded);}}
 private JSONObject requestFile(String endpoint,File payload,String contentSha)throws Exception{
  boolean uncertainWrite=false;
  for(int attempt=0;attempt<NETWORK_ATTEMPTS;attempt++){
   if(Thread.currentThread().isInterrupted())throw new InterruptedException();HttpURLConnection connection=null;
   try{
    connection=connection(endpoint,"PUT");connection.setDoOutput(true);connection.setRequestProperty("Content-Type","application/json; charset=utf-8");connection.setFixedLengthStreamingMode(payload.length());try(InputStream in=new FileInputStream(payload);OutputStream out=connection.getOutputStream()){copy(in,out);}return response(connection);
   }catch(SharedCache.Conflict conflict){if(uncertainWrite&&committed(contentSha))return new JSONObject();throw conflict;}
   catch(IOException error){if(!transientFailure(error)||Thread.currentThread().isInterrupted())throw error;uncertainWrite=true;if(attempt==NETWORK_ATTEMPTS-1){if(committed(contentSha))return new JSONObject();throw new IOException("GitHub 연결이 일시적으로 끊겨 3회 확인 후 중단했습니다. 완료한 휴대폰 자료는 유지되며 다시 검색하면 이어집니다.",error);}}
   finally{if(connection!=null)connection.disconnect();}
   pause.waitFor(attempt==0?300:900);
  }
  throw new IOException("GitHub 연결 확인 실패 · 완료한 자료는 유지합니다.");
 }
 @Override public void writeRegion(RegionDocument body,String expectedSha)throws Exception{
  if(token==null||token.isEmpty())throw new IOException("GitHub 저장 연결이 필요합니다.");File gzip=null,wrapper=null,payload=null;
  try{
   gzip=temporary(".gz");try(Writer writer=new BufferedWriter(new OutputStreamWriter(new PlainLimitOutputStream(new java.util.zip.GZIPOutputStream(new FileOutputStream(gzip))),StandardCharsets.UTF_8))){body.write(writer);}
   wrapper=temporary(".packed");try(OutputStream out=new PlainLimitOutputStream(new FileOutputStream(wrapper))){out.write("{\"encoding\":\"gzip-base64\",\"data\":\"".getBytes(StandardCharsets.UTF_8));base64(gzip,out);out.write("\"}".getBytes(StandardCharsets.UTF_8));}
   String contentSha=blobSha(wrapper);payload=temporary(".put");try(OutputStream out=new PlainLimitOutputStream(new FileOutputStream(payload),MAX_TRANSPORT_BYTES)){out.write(("{\"branch\":"+JSONObject.quote(BRANCH)+",\"message\":\"Update shared mobile award calendar\",\"content\":\"").getBytes(StandardCharsets.UTF_8));base64(wrapper,out);out.write(("\""+(expectedSha==null?"":",\"sha\":"+JSONObject.quote(expectedSha))+"}").getBytes(StandardCharsets.UTF_8));}requestFile("/contents/"+path,payload,contentSha);
  }finally{if(gzip!=null)gzip.delete();if(wrapper!=null)wrapper.delete();if(payload!=null)payload.delete();}
 }

 @Override public SharedCache.Document read()throws Exception{
  if((path.startsWith("data/mobile/v101/")||path.startsWith("data/mobile/v103/")))try(SharedCache.RegionRead region=openRegion()){
   if(region.document().metadata()==null)return new SharedCache.Document(region.sha(),null);
   ByteArrayOutputStream out=new ByteArrayOutputStream();try(Writer writer=new OutputStreamWriter(new PlainLimitOutputStream(out,MAX_COMPAT_BYTES),StandardCharsets.UTF_8)){region.document().write(writer);}return new SharedCache.Document(region.sha(),new JSONObject(out.toString(StandardCharsets.UTF_8)));
  }
  JSONObject file=request("/contents/"+path+"?ref="+BRANCH,"GET",null,true,null);if(file==null)return new SharedCache.Document(null,null);String sha=file.getString("sha"),content=file.optString("content");if(!file.optString("encoding").equals("base64")){JSONObject blob=required("/git/blobs/"+sha);if(!blob.getString("sha").equals(sha)||!blob.getString("encoding").equals("base64"))throw new IOException("GitHub 저장 파일 검증 실패");content=blob.getString("content");}byte[] bytes=Base64.decode(content,Base64.DEFAULT);if(bytes.length>MAX_COMPAT_BYTES)throw new IOException("공유 저장 자료 크기 초과");verifyBlob(bytes,sha);JSONObject body=new JSONObject(new String(bytes,StandardCharsets.UTF_8));if("gzip-base64".equals(body.optString("encoding"))){byte[] compressed=Base64.decode(body.getString("data"),Base64.DEFAULT);try(InputStream in=new RemoteCache.Bounded(new java.util.zip.GZIPInputStream(new ByteArrayInputStream(compressed)),MAX_COMPAT_BYTES)){body=new JSONObject(new String(in.readAllBytes(),StandardCharsets.UTF_8));}}return new SharedCache.Document(sha,body);
 }
 @Override public void write(JSONObject body,String expectedSha)throws Exception{
  if((path.startsWith("data/mobile/v101/")||path.startsWith("data/mobile/v103/"))){try(RegionDocument document=RegionDocument.memory(body)){writeRegion(document,expectedSha);}return;}
  if(token==null||token.isEmpty())throw new IOException("GitHub 저장 연결이 필요합니다.");byte[] bytes=packed(body).toString().getBytes(StandardCharsets.UTF_8);if(bytes.length>MAX_COMPAT_BYTES)throw new IOException("공유 저장 자료 크기 초과");JSONObject payload=new JSONObject().put("branch",BRANCH).put("message","Update shared mobile award calendar").put("content",Base64.encodeToString(bytes,Base64.NO_WRAP));if(expectedSha!=null)payload.put("sha",expectedSha);request("/contents/"+path,"PUT",payload.toString().getBytes(StandardCharsets.UTF_8),false,blobSha(bytes));
 }
 static SharedCache client(Context ctx,String origin,String region,String owner)throws Exception{String id=RegionBatch.catalog(ctx).getJSONObject("ids").getString(region);return new SharedCache(BuildConfig.DEBUG&&hasTestBackend(region)?testBackends.getOrDefault(region,testBackend):new GitHubStore(origin,id,GitHubToken.read(ctx)).usingCache(ctx.getCacheDir()),origin,region,owner);}
}

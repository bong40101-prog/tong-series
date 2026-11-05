package kr.tong.sky;

import org.json.*;
import java.time.*;
import java.util.regex.*;

/** Only collection context is accepted. Browser bodies, headers and credentials are never captured. */
final class DiagnosticEvent {
 static final ZoneId KST=ZoneId.of("Asia/Seoul");
 static String clean(String value){
  String s=value==null?"":value;
  s=s.replaceAll("(?i)https?://[^\\s<>\\\"]+","[주소 생략]");
  s=s.replaceAll("(?i)(?:github_pat_|gh[pousr]_)[A-Za-z0-9_]+","[인증정보 생략]");
  s=s.replaceAll("(?i)Bearer\\s+[^\\s,;]+","Bearer [생략]");
  s=s.replaceAll("(?im)(authorization|cookie|set-cookie|password|token|secret)\\s*[:=]\\s*[^\\r\\n]+","$1=[생략]");
  s=s.replaceAll("[\\p{Cntrl}&&[^\\n\\t]]","");
  return s.length()>4000?s.substring(0,4000)+"…":s;
 }
 static String code(String message){
  String m=message==null?"":message;
  if(m.contains("사용자")&&m.contains("중단"))return "USER_STOP";
  if(m.contains("HTTP 401")||m.contains("HTTP 403")||m.contains("HTTP 429")||m.contains("접근 제한"))return "ACCESS_RESTRICTED";
  if(m.contains("인증서"))return "TLS_ERROR";
  if(m.contains("원본 달력 전체 날짜 선택 불가"))return "CALENDAR_UNAVAILABLE";
  if(m.contains("예약정보 이관 대상"))return "AIRLINE_TRANSFER_UNAVAILABLE";
  if(m.contains("조회 가능 날짜 모두 운항편 없음"))return "CALENDAR_NO_FLIGHTS";
  if(m.contains("조회가 불가"))return "UNSUPPORTED_ITINERARY";
  if(m.contains("공식 도시 목록"))return "AIRPORT_NOT_LISTED";
  if(m.contains("시간 초과")||m.contains("응답을 확인하지"))return "STAGE_TIMEOUT";
  if(m.contains("연결 오류")||m.contains("unexpected end of stream"))return "NETWORK_ERROR";
  if(m.contains("웹 화면이 종료"))return "WEBVIEW_EXIT";
  if(m.contains("안드로이드")&&m.contains("시간")||m.contains("실행 제한"))return "RUNTIME_LIMIT";
  if(m.contains("GitHub")||m.contains("공유 자료")||m.contains("공유 실패"))return "SHARED_STORAGE_ERROR";
  if(m.contains("잠금"))return "LEASE_ERROR";
  if(m.contains("결과")&&(m.contains("확인하지")||m.contains("저장하지")||m.contains("오류")))return "RESULT_ERROR";
  return "COLLECTION_ERROR";
 }
 static String title(String code){return switch(code){
  case "AIRPORT_NOT_LISTED" -> "공항 목록에서 찾지 못함";
  case "AIRPORT_SELECTION_TIMEOUT","STAGE_TIMEOUT" -> "선택·응답 시간 초과";
  case "CALENDAR_UNAVAILABLE" -> "전체 날짜 선택 불가 · 미확인";
  case "AIRLINE_TRANSFER_UNAVAILABLE" -> "예약정보 이관 대상 · 조회 불가";
  case "CALENDAR_NO_FLIGHTS" -> "원본 달력 운항편 없음";
  case "UNSUPPORTED_ITINERARY" -> "항공사 조회 불가 여정";
  case "NETWORK_ERROR" -> "통신 오류";
  case "ACCESS_RESTRICTED","TLS_ERROR" -> "접근·인증서 제한";
  case "SHARED_STORAGE_ERROR","SHARED_CHECKPOINT_FAILED" -> "공유 저장 오류";
  case "PROCESS_INTERRUPTED","PROCESS_DESTROYED","WEBVIEW_EXIT" -> "앱·조회 화면 종료";
  case "USER_STOP" -> "사용자 중단";
  case "RUNTIME_LIMIT" -> "백그라운드 실행 제한";
  case "MONTH_NAVIGATION_RETRY" -> "월 전환 지연 · 한 번 재조회";
  case "RECOVERED" -> "이전 오류 후 조회 성공";
  case "SESSION_STARTED" -> "수집 시작";
  case "SERVICE_START_FAILED" -> "수집 서비스 시작 실패";
  case "JAVA_CRASH" -> "앱 예외 종료";
  case "AF_LOGIN_REQUIRED" -> "에어프랑스 로그인 필요";
  case "AF_NO_RESULTS" -> "에어프랑스 조회 결과 없음";
  case "AF_RESULT_ERROR" -> "에어프랑스 결과 확인 실패";
  case "AF_STORAGE_ERROR" -> "에어프랑스 저장 오류";
  case "AF_WEB_ERROR" -> "에어프랑스 웹 화면 오류";
  case "AF_ACCESS_RESTRICTED" -> "에어프랑스 접근 제한";
  case "AF_NO_MATCHING_QUOTES" -> "에어프랑스 일치 운임 없음 · 조회 관찰";
  case "AF_AUTO_STARTED" -> "에어프랑스 자동검색 시작";
  case "AF_AUTO_QUERY_CHECKED" -> "에어프랑스 자동검색 항목 확인";
  case "AF_AUTO_COMPLETED" -> "에어프랑스 자동검색 완료";
  case "AF_AUTO_STOPPED" -> "에어프랑스 자동검색 중단";
  case "AF_AUTO_DOCUMENT_START_UNAVAILABLE" -> "에어프랑스 초기 응답 수집 미지원";
  case "AF_AUTO_QUERY_TIMEOUT" -> "에어프랑스 자동검색 응답 시간 초과";
  case "AF_AUTO_WEB_ERROR" -> "에어프랑스 자동검색 웹 화면 오류";
  case "AF_AUTO_STATE_ERROR" -> "에어프랑스 자동검색 상태 오류";
  case "AF_AUTO_NAVIGATION_ERROR","AF_AUTO_NAVIGATION_BLOCKED" -> "에어프랑스 자동검색 화면 이동 오류";
  case "AF_AUTO_UNSUPPORTED_RESULT" -> "에어프랑스 자동검색 결과 미지원";
  case "AF_AUTO_UNSUPPORTED_SEARCH_FORM" -> "에어프랑스 자동검색 양식 미지원";
  case "AF_AUTO_DUPLICATE_NAVIGATION_BLOCKED" -> "에어프랑스 중복 검색 차단";
  case "AF_AUTO_INVALID_AUTOMATIC_PACKET","AF_AUTO_RESULT_ERROR" -> "에어프랑스 자동검색 결과 확인 실패";
  case "AF_AUTO_STORAGE_ERROR" -> "에어프랑스 자동검색 저장 오류";
  case "AF_AUTO_STALE_TASK" -> "에어프랑스 이전 작업 응답 제외";
  case "AF_AUTO_ACCESS_RESTRICTED" -> "에어프랑스 자동검색 접근 제한";
  case "AF_AUTO_RESULT_SCROLL_LIMIT" -> "에어프랑스 검색 결과 스크롤 한도";
  case "AF_AUTO_RESULT_BUFFER_LIMIT" -> "에어프랑스 검색 결과 임시 용량 한도";
  case "AF_AUTO_DOM_RESULT_UNSUPPORTED" -> "에어프랑스 화면 표시 운임 확인 실패";
  case "AF_AUTO_WEBVIEW_PROFILE_UNAVAILABLE" -> "에어프랑스 전용 검색 환경 오류";
  case "COMPLETE" -> "수집 처리 완료";
  case "STOPPED" -> "수집 종료";
  case "LEGACY_ERROR" -> "업데이트 전 마지막 오류";
  default -> "조회·저장 오류";
 };}
 static JSONObject query(JSONObject q)throws JSONException{
  JSONObject safe=new JSONObject();if(q==null)return safe;
  for(String field:new String[]{"origin","destination","month"}){
   String v=q.optString(field);if(v.matches(field.equals("month")?"\\d{4}-\\d{2}":"[A-Z]{3}"))safe.put(field,v);
  }
  return safe;
 }
 static JSONObject fromKey(String key)throws JSONException{
  Matcher m=Pattern.compile("([A-Z]{3})-([A-Z]{3})-(\\d{4}-\\d{2})").matcher(key);
  return m.find()?new JSONObject().put("origin",m.group(1)).put("destination",m.group(2)).put("month",m.group(3)):new JSONObject();
 }
 /** Locally generated UUID only. Never retain caller-supplied account or session text. */
 static String localId(String value){return value!=null&&value.matches("[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}")?value:"";}
 static String isoDate(Object value){
  if(!(value instanceof String s)||!s.matches("\\d{4}-\\d{2}-\\d{2}"))return "";
  try{return LocalDate.parse(s).toString();}catch(DateTimeException e){return "";}
 }
 /** Flat, typed search context only; keeps the existing history screen's route labels readable. */
 static JSONObject afTask(JSONObject task)throws JSONException{
  JSONObject safe=new JSONObject();if(task==null)return safe;
  for(String field:new String[]{"origin","destination"}){
   JSONObject station=task.optJSONObject(field);if(station==null)continue;
   Object code=station.opt("code"),type=station.opt("type");
   if(code instanceof String c&&c.matches("[A-Z]{3}")&&type instanceof String t&&java.util.Set.of("CITY","AIRPORT").contains(t)){safe.put(field,c);safe.put(field+"Type",t);}
  }
  String date=isoDate(task.opt("date")),returnDate=isoDate(task.opt("returnDate"));
  if(!date.isEmpty()){safe.put("date",date);safe.put("month",date.substring(0,7));}
  if(!returnDate.isEmpty())safe.put("returnDate",returnDate);
  Object cabin=task.opt("cabin"),trip=task.opt("trip"),adults=task.opt("adults"),id=task.opt("id");
  if(cabin instanceof String c&&java.util.Set.of("ECONOMY","PREMIUM","BUSINESS","FIRST").contains(c))safe.put("cabin",c);
  if(trip instanceof String t&&java.util.Set.of("ONE_WAY","ROUND_TRIP").contains(t)){
   safe.put("trip",t);
   if("ONE_WAY".equals(t))safe.put("scope","ONE_WAY");
   else if(!returnDate.isEmpty())safe.put("scope","ROUND_TRIP");
   else if(task.isNull("returnDate"))safe.put("scope","OUTBOUND_OF_ROUND_TRIP");
  }
  if(adults instanceof Number n&&n.doubleValue()==n.intValue()&&n.intValue()>=1&&n.intValue()<=9)safe.put("adults",n.intValue());
  if(id instanceof String s&&s.matches("afq-(0|[1-9][0-9]{0,5})"))safe.put("taskId",s);
  return safe;
 }
 static JSONObject make(String carrier,String session,String batch,JSONObject q,String stage,String event,String code,String message,String detail,long elapsed,int done,int total,long now)throws JSONException{
  return new JSONObject().put("schema",1).put("occurredAt",now).put("at",Instant.ofEpochMilli(now).atZone(KST).toOffsetDateTime().toString())
   .put("appVersion",BuildConfig.VERSION_NAME).put("carrier","AF".equals(carrier)?"AF":"APP".equals(carrier)?"APP":"OZ".equals(carrier)?"OZ":"KE").put("session",clean(session)).put("batch",clean(batch))
   .put("query",query(q)).put("stage",stage!=null&&stage.matches("[a-z_]{1,40}")?stage:"unknown")
   .put("event",event).put("code",code).put("message",clean(message)).put("detail",clean(detail))
   .put("elapsedMs",Math.max(0,elapsed)).put("done",Math.max(0,done)).put("total",Math.max(0,total));
 }
 static JSONObject interrupted(JSONObject active,long now)throws JSONException{
  return new JSONObject(active.toString()).put("event","failure").put("code","PROCESS_INTERRUPTED")
   .put("detectedAt",Instant.ofEpochMilli(now).atZone(KST).toOffsetDateTime().toString())
   .put("message","이 항목을 조회하던 앱이 정상 종료 기록 없이 끝났습니다. 강제 종료·시스템 종료 등의 원인은 확정할 수 없습니다.");
 }
}

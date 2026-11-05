package kr.tong.sky;

/** Classifies narrowly scoped query-local failures; none are seat observations. */
final class AsianaQueryFailure {
 // Official notice CM202610140002530842. This annotates an actual refusal only;
 // it never suppresses a query, deletes data, or treats an entire route as closed.
 static boolean transferPeriod(String month,java.time.LocalDate today){
  if(month==null)return false;
  return java.util.Set.of("2027-09","2027-10").contains(month)&&!today.isBefore(java.time.LocalDate.of(2026,11,4))
   ||java.util.Set.of("2027-07","2027-08").contains(month)&&!today.isBefore(java.time.LocalDate.of(2026,11,5));
 }

 static final String UNSUPPORTED_ITINERARY="검색하신 여정은 조회가 불가합니다.";
 static boolean skipUnsupportedItinerary(String message,String stage,boolean asiana,boolean activeQuery,boolean batchQuery,boolean allowedPage){
  return asiana&&activeQuery&&batchQuery&&allowedPage&&"result".equals(stage)&&message!=null&&UNSUPPORTED_ITINERARY.equals(message.replaceAll("[\\s\\u00a0]+"," ").trim());
 }
 static boolean skipAirportSelection(String code,String stage,boolean asiana,boolean activeQuery,boolean batchQuery,boolean allowedPage){
  if(!asiana||!activeQuery||!batchQuery||!allowedPage)return false;
  if(!"AIRPORT_NOT_LISTED".equals(code)&&!"AIRPORT_SELECTION_TIMEOUT".equals(code))return false;
  return "origin".equals(stage)||"origin_pick".equals(stage)||"origin_verify".equals(stage)||"destination".equals(stage)||"destination_pick".equals(stage)||"destination_verify".equals(stage);
 }
}

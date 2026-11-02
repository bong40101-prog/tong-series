/* Reads the rendered anonymous Korean Air weekly schedule. No requests or UI actions. */
(function(q){
'use strict';
const norm=s=>(s||'').replace(/\s+/gu,' ').trim(),text=e=>norm(e?.textContent);
const all=(s,r=document)=>Array.from(r.querySelectorAll(s));
const rendered=e=>{if(!e||!e.getClientRects().length)return false;const r=e.getBoundingClientRect();if(r.width<=0||r.height<=0)return false;for(let p=e;p;p=p.parentElement){const s=getComputedStyle(p);if(s.display==='none'||s.visibility==='hidden'||s.visibility==='collapse'||s.opacity==='0')return false;}return true;};
const wait=detail=>({wait:true,detail}),fail=message=>({error:message});
const iso=d=>d.toISOString().slice(0,10),dayMs=86400000;
try{
 const url=new URL(location.href);
 if(url.protocol!=='https:'||!['www.koreanair.com','koreanair.com'].includes(url.hostname)||url.username||url.password||url.port&&url.port!=='443'||!/^\/(?:[a-z]{2}\/[a-z]{2}\/)?flight-status\/?$/.test(url.pathname))return fail('공식 주간 운항 스케줄 화면이 아닙니다.');
 if(!q||!/^\d{4}-\d{2}-\d{2}$/.test(q.date)||!/^[A-Z]{3}$/.test(q.origin||'')||!/^[A-Z]{3}$/.test(q.destination||'')||q.origin===q.destination)return fail('주간 운항 스케줄 조회 조건이 올바르지 않습니다.');
 const requested=new Date(q.date+'T00:00:00Z');if(!Number.isFinite(requested.getTime())||iso(requested)!==q.date)return fail('주간 운항 스케줄 조회 날짜가 올바르지 않습니다.');
 const body=norm(document.body?.innerText||document.body?.textContent);
 if(/access denied|접근이 차단|접속이 차단|비정상적인 접근|보안 문자|자동입력 방지|로봇이 아님|verify you are human|captcha challenge/i.test(body))return fail('보안 확인 또는 접근 제한으로 운항 스케줄을 확인하지 못했습니다.');
 const results=all('#scheduleResultList').filter(rendered);if(!results.length)return wait('주간 운항 스케줄 결과 대기');if(results.length!==1)return fail('주간 운항 스케줄 결과가 중복되어 저장하지 않았습니다.');
 const result=results[0];if(result.getAttribute('aria-busy')==='true')return wait('주간 운항 스케줄 갱신 대기');
 // The completed result heading is intentionally an accessibility-only H2.
 // The URL query is not used as itinerary proof: normal UI searches leave it stale.
 const headings=all('h2',result);if(headings.length!==1)return fail('주간 운항 스케줄 결과 제목을 확인하지 못했습니다.');
 const heading=text(headings[0]),codes=heading.match(/\b[A-Z]{3}\b/g)||[],dates=Array.from(heading.matchAll(/(\d{4})년\s*(\d{1,2})월\s*(\d{1,2})일/g));
 if(codes.length!==2||codes[0]!==q.origin||codes[1]!==q.destination)return fail('주간 운항 스케줄 결과 노선이 요청과 다릅니다.');
 if(dates.length!==1||`${dates[0][1]}-${dates[0][2].padStart(2,'0')}-${dates[0][3].padStart(2,'0')}`!==q.date)return fail('주간 운항 스케줄 결과 날짜가 요청과 다릅니다.');
 const tables=all('.flight-schedule__table',result).filter(rendered);if(!tables.length)return wait('주간 운항 스케줄 표 대기');if(tables.length!==1)return fail('주간 운항 스케줄 표가 중복되어 저장하지 않았습니다.');
 const table=tables[0],headers=all('thead th',table);if(headers.length!==8)return fail('주간 운항 스케줄 날짜 열 구조가 다릅니다.');
 const monday=new Date(requested.getTime()-((requested.getUTCDay()+6)%7)*dayMs),week=Array.from({length:7},(_,i)=>new Date(monday.getTime()+i*dayMs));
 const expectedMonths=[...new Set([iso(week[0]).slice(0,7),iso(week[6]).slice(0,7)])],headerMonths=Array.from(text(headers[0]).matchAll(/(\d{4})년\s*(\d{1,2})월/g),m=>m[1]+'-'+m[2].padStart(2,'0'));
 if(headerMonths.length!==expectedMonths.length||headerMonths.some((m,i)=>m!==expectedMonths[i]))return fail('주간 운항 스케줄 표의 연도·월이 다릅니다.');
 for(let i=0;i<7;i++){const nums=all('.flight-schedule__num',headers[i+1]);if(nums.length!==1||!/^\d{1,2}$/.test(text(nums[0]))||Number(text(nums[0]))!==week[i].getUTCDate())return fail('주간 운항 스케줄 날짜 열이 요청 주간과 다릅니다.');}
 const rows=all('tbody tr',table).filter(rendered);if(!rows.length)return wait('확인된 운항 스케줄 항공편이 없습니다.');
 const flights=[],knownNames={KE:'대한항공',OZ:'아시아나항공',AF:'에어프랑스'};
 for(const row of rows){
  const th=Array.from(row.children).filter(e=>e.tagName==='TH');if(th.length!==1)return fail('주간 운항 스케줄 항공편 행 구조가 다릅니다.');
  const numbers=all('ke-flight-numbers',th[0]);if(numbers.length!==1||!rendered(numbers[0]))return fail('주간 운항 스케줄 운항 항공사를 확인하지 못했습니다.');
  const component=numbers[0],icons=all('.code-share',component);if(icons.length!==1)return fail('주간 운항 스케줄 운항 항공사 표식이 불명확합니다.');
  const operatorClasses=Array.from(icons[0].classList).filter(c=>/^-[a-z0-9]{2,3}$/.test(c));
  if(operatorClasses.length!==1)return fail('주간 운항 스케줄 운항 항공사 코드를 확인하지 못했습니다.');
  const operatorCode=operatorClasses[0].slice(1).toUpperCase(),labels=all('._hidden',component).map(text).filter(Boolean);
  if(labels.length!==1||!/^.{2,80} 운항$/u.test(labels[0]))return fail('주간 운항 스케줄 실제 운항 항공사 이름을 확인하지 못했습니다.');
  const operatorName=labels[0].slice(0,-3).trim();
  if(!/^[\p{L}\p{N}][\p{L}\p{N} .&'’()·-]{1,79}$/u.test(operatorName)||/미확인|알 수 없|확인 중|unknown|undefined|null|조회|공동운항/i.test(operatorName))return fail('주간 운항 스케줄 실제 운항 항공사 이름이 불명확합니다.');
  if(knownNames[operatorCode]&&knownNames[operatorCode]!==operatorName||Object.entries(knownNames).some(([code,name])=>name===operatorName&&code!==operatorCode))return fail('주간 운항 스케줄 운항 항공사 코드와 이름이 다릅니다.');
  // The icon identifies the actual operator; the text sibling identifies the marketing flight.
  // A KE marketing prefix never establishes who operates that flight.
  const numberCopy=component.cloneNode(true);for(const e of all('.code-share,._hidden',numberCopy))e.remove();
  const numberText=text(numberCopy),numberMatch=numberText.match(/^([A-Z0-9]{2})\s*(\d{1,4})$/);
  if(!numberMatch)return fail('주간 운항 스케줄 항공편 번호를 확인하지 못했습니다.');
  const number=numberMatch[1]+numberMatch[2],timeNodes=all('.flight-schedule__tb-time',th[0]);
  if(timeNodes.length!==1||!rendered(timeNodes[0]))return fail('주간 운항 스케줄 출도착 시간을 확인하지 못했습니다.');
  const timeText=text(timeNodes[0]),timeMatch=timeText.match(/^출발\s*시간\s*((?:[01]?\d|2[0-3]):[0-5]\d)\s*~\s*도착\s*시간\s*((?:[01]?\d|2[0-3]):[0-5]\d)(?:\s|$)/);
  if(!timeMatch||(timeText.match(/\d{1,2}:\d{2}/g)||[]).length!==2)return fail('주간 운항 스케줄 출도착 시간이 불명확합니다.');
  const clock=t=>t.padStart(5,'0'),departureTime=clock(timeMatch[1]),arrivalTime=clock(timeMatch[2]);
  const cells=Array.from(row.children).filter(e=>e.tagName==='TD');if(cells.length!==7)return fail('항공편의 주간 날짜 열이 완전하지 않습니다.');
  const states=[];
  for(let i=0;i<7;i++){
   const cell=cells[i];if(!rendered(cell)||cell.getAttribute('data-index')!==String(i+1))return fail('항공편 날짜 열 순서가 다릅니다.');
   const on=all('[accessibility-text="운항 함"]',cell).filter(rendered).length,cellText=text(cell),off=cellText==='운항 안함';
   if(on>1||Boolean(on)===off||on&&/운항\s*안함/u.test(cellText))return fail('항공편 운항 여부를 명확히 확인하지 못했습니다.');
   states.push(Boolean(on));
  }
  flights.push({number,operatorCode,operatorName,serviceKind:operatorCode==='KE'?'DIRECT':'CODESHARE',departureTime,arrivalTime,states});
 }
 const identity=f=>[f.number,f.operatorCode,f.departureTime,f.arrivalTime].join('|');
 if(new Set(flights.map(identity)).size!==flights.length)return fail('주간 운항 스케줄 항공편 행이 중복되어 저장하지 않았습니다.');
 const ownFlightRows=flights.filter(f=>f.serviceKind==='DIRECT').length,partnerFlightRows=flights.length-ownFlightRows;
 return {data:{schema:2,completeOperators:true,carrier:'KE',origin:q.origin,destination:q.destination,queriedDate:q.date,weekStart:iso(monday),observedAt:new Date().toISOString(),sourceUrl:url.href,ownFlightRows,partnerFlightRows,dates:week.map((date,i)=>({date:iso(date),status:flights.some(f=>f.states[i])?'OPERATING':'NO_FLIGHT',flights:flights.map(({states,...flight})=>({...flight,status:states[i]?'OPERATING':'NO_FLIGHT'}))}))}};
}catch(e){return fail('공식 주간 운항 스케줄을 검증하지 못했습니다. 기존 자료를 유지합니다.');}
})

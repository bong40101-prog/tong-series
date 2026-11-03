/* Calendar interpretation adapted from we-insub/Korean_air, MIT; see THIRD_PARTY_LICENSE.txt. */
(function(stage,q){
'use strict';
const norm=s=>(s||'').replace(/\s+/gu,' ').trim();
const text=e=>norm(e && e.textContent);
const visible=e=>{if(!e||!e.getClientRects().length)return false;const r=e.getBoundingClientRect();if(r.width<=0||r.height<=0)return false;for(let p=e;p;p=p.parentElement||p.getRootNode?.().host){const style=getComputedStyle(p);if(style.display==='none'||style.visibility==='hidden'||style.visibility==='collapse'||style.opacity==='0')return false;}return true;};
const all=(s,root=document)=>Array.from(root.querySelectorAll(s));
const one=s=>document.querySelector(s);
const labelled=(e,name)=>(e.getAttribute('aria-labelledby')||'').trim().split(/\s+/u).filter(Boolean).some(id=>text(document.getElementById(id))===name);
const wait=()=>({wait:true});
const next=(n)=>({next:n});
const fail=message=>({error:message});
const seatMap={'일반석 보너스':'economyAward','프리미엄석 보너스':'premiumAward','프레스티지석 보너스':'prestigeAward','프리미엄석 좌석승급':'premiumUpgrade','프레스티지석 좌석승급':'prestigeUpgrade','일등석 보너스/좌석승급':'firstCombined'};
const position=e=>{if(!e||!visible(e)||e.disabled||e.getAttribute('aria-disabled')==='true')return null;e.scrollIntoView({block:'center',inline:'nearest',behavior:'instant'});const r=e.getBoundingClientRect();const x=r.left+r.width/2,y=r.top+r.height/2;const hit=document.elementFromPoint(x,y);if(x<0||y<0||x>=innerWidth||y>=innerHeight||!hit||!(hit===e||e.contains(hit)))return null;return {x,y,width:innerWidth};};
const click=(e,n)=>{if(!e||!visible(e)||e.disabled||e.getAttribute('aria-disabled')==='true')return wait();e.scrollIntoView({block:'center',inline:'nearest',behavior:'instant'});e.click();return next(n);};
const tap=(e,n)=>{const point=position(e);return point?{next:n,tap:point}:wait();};

function roots(){const r=[document];for(let i=0;i<r.length;i++)for(const e of r[i].querySelectorAll('*'))if(e.shadowRoot)r.push(e.shadowRoot);return r;}
const byText=(s,t,root=document)=>all(s,root).find(e=>visible(e)&&text(e)===t);
const body=norm(document.body?.innerText);
if(/access denied|접근이 차단|접속이 차단|비정상적인 접근|보안 문자|자동입력 방지|로봇이 아님|verify you are human|captcha challenge/i.test(body))return fail('보안 확인 또는 접근 제한으로 조회를 중단했습니다.');
const reuseKey=[q.origin,q.destination,q.month].join('|');
const currentReuse=()=>window.__tongKeReuse?.key===reuseKey?window.__tongKeReuse:null;
const resultDialog=()=>one('[role="dialog"][aria-labelledby="modals-travelCalendar-title"]');
const officialPage=()=>{try{return location.protocol==='https:'&&['koreanair.com','www.koreanair.com'].includes(location.hostname)&&!location.username&&!location.password&&(!location.port||location.port==='443')&&location.pathname==='/booking/book-and-manage/award-seat-availability';}catch(_){return false;}};
const sourceClock=()=>{const m=body.match(/좌석 상황은 대한민국 시간\((\d{4})년\s*(\d{1,2})월\s*(\d{1,2})일\s*(\d{2}):(\d{2})\) 기준/);return m?m[1]+'-'+m[2].padStart(2,'0')+'-'+m[3].padStart(2,'0')+'T'+m[4]+':'+m[5]+':00+09:00':null;};
const canUse=e=>visible(e)&&!e.disabled&&e.getAttribute('aria-disabled')!=='true';
const monthText=month=>month.slice(0,4)+'년 '+Number(month.slice(5))+'월';
const rightRoute=d=>{const codes=text(d?.querySelector('#modals-travelCalendar-title')).match(/\b[A-Z]{3}\b/g)||[];return codes.length===2&&codes[0]===q.origin&&codes[1]===q.destination;};
const coldReuse=detail=>{if(currentReuse()?.submitted)return fail('이미 요청한 월은 다시 조회하지 않습니다.');if(currentReuse()?.cold)return wait();window.__tongKeSuccess=null;window.__tongKeReuse={key:reuseKey,cold:true};return {cold:true,detail};};
// This is the visible airline response, not an inferred no-flight/no-seat result.
if(['result','filter','parse','reuse'].includes(stage)){
 const alert=one('#keAlertModalTitleless');
 if(visible(alert)&&text(alert.querySelector('p'))==='검색하신 여정은 조회가 불가합니다.')return {error:'항공사 조회 불가 여정 · 미확인: 검색하신 여정은 조회가 불가합니다.',code:'UNSUPPORTED_ITINERARY'};
}
if(stage==='reuse'){
 const state=currentReuse();if(state?.submitted)return {next:'result',submitted:true};if(state?.cold)return wait();
 const prior=window.__tongKeSuccess,d=resultDialog(),wrap=d?.querySelector('#bonusCalendarTableWrapEl'),button=d?.querySelector('#travelCalendarNextBtn');
 const consecutive=prior&&new Date(prior.month+'-01T00:00:00Z');if(consecutive)consecutive.setUTCMonth(consecutive.getUTCMonth()+1);
 if(!officialPage()||!prior||!consecutive||consecutive.toISOString().slice(0,7)!==q.month||prior.origin!==q.origin||prior.destination!==q.destination||d!==prior.dialog||wrap!==prior.wrap||!visible(d)||!rightRoute(d)||text(d.querySelector('#travelCalendarListBtn'))!==monthText(prior.month)||sourceClock()!==prior.source||!canUse(button)||wrap.innerHTML!==prior.calendar)return coldReuse('기존 달력의 노선·월·표식 또는 다음 달 버튼을 확인하지 못해 새 화면을 엽니다.');
 const cells=all('tbody td[id^="day_"]',wrap);if(!cells.length)return coldReuse('이전 달력의 날짜를 확인하지 못해 새 화면을 엽니다.');
 // Submit once. Do not accept a changed heading until every old date cell is replaced.
 window.__tongKeReuse={key:reuseKey,submitted:true,dialog:d,wrap,cells,source:prior.source};button.click();return {next:'result',submitted:true};
}
const reuse=currentReuse();
if(reuse?.submitted&&['result','filter','parse'].includes(stage)){
 const d=resultDialog(),wrap=d?.querySelector('#bonusCalendarTableWrapEl');
 if(!visible(d)||!wrap)return {wait:true,detail:'다음 달 결과 대기 · 이전 달력은 저장하지 않습니다.'};
 if(!rightRoute(d))return fail('월 전환 결과의 노선이 요청한 노선과 다릅니다.');
 if(sourceClock()!==reuse.source)return fail('월 전환 중 원본 기준 시각이 변경되어 저장하지 않았습니다.');
 if(text(d.querySelector('#travelCalendarListBtn'))!==monthText(q.month)||reuse.cells.some(cell=>cell.isConnected&&wrap.contains(cell)))return {wait:true,detail:'다음 달 날짜 교체 대기 · 이전 달력은 저장하지 않습니다.'};
}
if(stage==='consent'){
 for(const r of roots()){const b=byText('button','필수 쿠키만 허용',r);if(b){b.click();return next('oneway');}}
 if(visible(one('#bonusTripTypeLabel_OW')))return next('oneway');return wait();
}
if(stage==='oneway'){
 const m=body.match(/좌석 상황은 대한민국 시간\((\d{4})년\s*(\d{1,2})월\s*(\d{1,2})일\s*(\d{2}):(\d{2})\) 기준/);
 if(m)window.__tongSource=m[1]+'-'+m[2].padStart(2,'0')+'-'+m[3].padStart(2,'0')+'T'+m[4]+':'+m[5]+':00+09:00';
 if(one('#bonusTripType_OW')?.checked)return next('origin_open');const label=one('#bonusTripTypeLabel_OW');if(visible(label))label.click();return wait();
}
function airportButton(origin){const prefix=origin?'departureBtn':'destinationBtn';const name=origin?/^(?:From )?출발지(?: |$)/u:/^(?:To )?도착지(?: |$)/u;return all('button').find(e=>visible(e)&&(e.id.startsWith(prefix)||name.test(norm(e.getAttribute('aria-label')||e.innerText))));}
function airportInput(origin){const name=origin?'출발지 검색':'도착지 검색';return all('[role="combobox"],input').find(e=>visible(e)&&(e.getAttribute('aria-label')===name||Array.from(e.labels||[]).some(l=>text(l)===name)||labelled(e,name)));}
function airportDialogOpen(name){return all('[role="dialog"]').some(e=>{const r=e.getBoundingClientRect();return visible(e)&&r.top<innerHeight&&r.bottom>0&&(labelled(e,name)||e.getAttribute('aria-label')===name);});}
if(stage==='origin_open'||stage==='dest_open'){
 if(!one('#bonusTripType_OW')?.checked)return fail('공식 화면의 편도 선택을 확인하지 못했습니다.');
 const origin=stage==='origin_open';if(!origin&&airportDialogOpen('출발지 검색'))return wait();
 return click(airportButton(origin),origin?'origin_focus':'dest_focus');
}
if(stage==='origin_selected'||stage==='dest_selected'){
 const origin=stage==='origin_selected',code=origin?q.origin:q.destination;
 if(airportDialogOpen(origin?'출발지 검색':'도착지 검색')){
  const input=airportInput(origin),key=origin?'origin':'dest',now=Date.now();
  const attempts=window.__tongAirportAttempts||(window.__tongAirportAttempts={});
  const a=attempts[key]||(attempts[key]={t:now,retries:0});
  if(now-a.t<1400)return wait();
  if(a.retries>=2)return fail('공항 후보 선택이 완료되지 않았습니다. 기존 결과는 유지합니다.');
  a.retries++;a.t=now;
  return next(input?.value?.toUpperCase()===code?(origin?'origin_pick':'dest_pick'):(origin?'origin_focus':'dest_focus'));
 }
 const button=airportButton(origin);if(!button||!new RegExp('\\b'+code+'\\b').test(text(button)))return wait();
 return next(origin?'dest_open':'month_open');
}
if(stage==='origin_focus'||stage==='dest_focus'){
 const origin=stage==='origin_focus';const input=airportInput(origin);
 if(!input)return wait();input.scrollIntoView({block:'center',behavior:'instant'});input.setAttribute('inputmode','none');input.focus();input.select();return {next:origin?'origin_pick':'dest_pick',type:origin?q.origin:q.destination};
}
if(stage==='origin_pick'||stage==='dest_pick'){
 const origin=stage==='origin_pick',code=origin?q.origin:q.destination,input=airportInput(origin);
 // Do not tap stale suggestions while physical key events are still arriving.
 if(!input||input.value.toUpperCase()!==code)return wait();
 const option=all('[role="option"]').find(e=>visible(e)&&new RegExp('^'+code+'\\b').test(text(e)));
 return tap(option,origin?'origin_selected':'dest_selected');
}

if(stage==='month_open')return click(one('#seatCalendarBtn'),'month_pick');
if(stage==='month_pick'){
 const [year,month]=q.month.split('-');
 const group=all('[role="group"]').find(e=>visible(e)&&(e.getAttribute('aria-label')===year||labelled(e,year)));
 if(!group)return wait();return click(byText('button',Number(month)+'월',group),'month_confirm');
}
if(stage==='month_confirm')return click(byText('button','선택'),'search');
// Recheck canonical one-way and visible airports immediately before submission;
// earlier selection can be reset by a late airline UI handler.
if(stage==='search'){
 if(!one('#bonusTripType_OW')?.checked)return fail('조회 직전 편도 선택이 변경되어 조회하지 않았습니다.');
 for(const origin of [true,false]){const code=origin?q.origin:q.destination,button=airportButton(origin);if(!button||!new RegExp('\\b'+code+'\\b').test(text(button)))return fail('조회 직전 선택 공항이 변경되어 조회하지 않았습니다.');}
 const submit=byText('button','조회');if(!submit||submit.disabled||submit.getAttribute('aria-disabled')==='true')return wait();
 return click(submit,'result');
}
if(stage==='result'){
 const d=one('[role="dialog"][aria-labelledby="modals-travelCalendar-title"]');
 if(!visible(d)||!d.querySelector('tbody td[id^="day_"]'))return wait();
 const codes=text(d.querySelector('#modals-travelCalendar-title')).match(/\b[A-Z]{3}\b/g)||[];
 if(codes.length!==2||codes[0]!==q.origin||codes[1]!==q.destination)return fail('공식 결과의 노선이 요청한 노선과 다릅니다.');
 const [year,month]=q.month.split('-');if(text(d.querySelector('#travelCalendarListBtn'))!==year+'년 '+Number(month)+'월')return fail('공식 결과의 연도·월이 다릅니다.');
 return click(d.querySelector('#bonusFilterBtn'),'filter');
}
if(stage==='filter'){
 const d=all('[role="dialog"]').find(e=>visible(e)&&(e.getAttribute('aria-label')==='보너스 좌석 필터'||labelled(e,'보너스 좌석 필터')));
 if(!d)return wait();
 const inputs=all('input[type="checkbox"]',d);
 const expected=['일반석 보너스','프리미엄석 보너스','프리미엄석 좌석승급','프레스티지석 보너스','프레스티지석 좌석승급','일등석 보너스/좌석승급'];
 if(inputs.length!==6)return fail('좌석 필터 구조가 달라 수집을 중단했습니다.');
 const labels=inputs.map(e=>norm((e.labels&&Array.from(e.labels).map(text).join(' '))||e.getAttribute('aria-label')));
 if(expected.some(e=>!labels.includes(e))||new Set(labels).size!==6)return fail('좌석 필터 이름을 확인할 수 없어 저장하지 않았습니다.');
 // Restore the visible filter through its own labels, then verify on another tick.
 // Never set checked programmatically or save a partially filtered calendar.
 let state=window.__tongKeFilter;
 if(!state||state.key!==reuseKey||state.dialog!==d)state=window.__tongKeFilter={key:reuseKey,dialog:d,clicked:[],pending:null};
 if(state.pending){const index=labels.indexOf(state.pending);if(index<0)return fail('좌석 필터 이름이 변경되어 저장하지 않았습니다.');if(!inputs[index].checked)return {wait:true,detail:'좌석 필터 선택 반영 대기 · '+state.pending};state.pending=null;}
 const index=inputs.findIndex(e=>!e.checked);
 if(index>=0){const input=inputs[index],name=labels[index];if(state.clicked.includes(name))return fail('좌석 필터 선택이 유지되지 않아 저장하지 않았습니다: '+name);if(input.disabled||input.getAttribute('aria-disabled')==='true')return fail('좌석 필터를 선택할 수 없어 저장하지 않았습니다: '+name);const label=Array.from(input.labels||[]).find(e=>d.contains(e)&&visible(e));if(!label)return {wait:true,detail:'좌석 필터 표시 대기 · '+name};state.clicked.push(name);state.pending=name;label.scrollIntoView({block:'center',inline:'nearest',behavior:'instant'});label.click();return {wait:true,detail:'좌석 필터 전체 표시 복구 · '+name};}

 const close=byText('button','선택 완료',d)||byText('button','닫기',d);return click(close,'parse');
}
if(stage==='parse'||stage==='parse_test'){
 const d=one('[role="dialog"][aria-labelledby="modals-travelCalendar-title"]');if(!d)return fail('결과 달력이 없습니다.');
 // A hidden prior dialog must not acquire a new collectedAt/success timestamp.
 if(!visible(d))return wait();
 const codes=text(d.querySelector('#modals-travelCalendar-title')).match(/\b[A-Z]{3}\b/g)||[];
 if(codes.length!==2||codes[0]!==q.origin||codes[1]!==q.destination)return fail('결과 노선 불일치');
 const [year,month]=q.month.split('-').map(Number);
 if(text(d.querySelector('#travelCalendarListBtn'))!==year+'년 '+month+'월')return fail('결과 월 불일치');
 const tables=all('#bonusCalendarTableWrapEl table.bonus-calendar__table',d);if(tables.length!==1)return fail('달력 구조 변경');
 const days=new Date(Date.UTC(year,month,0)).getUTCDate();const seen=new Set();const dates=[];let noFlightCount=0,disabledCount=0;
 for(const cell of all('tbody td',tables[0])){
  if(!cell.id){if(cell.getAttribute('aria-hidden')!=='true'||text(cell)!==''||cell.children.length)return fail('확인되지 않은 날짜 셀');continue;}
  const m=cell.id.match(/^day_([1-9]\d?)$/);const day=Number(m?.[1]);if(!m||day<1||day>days||seen.has(day)||cell.getAttribute('aria-hidden')==='true')return fail('날짜 누락·중복 또는 잘못된 날짜');seen.add(day);
  const label=all('.bonus-calendar__day > span[aria-hidden="true"]',cell);if(label.length!==1||text(label[0])!==String(day))return fail('날짜 표시 불일치');
  if(all('.bonus-calendar__icons',cell).length>1)return fail('중복된 좌석 표식 영역');
  // Hidden transition/template icons are not public seat observations.
  const markers=all('.bonus-calendar__icons li',cell).filter(visible).map(text);
  const noSeat=all('.bonus-calendar__day > span.ux-class-icon.-no-seat.-gray',cell).filter(visible);
  if(noSeat.length&&(noSeat.length!==1||text(noSeat[0])!=='좌석 없음'||markers.length))return fail('좌석 없음 표식 불일치');
  const item={date:q.month+'-'+String(day).padStart(2,'0'),status:markers.length||noSeat.length?'OBSERVED':'UNKNOWN',economyAward:false,premiumAward:false,prestigeAward:false,premiumUpgrade:false,prestigeUpgrade:false,firstCombined:false};
  const unique=new Set();for(const marker of markers){if(!seatMap[marker]||unique.has(marker))return fail('알 수 없거나 중복된 좌석 표식');unique.add(marker);item[seatMap[marker]]=true;}
  // Verified on ICN-CNX October 2027 (2026-11-03): a no-flight icon is
  // distinct from no-seat. Keep prior saved data and classify a fully empty
  // operating calendar instead of waiting until the parser times out.
  const noFlight=all('.bonus-calendar__day > span.ux-class-icon.-no-flight.-gray',cell).filter(visible);
  if(noFlight.length&&(noFlight.length!==1||text(noFlight[0])!=='운항편 없음'||!cell.classList.contains('-no-flight')||cell.getAttribute('aria-disabled')!=='true'||markers.length||noSeat.length))return fail('운항편 없음 표식 불일치');
  if(noFlight.length)noFlightCount++;
  else if(cell.classList.contains('-disabled')&&cell.getAttribute('aria-disabled')==='true'&&!markers.length&&!noSeat.length)disabledCount++;
  dates.push(item);
 }
 if(seen.size!==days)return fail('한 달 날짜가 완전하지 않아 저장하지 않았습니다.');
 if(dates.every(d=>d.status==='UNKNOWN')){
  if(noFlightCount>0&&noFlightCount+disabledCount===days)return {error:'원본 달력의 조회 가능 날짜 모두 운항편 없음 · 기존 저장 자료를 유지합니다.',code:'CALENDAR_NO_FLIGHTS'};
  return {wait:true,detail:'달력 좌석 표식 확인 대기 · 운항편 없음 '+noFlightCount+'일 · 선택 불가 '+disabledCount+'일'};
 }
 dates.sort((a,b)=>a.date.localeCompare(b.date));
 const source=window.__tongSource||q.testSource;
 if(!source||!Number.isFinite(Date.parse(source)))return fail('대한항공 정보 기준 시각을 확인하지 못했습니다.');
 const data={collectionMethod:"MONTHLY_CALENDAR",origin:q.origin,destination:q.destination,month:q.month,sourceUpdatedAt:source,collectedAt:new Date().toISOString(),dates};
 // The monthly calendar is the source. No per-flight or timetable capture is required.
 const wrap=d.querySelector('#bonusCalendarTableWrapEl'),reuseReady=officialPage()&&sourceClock()===source&&canUse(d.querySelector('#travelCalendarNextBtn'));
 window.__tongKeReuse=null;window.__tongKeSuccess=reuseReady?{origin:q.origin,destination:q.destination,month:q.month,source,dialog:d,wrap,calendar:wrap.innerHTML}:null;
 return {data,reuseReady};
}
return fail('지원하지 않는 조회 단계');
})


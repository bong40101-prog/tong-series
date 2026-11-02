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
const seatFields=Object.values(seatMap);
const position=e=>{if(!e||!visible(e)||e.disabled||e.getAttribute('aria-disabled')==='true')return null;e.scrollIntoView({block:'center',inline:'nearest',behavior:'instant'});const r=e.getBoundingClientRect();const x=r.left+r.width/2,y=r.top+r.height/2;const hit=document.elementFromPoint(x,y);if(x<0||y<0||x>=innerWidth||y>=innerHeight||!hit||!(hit===e||e.contains(hit)))return null;return {x,y,width:innerWidth};};
const click=(e,n)=>{if(!e||!visible(e)||e.disabled||e.getAttribute('aria-disabled')==='true')return wait();e.scrollIntoView({block:'center',inline:'nearest',behavior:'instant'});e.click();return next(n);};
const tap=(e,n)=>{const point=position(e);return point?{next:n,tap:point}:wait();};

function roots(){const r=[document];for(let i=0;i<r.length;i++)for(const e of r[i].querySelectorAll('*'))if(e.shadowRoot)r.push(e.shadowRoot);return r;}
const byText=(s,t,root=document)=>all(s,root).find(e=>visible(e)&&text(e)===t);
const body=norm(document.body?.innerText);
if(/access denied|접근이 차단|접속이 차단|비정상적인 접근|보안 문자|자동입력 방지|로봇이 아님|verify you are human|captcha challenge/i.test(body))return fail('보안 확인 또는 접근 제한으로 조회를 중단했습니다.');
if(stage==='reuse'){
 const key=[q.origin,q.destination,q.month].join('|');
 if(window.__tongKeColdReuse===key)return wait();window.__tongKeColdReuse=key;
 return {cold:true,detail:'대한항공 월 전환 화면 재사용은 검증되지 않아 새 공식 화면을 엽니다.'};
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
 // A matching initial detail list must belong to this actual cold search, not
 // merely to an already-selected date in a previous result.
 const prior=one('[role="dialog"][aria-labelledby="modals-travelCalendar-title"]');
 window.__tongKeFlightSearch={key:captureKey(),document,beforeDialog:prior,beforeTable:prior?.querySelector('#bonusCalendarTableWrapEl table.bonus-calendar__table'),beforeLists:all('#bonusSeatSideScrollEl > ul.bonus-calendar__flight-list'),used:false};
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
 if(expected.some(e=>!labels.includes(e))||inputs.some(e=>!e.checked))return fail('좌석 필터가 일부 숨겨져 있어 저장하지 않았습니다.');
 const close=byText('button','선택 완료',d)||byText('button','닫기',d);return click(close,'parse');
}
function flightContext(){
 const d=one('[role="dialog"][aria-labelledby="modals-travelCalendar-title"]');if(!d||!visible(d))return null;
 const codes=text(d.querySelector('#modals-travelCalendar-title')).match(/\b[A-Z]{3}\b/g)||[],[year,month]=q.month.split('-').map(Number);
 if(codes.length!==2||codes[0]!==q.origin||codes[1]!==q.destination||text(d.querySelector('#travelCalendarListBtn'))!==year+'년 '+month+'월')return null;
 const tables=all('#bonusCalendarTableWrapEl table.bonus-calendar__table',d);return tables.length===1?{d,table:tables[0]}:null;
}
function detailDate(d){const heads=all('#seatScroll',d);if(heads.length!==1||!visible(heads[0]))return null;const value=text(heads[0]),m=value.match(/^항공편 별 좌석 상황\s*(\d{4})년\s*(\d{1,2})월\s*(\d{1,2})일$/u);return m?`${m[1]}-${m[2].padStart(2,'0')}-${m[3].padStart(2,'0')}`:null;}
function detailLists(d){return all('#bonusSeatSideScrollEl > ul.bonus-calendar__flight-list',d).filter(visible);}
// On the official mobile layout, flight details are a separate modal outside
// the calendar dialog. The calendar selection changes only after confirmation.
function mobileFlightPanels(){return all('ke-bonus-seat-side-scroll > #caleandarSideEl[role="dialog"]').filter(visible);}
function flightPanel(d){
 const mobile=mobileFlightPanels();
 if(mobile.length){if(mobile.length!==1||mobile[0].getAttribute('aria-labelledby')!=='seatScroll'||!mobile[0].classList.contains('-active')||all('#seatScroll',d).some(visible))return null;return {root:mobile[0],mobile:true};}
 return {root:d,mobile:false};
}
function stopFlightWatch(active){active?.observer?.disconnect();}
function flightWait(detail){return {wait:true,detail:'항공편별 좌석 정보 대기 · '+detail+' · 미확인은 좌석 없음으로 저장하지 않습니다.'};}
function captureKey(){return [q.origin,q.destination,q.month].join('|');}
function recordFlightRows(state,date,rows,observedAt){
 const observed=state.snapshot.dates.find(d=>d.date===date);if(!observed)return fail('항공편별 좌석 저장 날짜가 다릅니다.');
 observed.flights=rows;observed.flightDetailStatus='CAPTURED';observed.flightDetailDate=date;observed.flightDetailObservedAt=observedAt;stopFlightWatch(state.active);state.active=null;state.index++;return next('parse_flights_open');
}
function flightRows(list){
 const rows=Array.from(list.children).filter(e=>e.matches('li.bonus-calendar__flight-item')&&visible(e));
 if(!rows.length||rows.length>128||Array.from(list.children).some(e=>visible(e)&&!e.matches('li.bonus-calendar__flight-item')))return null;
 const result=[],seen=new Set();
 const iconClass={economyAward:'-class-e',premiumAward:'-class-r',premiumUpgrade:'-class-g',prestigeAward:'-class-p',prestigeUpgrade:'-class-u',firstCombined:'-class-f'};
 for(const row of rows){
  const numbers=all('.bonus-calendar__flight-num',row).filter(visible),times=all('.bonus-calendar__flight-time',row).filter(visible),classes=all('.bonus-calendar__flight-class > ul.bonus-calendar__class',row).filter(e=>visible(e)&&!e.classList.contains('-expander-case'));
  if(numbers.length!==1||times.length!==1||classes.length!==1)return null;
  const number=text(numbers[0]).replace(/\s+/g,''),departureTime=text(times[0]);
  if(!/^[A-Z0-9]{2}\d{1,4}$/.test(number)||!/^([01]\d|2[0-3]):[0-5]\d$/.test(departureTime)||seen.has(number+'|'+departureTime))return null;seen.add(number+'|'+departureTime);
  const item={number,departureTime};for(const field of seatFields)item[field]=false;
  const markers=Array.from(classes[0].children).filter(visible);if(!markers.length)return null;
  for(const marker of markers){
   if(!marker.matches('li.bonus-calendar__class-item'))return null;
   const label=text(marker),icons=all('.icon',marker).filter(visible);
   if(label==='좌석 없음'){if(markers.length!==1||icons.length!==1||!icons[0].classList.contains('-no-seat'))return null;item.noSeat=true;continue;}
   const field=seatMap[label];
   if(!field||item[field]||icons.length!==1||!icons[0].classList.contains(iconClass[field]))return null;item[field]=true;
  }
  result.push(item);
 }
 return result;
}
if(stage==='parse_flights_open'||stage==='parse_flights'||stage==='parse_flights_confirm'){
 const state=window.__tongKeFlightCapture,context=flightContext();
 if(!state||state.key!==captureKey()||!context||context.d!==state.dialog||context.table!==state.table)return fail('항공편별 좌석 조회 중 결과 노선·월 또는 화면이 변경되어 저장하지 않았습니다.');
 if(state.index>=state.days.length){stopFlightWatch(state.active);const data=state.snapshot;data.collectedAt=new Date().toISOString();window.__tongKeFlightCapture=null;return {data};}
 const date=state.days[state.index],cell=context.table.querySelector('#day_'+Number(date.slice(-2)));
 if(!cell||!visible(cell))return flightWait(date+' 날짜 셀');
 if(stage==='parse_flights_open'){
  if(state.active)return next('parse_flights');
  if(mobileFlightPanels().length)return flightWait(date+' 기존 모바일 상세창 닫힘');
  const before=detailLists(context.d),beforeDate=detailDate(context.d),alreadySelected=cell.classList.contains('-selected')&&beforeDate===date;
  const initial=alreadySelected&&state.index===0&&state.initialDate===date&&before.length===1&&state.initialList===before[0]&&state.initialEligible;
  if(alreadySelected&&!initial)return flightWait(date+' 현재 검색에서 생성된 초기 항공편 목록 확인');
  const active={date,beforeDate,initial,clicked:false,signature:null,stableAt:0};state.active=active;
  if(!initial){if(cell.getAttribute('aria-disabled')==='true'){state.active=null;return flightWait(date+' 선택 불가');}cell.scrollIntoView({block:'center',inline:'nearest',behavior:'instant'});active.clicked=true;cell.click();}
  return {next:'parse_flights',detail:'항공편별 좌석 확인 '+(state.index+1)+'/'+state.days.length+' · '+date};
 }
 const active=state.active;if(!active||active.date!==date)return fail('항공편별 좌석 확인 순서가 달라 저장하지 않았습니다.');
 const selected=all('tbody td.-selected',context.table).filter(visible);
 if(stage==='parse_flights_confirm'){
  if(!active.confirmed||!active.pendingRows)return fail('모바일 항공편 확정 순서가 다릅니다.');
  if(selected.length!==1||selected[0]!==cell||mobileFlightPanels().length)return flightWait(date+' 모바일 날짜 확정 및 상세창 닫힘');
  return recordFlightRows(state,date,active.pendingRows,active.observedAt);
 }
 const panel=flightPanel(context.d);if(!panel)return flightWait(date+' 상세창 식별');
 if(detailDate(panel.root)!==date||(!panel.mobile&&(selected.length!==1||selected[0]!==cell)))return flightWait(date+' 선택 날짜 반영');
 if(panel.mobile&&(!active.clicked||active.initial))return flightWait(date+' 현재 날짜 선택으로 열린 모바일 상세창');
 const lists=detailLists(panel.root);if(lists.length!==1)return flightWait(date+' 항공편 목록');
 // The observed official UI legitimately keeps identical row nodes across dates.
 // Our accepted date action + completed exact header and stable rows is UI evidence,
 // not a claim that another network request occurred or every possible row exists.
 if(!active.initial&&(!active.clicked||active.beforeDate===date))return flightWait(date+' 날짜 선택 반영');
 const rows=flightRows(lists[0]);if(!rows)return flightWait(date+' 완전한 항공편·등급 자료');
 const observed=state.snapshot.dates.find(d=>d.date===date);if(!observed||seatFields.some(field=>observed[field]!==rows.some(row=>row[field])))return flightWait(date+' 달력·항공편 등급 일치');
 const signature=JSON.stringify(rows);if(active.signature!==signature||active.panel!==panel.root){active.panel=panel.root;active.signature=signature;active.stableAt=Date.now();return flightWait(date+' 항공편 목록 안정화');}
 if(Date.now()-active.stableAt<250)return flightWait(date+' 항공편 목록 안정화');
 const reread=flightRows(lists[0]);if(!reread||JSON.stringify(reread)!==signature)return flightWait(date+' 항공편 목록 갱신');
 if(panel.mobile){
  const buttons=all('button',panel.root).filter(e=>visible(e)&&text(e)==='해당 날짜 선택');
  if(buttons.length!==1||buttons[0].disabled||buttons[0].getAttribute('aria-disabled')==='true')return flightWait(date+' 모바일 날짜 확정 버튼');
  active.pendingRows=rows;active.observedAt=new Date().toISOString();active.confirmed=true;buttons[0].click();return next('parse_flights_confirm');
 }
 return recordFlightRows(state,date,rows,new Date().toISOString());
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
 const days=new Date(Date.UTC(year,month,0)).getUTCDate();const seen=new Set();const dates=[];
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
  // No-flight cells have not been verified on a live page. Preserve as UNKNOWN, never invent no-seat.
  dates.push(item);
 }
 if(seen.size!==days)return fail('한 달 날짜가 완전하지 않아 저장하지 않았습니다.');
 if(dates.every(d=>d.status==='UNKNOWN'))return wait();
 dates.sort((a,b)=>a.date.localeCompare(b.date));
 const source=window.__tongSource||q.testSource;
 if(!source||!Number.isFinite(Date.parse(source)))return fail('대한항공 정보 기준 시각을 확인하지 못했습니다.');
 const data={origin:q.origin,destination:q.destination,month:q.month,sourceUpdatedAt:source,collectedAt:new Date().toISOString(),dates};
 // parse_test is an explicit aggregate-only offline-fixture seam, never a normal stage transition.
 if(stage==='parse_test')return {data};
 stopFlightWatch(window.__tongKeFlightCapture?.active);
 const validBound=value=>typeof value==='string'&&/^\d{4}-\d{2}-\d{2}$/.test(value)&&value.startsWith(q.month+'-')&&Number.isFinite(Date.parse(value+'T00:00:00Z'))&&new Date(value+'T00:00:00Z').toISOString().slice(0,10)===value;
 if(!validBound(q.captureStart)||!validBound(q.captureEnd)||q.captureStart>q.captureEnd)return fail('항공편별 좌석 확인 기간이 올바르지 않아 저장하지 않았습니다.');
 data.captureStart=q.captureStart;data.captureEnd=q.captureEnd;
 for(const day of dates){day.awardCalendarFlags=Object.fromEntries(seatFields.map(field=>[field,day[field]]));day.flights=[];day.flightDetailStatus=day.status==='OBSERVED'&&!seatFields.some(field=>day[field])?'NO_MARKS':'UNKNOWN';if(day.date<q.captureStart||day.date>q.captureEnd){day.status='UNKNOWN';day.flightDetailStatus='UNKNOWN';for(const field of seatFields)day[field]=false;}}
 data.flightDetailPolicyVersion=1;const positive=dates.filter(day=>seatFields.some(field=>day[field])).map(day=>day.date);
 if(!positive.length)return {data};
 const proof=window.__tongKeFlightSearch,lists=detailLists(d),initialList=lists.length===1?lists[0]:null;
 const initialEligible=!!(proof&&!proof.used&&proof.key===captureKey()&&proof.document===document&&initialList&&!proof.beforeLists.includes(initialList)&&(d!==proof.beforeDialog||tables[0]!==proof.beforeTable));if(proof)proof.used=true;
 window.__tongKeFlightCapture={key:captureKey(),snapshot:data,dialog:d,table:tables[0],days:positive,index:0,active:null,initialList,initialDate:detailDate(d),initialEligible};return next('parse_flights_open');
}
return fail('지원하지 않는 조회 단계');
})


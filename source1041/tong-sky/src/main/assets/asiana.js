// Public calendar adapter; selector reference: we-insub/Korean_air (MIT).
// No login, private endpoint, fingerprint modification, or challenge retry.
(function(stage,q){try{
 const text=document.body?.innerText||'';
 const shown=e=>{if(!e)return false;const r=e.getBoundingClientRect(),s=getComputedStyle(e);return r.width>0&&r.height>0&&s.display!=='none'&&s.visibility!=='hidden'&&s.opacity!=='0';};
 if(/access denied|접근.{0,8}(제한|차단)|captcha|보안 확인/i.test(text)||shown(document.querySelector('#sec-overlay')))return {error:'아시아나 보안 확인 또는 접근 제한 · 재시도하지 않습니다.'};
 const find=s=>document.querySelector(s);
 const rendered=e=>{if(!shown(e))return false;for(let p=e;p;p=p.parentElement){const style=getComputedStyle(p);if(style.display==='none'||style.visibility==='hidden'||style.visibility==='collapse'||style.opacity==='0')return false;}return true;};
 const findShown=s=>[...document.querySelectorAll(s)].find(shown);
 // The official .layer_wrap has height:0 even when its positioned popup is open.
 // Check CSS hiding on the wrapper, then the rendered popup/controls inside it.
 const popupOpen=layer=>{
  if(!layer)return false;
  for(let e=layer;e;e=e.parentElement){const s=getComputedStyle(e);if(s.display==='none'||s.visibility==='hidden'||s.visibility==='collapse'||s.opacity==='0')return false;}
  return [...layer.querySelectorAll('.layer_pop, a, button')].some(shown);
 };
 const click=(e,next)=>{if(!shown(e)||e.disabled)return {wait:true};e.scrollIntoView({block:'center',behavior:'instant'});e.click();return {next};};
 const host=typeof window==='undefined'?globalThis:window,reuseKey=[q.origin,q.destination,q.month].join('|');
 const reusePage=()=>{try{return location.protocol==='https:'&&['flyasiana.com','www.flyasiana.com','m.flyasiana.com'].includes(location.hostname)&&!location.username&&!location.password&&(!location.port||location.port==='443')&&location.pathname==='/I/KR/KO/MileageSeatSearch.do';}catch(_){return false;}};
 const currentSource=()=>{const m=text.match(/좌석 상황은 대한민국 시간\s*\(\s*(\d{4})\s*[.년]\s*(\d{1,2})\s*[.월]\s*(\d{1,2})\s*[.일]?\s*(\d{1,2}):(\d{2})\s*\)/);return m?`${m[1]}-${m[2].padStart(2,'0')}-${m[3].padStart(2,'0')}T${m[4].padStart(2,'0')}:${m[5]}:00+09:00`:null;};
 const currentReuse=()=>host.__tongSkyOzReuse?.key===reuseKey?host.__tongSkyOzReuse:null;
 const disconnectReuse=state=>{try{state?.observer?.disconnect();}catch(_){}};
 const coldReuse=detail=>{let state=currentReuse();if(state?.submitted)return {error:'이미 조회한 항목은 화면 재사용으로 다시 요청하지 않습니다.'};if(state?.cold)return {wait:true};disconnectReuse(state);state={key:reuseKey,cold:true,submitted:false};host.__tongSkyOzReuse=state;host.__tongSkyOzSuccess=null;return {cold:true,detail};};
 const sameForm=()=>find('#tripType')?.value==='OW'&&find('#departureAirport1')?.value===q.origin&&find('#arrivalAirport1')?.value===q.destination;
 const monthApplied=()=>{const [y,m]=q.month.split('-');return (find('#span_dep_date')?.textContent||'').trim()===y.slice(-2)+'.'+m&&(find('#departureDate1')?.value||'').replace(/[^0-9]/g,'').startsWith(y+m);};
 const reusableControl=e=>rendered(e)&&!e.disabled&&e.getAttribute('aria-disabled')!=='true';
 const currentSubmit=()=>host.__tongSkyOzSubmit?.key===reuseKey?host.__tongSkyOzSubmit:null;
 const disconnectSubmit=state=>{try{state?.observer?.disconnect();}catch(_){}};
 const resultNodes='td.fc-day-top.bg_no_seat[data-date],a[id^="cal_"]';
 const bindSubmit=()=>{
  disconnectSubmit(host.__tongSkyOzSubmit);
  const calendar=find('#depCalendar'),state={key:reuseKey,calendar,hadResult:!!calendar?.querySelectorAll(resultNodes).length,cleared:false};
  if(calendar&&state.hadResult&&typeof MutationObserver==='function'){
   state.observer=new MutationObserver(changes=>{for(const change of changes){if(change.type!=='childList')continue;for(const node of change.removedNodes)if(node.nodeType===1&&(node.matches?.('td[data-date],a[id^="cal_"],tbody,table')||node.querySelector?.('td[data-date],a[id^="cal_"],tbody,table')))state.cleared=true;}});
   state.observer.observe(calendar,{childList:true,subtree:true});
  }
  host.__tongSkyOzSubmit=state;return state;
 };
 if(stage==='reuse'){
  let state=currentReuse();if(state?.cold)return {wait:true};if(state?.submitted)return {wait:true,detail:'이미 시작한 월 조회 응답을 기다립니다.'};
  const success=host.__tongSkyOzSuccess,prior=success?.month,target=/^\d{4}-\d{2}$/.test(q.month||'')?q.month:null;
  let consecutive=false;if(prior&&target){const [y,m]=prior.split('-').map(Number),d=new Date(Date.UTC(y,m,1));consecutive=`${d.getUTCFullYear()}-${String(d.getUTCMonth()+1).padStart(2,'0')}`===target;}
  const calendar=find('#depCalendar'),opener=find('#sCalendarMonth'),search=find('#btn_MileageSeat_search');
  if(!reusePage()||!success||success.origin!==q.origin||success.destination!==q.destination||!consecutive||success.calendar!==calendar||!rendered(calendar)||!sameForm()||currentSource()!==success.source||!reusableControl(opener)||!reusableControl(search)||typeof MutationObserver!=='function')return coldReuse('공식 화면의 이전 조회·노선·월 선택 상태를 확인하지 못해 새 화면을 엽니다.');
  if(!state){state={key:reuseKey,phase:'open',at:Date.now(),calendar,submitted:false,cleared:false};host.__tongSkyOzReuse=state;}
  if(state.phase==='open'){
   const [y,m]=q.month.split('-');if(!find(`.month_btn[data-year="${y}"][data-month="${Number(m)}"]`))return coldReuse('공식 화면의 다음 월 항목을 확인하지 못해 새 화면을 엽니다.');
   opener.scrollIntoView({block:'center',behavior:'instant'});opener.click();state.phase='pick';state.at=Date.now();return {wait:true,detail:'기존 공식 화면에서 다음 월 선택 중'};
  }
  if(state.phase==='pick'){
   const [y,m]=q.month.split('-'),button=findShown(`.month_btn[data-year="${y}"][data-month="${Number(m)}"]`);
   if(!reusableControl(button)){if(Date.now()-state.at>2000)return coldReuse('다음 월 선택 화면을 확인하지 못해 새 화면을 엽니다.');return {wait:true};}
   button.click();state.phase='verify';state.at=Date.now();return {wait:true};
  }
  if(!sameForm())return coldReuse('다음 월 선택 중 노선 설정이 바뀌어 새 화면을 엽니다.');
  if(!monthApplied()){if(Date.now()-state.at>2000)return coldReuse('다음 월의 실제 입력값을 확인하지 못해 새 화면을 엽니다.');return {wait:true};}
  state.observer=new MutationObserver(changes=>{for(const change of changes){if(change.type!=='childList')continue;for(const node of change.removedNodes){if(node.nodeType===1&&(node.matches?.('td[data-date],a[id^="cal_"],tbody,table')||node.querySelector?.('td[data-date],a[id^="cal_"],tbody,table')))state.cleared=true;}}});
  state.observer.observe(calendar,{childList:true,subtree:true});state.submitted=true;state.phase='submitted';search.click();return {next:'result',submitted:true};
 }
 // Select from the airline's public city list. A private display need not have
 // keyboard focus, so key events cannot be used to drive autocomplete reliably.
 const opening=departure=>{
  const host=typeof window==='undefined'?globalThis:window,key=[q.origin,q.destination,q.month].join('|');
  if(!host.__tongSkyOzOpening||host.__tongSkyOzOpening.key!==key)host.__tongSkyOzOpening={key,dep:{attempts:0,at:0},arr:{attempts:0,at:0}};
  return host.__tongSkyOzOpening[departure?'dep':'arr'];
 };
 const activateOpener=e=>{
  e.scrollIntoView({block:'center',behavior:'instant'});
  // The official form also binds its airport opener to delegated mousedown.
  if(typeof MouseEvent==='function'&&typeof e.dispatchEvent==='function'){
   e.dispatchEvent(new MouseEvent('mousedown',{bubbles:true,cancelable:true,button:0,buttons:1}));
   e.dispatchEvent(new MouseEvent('mouseup',{bubbles:true,cancelable:true,button:0,buttons:0}));
  }
  e.click();
 };
 const uiReady=departure=>{
  if(document.readyState&&document.readyState!=='complete')return false;
  const input=find(departure?'#txtDepartureAirport1':'#txtArrivalAirport1');
  return !input||input.classList.contains('ui-autocomplete-input');
 };
 const airport=(departure,step)=>{
  const code=departure?q.origin:q.destination;
  if(!/^[A-Z]{3}$/.test(code||''))return {error:'공항 코드 형식 오류'};
  const field=find(departure?'#departureAirport1':'#arrivalAirport1');
  const next=departure?'destination':'month';
  if(step==='open'){
   if(field?.value===code)return {next};
   if(!uiReady(departure))return {wait:true,detail:'공항 입력 초기화 대기 · '+(document.readyState||'확인 중')};
   const button=findShown(departure?'#btn_depAllAirport1':'#btn_arrAllAirport1');
   if(!shown(button))return {wait:true,detail:'보이는 도시 목록 버튼 없음'};
   const state=opening(departure);state.attempts++;state.at=Date.now();activateOpener(button);
   return {next:departure?'origin_pick':'destination_pick'};
  }
  if(step==='verify')return field?.value===code?{next}:{wait:true,detail:'공항 선택값 미반영 ('+code+')'};
  const layer=find(departure?'#depAllAirport1':'#arrAllAirport1');
  if(!popupOpen(layer)){
   const state=opening(departure),now=Date.now();
   // Only reopen a closed UI, never reload or resubmit an airline query.
   // A retry returns wait, preserving the native stage timeout and attempt cap.
   if(state.attempts>0&&state.attempts<3&&now-state.at>=2000&&uiReady(departure)){
    const button=findShown(departure?'#btn_depAllAirport1':'#btn_arrAllAirport1');
    if(shown(button)){state.attempts++;state.at=now;activateOpener(button);}
   }
   const style=layer?getComputedStyle(layer):null;
   const items=layer?[...layer.querySelectorAll('a,button')].filter(shown).length:0;
   return {wait:true,detail:'도시 목록 열림 대기 · 열기 '+state.attempts+'/3 · '+(style?style.display+'/'+style.visibility:'목록 없음')+' · 표시 항목 '+items};
  }
  const rows=[...layer.querySelectorAll('li[airport]')].filter(e=>e.getAttribute('airport')===code);
  const selected=rows.map(e=>e.querySelector('a')).find(shown);
  if(selected)return click(selected,departure?'origin_verify':'destination_verify');
  // Hidden city groups are exposed only by their official region tabs.
  const labels={KR:'한국',JP:'일본',CE:'동북아시아',EA:'동남아시아',SA:'서남아시아',US:'미주',EU:'유럽',AU:'대양주',ME:'중동',RU:'러시아'};
  // The UI group (area=CE) and a city's management area (mgtArea=JP)
  // differ. Official clickArea(this) selects the national_listbox by tab
  // index, with no area argument. Follow that relationship before fallbacks.
  const area=rows.map(e=>e.getAttribute('area')||e.getAttribute('mgtarea')).find(a=>/^[A-Z]{2,3}$/.test(a||''));
  const group=rows.map(e=>e.closest?.('.national_listbox')).find(Boolean);
  const scope=group?.closest('.national')||layer;
  const groups=[...scope.querySelectorAll('.national_listbox')];
  const groupTabs=[...scope.querySelectorAll('.list_nation > ul > li > a[onclick*="clickArea"]')];
  const groupIndex=groups.indexOf(group);
  const linked=groupIndex>=0&&groups.length===groupTabs.length?groupTabs[groupIndex]:null;
  const tabs=[...layer.querySelectorAll('a[onclick*="clickArea"]')].filter(shown);
  const region=shown(linked)?linked:tabs.find(e=>area&&new RegExp("['\"]"+area+"['\"]").test(e.getAttribute?.('onclick')||''))||tabs.find(e=>labels[area]&&e.textContent.replace(/\s+/g,'')===labels[area].replace(/\s+/g,''));
  if(region){
   const state=opening(departure),now=Date.now();
   const regionKey=linked===region?'group-'+groupIndex:area;
   if(!state.region||state.region.area!==regionKey)state.region={area:regionKey,attempts:0,at:0};
   const tab=state.region;
   // A dropped region-tab click must not click every polling tick or keep
   // extending the native stage deadline. Opening the same UI is bounded.
   if(tab.attempts<3&&(tab.attempts===0||now-tab.at>=2000)){
    const result=click(region,departure?'origin_pick':'destination_pick');
    if(result.next){tab.attempts++;tab.at=now;if(tab.attempts===1)return result;}
   }
   return {wait:true,detail:'지역 목록 표시 대기 · '+(labels[area]||area||'그룹 '+groupIndex)+' · 선택 '+tab.attempts+'/3'};
  }
  // An open popup can precede its asynchronous city rows. Wait in the same
  // UI without reloading, resubmitting, or inventing an unavailable-flight result.
  const state=opening(departure),now=Date.now();
  if(state.listAt===undefined)state.listAt=now;
  if(now-state.listAt<8000)return {wait:true,detail:'도시 목록 자료 대기 · '+code};
  return {error:'공식 도시 목록에서 '+code+' 공항을 확인하지 못했습니다. 기존 결과를 유지합니다.',code:'AIRPORT_NOT_LISTED'};
 };
 if(stage==='oneway'&&document.readyState&&document.readyState!=='complete')return {wait:true,detail:'첫 화면 로딩 대기 · '+document.readyState};
 if(stage==='oneway'){
  const field=find('#tripType');
  if(!field)return {wait:true,detail:'편도 실제 입력값 초기화 대기'};
  if(field.value==='OW')return {next:'origin'};
  const host=typeof window==='undefined'?globalThis:window,key=[q.origin,q.destination,q.month].join('|'),now=Date.now();
  if(!host.__tongSkyOzTrip||host.__tongSkyOzTrip.key!==key)host.__tongSkyOzTrip={key,attempts:0,at:0};
  const state=host.__tongSkyOzTrip;
  if(state.attempts<3&&(state.attempts===0||now-state.at>=2000)){
   const button=[...document.querySelectorAll('a')].find(e=>e.textContent.trim()==='편도'&&shown(e));
   if(shown(button)){state.attempts++;state.at=now;button.scrollIntoView({block:'center',behavior:'instant'});button.click();}
  }
  return field.value==='OW'?{next:'origin'}:{wait:true,detail:'편도 선택 반영 대기 · 실제 '+(field.value||'미설정')+' · 선택 '+state.attempts+'/3'};
 }
 // A visual tab or a matching departure month does not establish one-way mode.
 // The official RT validator also requires an arrival month. Re-enter only the
 // pre-submit form flow if its canonical trip mode changed; never retry results.
 if(['origin','origin_pick','origin_verify','destination','destination_pick','destination_verify','month','month_pick','month_verify','search'].includes(stage)){
  const field=find('#tripType');
  if(!field)return {wait:true,detail:'편도 실제 입력값 초기화 대기'};
  if(field.value!=='OW')return {next:'oneway',detail:'편도 선택값 변경 · 조회 전 다시 확인'};
 }

 if(stage==='origin')return airport(true,'open');
 if(stage==='origin_pick')return airport(true,'pick');
 if(stage==='origin_verify')return airport(true,'verify');
 if(stage==='destination'){
  if(find('#departureAirport1')?.value!==q.origin)return {wait:true};
  return airport(false,'open');
 }
 if(stage==='destination_pick')return airport(false,'pick');
 if(stage==='destination_verify')return airport(false,'verify');
 if(stage==='month'){if(find('#arrivalAirport1')?.value!==q.destination)return {wait:true};return click(findShown('#sCalendarMonth'),'month_pick');}
 if(stage==='month_pick'){const [y,m]=q.month.split('-');return click(findShown(`.month_btn[data-year="${y}"][data-month="${Number(m)}"]`),'month_verify');}
 if(stage==='month_verify'){const [y,m]=q.month.split('-'),label=(find('#span_dep_date')?.textContent||'').trim(),value=(find('#departureDate1')?.value||'').replace(/[^0-9]/g,'');if(label!==y.slice(-2)+'.'+m||!value.startsWith(y+m))return {wait:true};return {next:'search'};}
 // Airline input handlers can reset a canonical airport after its earlier
 // verification. Re-enter only the pre-submit selection flow; never submit a
 // stale itinerary and never resubmit from the result stage.
 if(stage==='search'){if(find('#departureAirport1')?.value!==q.origin)return {next:'origin',detail:'조회 전 출발 공항 다시 확인'};if(find('#arrivalAirport1')?.value!==q.destination)return {next:'destination',detail:'조회 전 도착 공항 다시 확인'};const [y,m]=q.month.split('-');const value=(find('#departureDate1')?.value||'').replace(/[^0-9]/g,'');if((find('#span_dep_date')?.textContent||'').trim()!==y.slice(-2)+'.'+m||!value.startsWith(y+m))return {error:'탑승월의 실제 입력값이 반영되지 않아 조회하지 않았습니다.'};if(currentSubmit())return {next:'result',submitted:true};const button=findShown('#btn_MileageSeat_search');if(!shown(button)||button.disabled)return {wait:true};bindSubmit();return click(button,'result');}
 if(stage!=='result')return {error:'지원하지 않는 조회 단계'};
 // A prior calendar may remain in the DOM while the next response is hidden.
 // Width alone does not prove that a fresh result is rendered.
 const calendar=find('#depCalendar');if(!rendered(calendar))return {wait:true,detail:'표시된 결과 달력 대기'};
 const submitted=currentSubmit();if(submitted?.hadResult&&calendar===submitted.calendar&&!submitted.cleared)return {wait:true,detail:'새 조회의 달력 교체 대기 · 이전 같은 월 결과는 저장하지 않습니다.'};
 const reuse=currentReuse();if(reuse){
  if(!reuse.submitted)return {error:'화면 재사용의 실제 월 조회를 확인하지 못했습니다.'};
  if(!sameForm())return {error:'화면 재사용 뒤 선택 노선 또는 편도 설정이 바뀌었습니다.'};
  if(calendar===reuse.calendar&&!reuse.cleared)return {wait:true,detail:'이전 달력 교체 대기 · 이전 결과는 저장하지 않습니다.'};
  const prior=host.__tongSkyOzSuccess?.month;if(prior){const [y,m]=prior.split('-'),head=calendar.querySelector('h2')?.textContent||'';if(head.includes(y+'년')&&new RegExp('(?:^|\\s)'+Number(m)+'월').test(head))return {wait:true,detail:'이전 월 달력 응답은 저장하지 않습니다.'};}
 }
 if(find('#departureAirport1')?.value!==q.origin||find('#arrivalAirport1')?.value!==q.destination)return {error:'선택 노선 불일치'};
 const [year,month]=q.month.split('-'),head=calendar.querySelector('h2')?.textContent||'';
 if(!head.includes(year+'년')||!new RegExp('(?:^|\\s)'+Number(month)+'월').test(head))return {error:'조회 월 불일치'};
 const source=text.match(/좌석 상황은 대한민국 시간\s*\(\s*(\d{4})\s*[.년]\s*(\d{1,2})\s*[.월]\s*(\d{1,2})\s*[.일]?\s*(\d{1,2}):(\d{2})\s*\)/);
 if(!source)return {error:'아시아나 기준 시각 확인 실패'};
 const sourceUpdatedAt=`${source[1]}-${source[2].padStart(2,'0')}-${source[3].padStart(2,'0')}T${source[4].padStart(2,'0')}:${source[5]}:00+09:00`;
 const nodes=[...calendar.querySelectorAll('a[id^="cal_"]')].filter(rendered),count=new Date(Number(year),Number(month),0).getDate(),seen=new Set(),dates=[];
 const emptyDay=date=>({date,status:'UNKNOWN',economyAward:false,premiumAward:false,prestigeAward:false,premiumUpgrade:false,prestigeUpgrade:false,firstCombined:false,flights:[]});
 const today=new Date().toLocaleDateString('sv-SE',{timeZone:'Asia/Seoul'});
 // The official calendar has two independent representations: cal_* links
 // contain flights, while a date-top cell with bg_no_seat is an explicit empty
 // result and has no flight link or data-economy/data-business attributes.
 const explicitNoFlight=cell=>!!cell&&[cell.getAttribute('aria-label'),cell.getAttribute('title'),cell.innerText].some(v=>/^(?:[0-9]{1,2}\s+)?(?:비운항|운항하지 않음|운항편 없음|No flights scheduled|Not operating)$/i.test(String(v||'').trim().replace(/\s+/g,' ')));
 const topDays=new Map(),padding=new Set();
 for(const cell of calendar.querySelectorAll('td.fc-day-top[data-date]')){
  if(!rendered(cell))continue;
  const date=(cell.getAttribute('data-date')||'').replace(/\./g,'-');
  const label=(cell.querySelector('.fc-day-number')?.textContent||'').trim();
  if(!/^\d{1,2}$/.test(label)||Number(label)!==Number(date.slice(-2)))return {error:'공식 날짜 셀 표시 불일치 · '+date};
  const parsed=/^\d{4}-\d{2}-\d{2}$/.test(date)?new Date(date+'T00:00:00Z'):null,valid=parsed&&Number.isFinite(parsed.getTime())&&parsed.toISOString().slice(0,10)===date;
  if(cell.classList.contains('fc-other-month')){if(valid&&date.slice(0,7)!==q.month){padding.add(date);continue;}return {error:'공식 다른 월 날짜 셀 범위 오류'};}
  if(!new RegExp('^'+q.month+'-\\d{2}$').test(date)||!valid||+date.slice(-2)<1||+date.slice(-2)>count||topDays.has(date))return {error:'공식 날짜 셀 누락·중복 또는 범위 오류'};
  topDays.set(date,cell);
 }
 for(const e of nodes){const date=(e.getAttribute('data-date')||'').replace(/\./g,'-');if(padding.has(date))continue;if(!new RegExp('^'+q.month+'-\\d{2}$').test(date)||+date.slice(-2)<1||+date.slice(-2)>count||seen.has(date))return {error:'날짜 누락 또는 중복'};seen.add(date);
 if(explicitNoFlight(topDays.get(date)))return {error:'같은 날짜에 운항편 자료와 비운항 안내가 함께 있어 저장하지 않습니다.'};
 if(topDays.get(date)?.classList.contains('bg_no_seat'))return {error:'같은 날짜에 좌석 데이터와 좌석 없음 표시가 함께 있어 저장하지 않습니다.'};
 const d={date,status:'OBSERVED',economyAward:false,premiumAward:false,prestigeAward:false,premiumUpgrade:false,prestigeUpgrade:false,firstCombined:false,flights:[]};
 for(const attr of ['data-economy','data-business']){if(!e.hasAttribute(attr))return {error:'좌석 데이터 필드 누락 · 좌석 없음으로 처리하지 않습니다.'};const rows=JSON.parse(e.getAttribute(attr));if(!Array.isArray(rows))return {error:'좌석 데이터 형식 변경'};
 for(const f of rows){if(f.brdCd!==q.origin||f.ofpCd!==q.destination||!String(f.deptrDt).startsWith(date.replace(/-/g,'')))return {error:'항공편 노선 또는 날짜 불일치'};if(!['X','I','P'].includes(f.bkgCd)||!/^\d+$/.test(String(f.availQty)))return {error:'확인되지 않은 예약 등급 또는 좌석 값'};if(+f.availQty>0){if(f.bkgCd==='X')d.economyAward=true;if(f.bkgCd==='I')d.prestigeAward=true;if(f.bkgCd==='P')d.prestigeUpgrade=true;d.flights.push({number:'OZ'+f.fltNbr,departure:String(f.deptrDt).slice(8,10)+':'+String(f.deptrDt).slice(10,12),bookingClass:f.bkgCd});}}
 }dates.push(d);}
 const unresolved=[];
 for(let n=1;n<=count;n++){
  const date=q.month+'-'+String(n).padStart(2,'0');if(seen.has(date))continue;
  const cell=topDays.get(date);
  if(explicitNoFlight(cell)){const day=emptyDay(date);day.status='NO_FLIGHT';dates.push(day);continue;}
  if(cell?.classList.contains('bg_no_seat')){const day=emptyDay(date);day.status='OBSERVED';dates.push(day);continue;}
  const past=cell||calendar.querySelector(`td[data-date="${date}"]`);
  if(past?.classList.contains('fc-past')&&date<today){dates.push(emptyDay(date));continue;}
  unresolved.push(date);
 }
 // Incomplete rendering stays within the native result-stage deadline. Never
 // infer no-seat from a bare/absent date cell and never resubmit the search.
 if(unresolved.length)return {wait:true,detail:'달력 날짜 응답 대기 · '+q.origin+'-'+q.destination+'-'+q.month+' · 미확인 '+unresolved.length+'일 ('+unresolved.slice(0,3).join(', ')+') · 좌석 링크 '+nodes.length};
 dates.sort((a,b)=>a.date.localeCompare(b.date));
 const data={carrier:'OZ',origin:q.origin,destination:q.destination,month:q.month,sourceUpdatedAt,collectedAt:new Date().toISOString(),dates};
 const reuseReady=reusePage()&&sameForm()&&monthApplied()&&reusableControl(find('#sCalendarMonth'))&&reusableControl(find('#btn_MileageSeat_search'));
 disconnectReuse(reuse);disconnectSubmit(submitted);host.__tongSkyOzSubmit=null;host.__tongSkyOzReuse=null;host.__tongSkyOzSuccess=reuseReady?{origin:q.origin,destination:q.destination,month:q.month,source:sourceUpdatedAt,calendar}:null;
 return {data,reuseReady};
}catch(e){return {error:'아시아나 화면 자료를 검증하지 못했습니다. 기존 자료를 유지합니다.'};}})

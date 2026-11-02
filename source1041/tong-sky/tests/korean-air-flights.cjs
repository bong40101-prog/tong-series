'use strict';
const assert=require('assert'),fs=require('fs'),path=require('path'),{JSDOM}=require('jsdom');
const source=fs.readFileSync(process.env.CALENDAR_ADAPTER||path.join(__dirname,'../src/main/assets/calendar.js'),'utf8');
const calendar=fs.readFileSync(path.join(__dirname,'../src/androidTest/assets/calendar.html'),'utf8');
const live=fs.readFileSync(path.join(__dirname,'../../tests/evidence138/ke-bonus-detail-live-20270221.html'),'utf8');
const noSeatLive=fs.readFileSync(path.join(__dirname,'../../tests/evidence138/ke-bonus-detail-no-seat-live-20270206.html'),'utf8');
const q={origin:'ICN',destination:'CDG',month:'2027-02',captureStart:'2027-02-01',captureEnd:'2027-02-28',testSource:'2026-11-01T23:00:00+09:00'};
function fixture(positive=[21]){
 const a=new JSDOM(calendar),d=a.window.document,b=new JSDOM(live),ld=b.window.document;
 d.querySelector('#travelCalendarListBtn').textContent='2027년 2월';
 for(const cell of d.querySelectorAll('td[id^="day_"]')){const n=Number(cell.id.slice(4));if(n>28){cell.remove();continue;}cell.classList.remove('-selected');cell.querySelectorAll('.bonus-calendar__icons,.ux-class-icon.-no-seat').forEach(e=>e.remove());if(positive.includes(n)){cell.removeAttribute('aria-disabled');const icons=ld.querySelector('#day_21 .bonus-calendar__icons').cloneNode(true);cell.querySelector('.bonus-calendar__day').append(icons);}else{const empty=d.createElement('span');empty.className='ux-class-icon -no-seat -gray';empty.textContent='좌석 없음';cell.querySelector('.bonus-calendar__day').append(empty);}}
 d.querySelector('#day_21').classList.add('-selected');d.querySelector('[role="dialog"]').append(ld.querySelector('.bonus-calendar__side-modal-scroll').cloneNode(true));
 const html=d.documentElement.outerHTML;a.window.close();b.window.close();return html;
}
function page(positive=[21]){
 const dom=new JSDOM(fixture(positive),{url:'https://www.koreanair.com/booking/book-and-manage/award-seat-availability',runScripts:'outside-only'}),w=dom.window,d=w.document;let clock=Date.parse('2026-11-02T03:00:00Z');
 const OriginalDate=w.Date;w.Date=class extends OriginalDate{constructor(...args){super(...(args.length?args:[clock]));}static now(){return clock;}};
 Object.defineProperty(w.HTMLElement.prototype,'innerText',{get(){return this.textContent;},configurable:true});w.HTMLElement.prototype.scrollIntoView=function(){};
 w.HTMLElement.prototype.getBoundingClientRect=function(){return {left:0,top:0,right:100,bottom:40,width:100,height:this.dataset.zero==='true'?0:40};};
 w.HTMLElement.prototype.getClientRects=function(){for(let e=this;e;e=e.parentElement)if(w.getComputedStyle(e).display==='none')return [];return [this.getBoundingClientRect()];};
 d.body.insertAdjacentHTML('afterbegin','<input id="bonusTripType_OW" type="radio" checked><button id="departureBtn1">출발지 ICN</button><button id="destinationBtn1">도착지 CDG</button><button id="test-query">조회</button>');
 const adapter=w.eval(source);d.querySelector('#test-query').onclick=()=>{const modal=d.querySelector('[role="dialog"]');modal.replaceWith(modal.cloneNode(true));};assert.equal(adapter('search',q).next,'result');
 const query={...q};return {w,d,query,run:(stage='parse')=>adapter(stage,query),advance:ms=>clock+=ms,close:()=>w.close()};
}
let passed=0;
function test(name,fn){try{fn();passed++;}catch(e){e.message=name+': '+e.message;throw e;}}
function withPage(fn,positive){const p=page(positive);try{fn(p);}finally{p.close();}}
function withNoSeatPage(fn){withPage(p=>{const capture=new JSDOM(noSeatLive),d=capture.window.document;p.query.captureStart='2027-02-06';p.query.captureEnd='2027-02-06';p.d.querySelector('#day_21').classList.remove('-selected');p.d.querySelector('#day_6').classList.add('-selected');p.d.querySelector('#day_6 .bonus-calendar__icons').replaceWith(p.d.importNode(d.querySelector('#day_6 .bonus-calendar__icons'),true));p.d.querySelector('#seatScroll').replaceWith(p.d.importNode(d.querySelector('#seatScroll'),true));p.d.querySelector('#bonusSeatSideScrollEl').replaceWith(p.d.importNode(d.querySelector('#bonusSeatSideScrollEl'),true));capture.window.close();fn(p);},[6]);}
function begin(p){const r=p.run();assert.equal(r.next,'parse_flights_open',JSON.stringify(r));assert.ok(!r.data);assert.equal(p.run('parse_flights_open').next,'parse_flights');}
function complete(p){const first=p.run('parse_flights');assert.ok(first.wait,JSON.stringify(first));p.advance(250);assert.equal(p.run('parse_flights').next,'parse_flights_open');const r=p.run('parse_flights_open');assert.ok(r.data,JSON.stringify(r));return r.data;}
function unresolved(p){const r=p.run('parse_flights');assert.ok(!r.data&&!r.next,JSON.stringify(r));assert.ok(r.wait||r.error);p.advance(1000);const again=p.run('parse_flights');assert.ok(!again.data&&!again.next,JSON.stringify(again));}
test('normal production parse never emits aggregate-only positive data',()=>withPage(p=>begin(p)));
test('normalized captured Feb21 detail retains KE901 E/P/U and KE5901 E without guessing either operator',()=>withPage(p=>{begin(p);const data=complete(p),day=data.dates[20];assert.equal(data.flightDetailPolicyVersion,1);assert.equal(day.flightDetailStatus,'CAPTURED');assert.equal(day.flightDetailDate,'2027-02-21');assert.ok(Number.isFinite(Date.parse(day.flightDetailObservedAt)));assert.equal(day.status,'OBSERVED');assert.equal(day.flights.length,2);assert.equal(day.flights[0].number,'KE901');assert.equal(day.flights[0].departureTime,'12:05');assert.equal(day.flights[0].prestigeAward,true);assert.equal(day.flights[0].prestigeUpgrade,true);assert.equal(day.flights[1].number,'KE5901');assert.equal(day.flights[1].departureTime,'13:20');assert.equal(day.flights[1].economyAward,true);assert.equal(day.flights[1].prestigeAward,false);assert.equal(day.flights[1].firstCombined,false);for(const f of day.flights){assert.ok(!('operatorCode'in f)&&!('serviceKind'in f));}assert.equal(data.sourceUpdatedAt,q.testSource);}));
test('all six legend entries are excluded from per-flight seat attribution',()=>withPage(p=>{begin(p);const day=complete(p).dates[20];assert.equal(day.flights[0].premiumAward,false);assert.equal(day.flights[0].premiumUpgrade,false);assert.equal(day.flights[0].firstCombined,false);}));
test('captured Feb6 explicit flight no-seat keeps number and time without claiming nonoperation',()=>withNoSeatPage(p=>{begin(p);const day=complete(p).dates[5];assert.equal(day.flightDetailDate,'2027-02-06');assert.equal(day.status,'OBSERVED');assert.equal(day.economyAward,true);assert.equal(day.prestigeAward,false);assert.equal(day.flights.length,2);assert.equal(day.flights[1].number,'KE5901');assert.equal(day.flights[1].departureTime,'13:20');assert.equal(day.flights[1].noSeat,true);for(const field of ['economyAward','premiumAward','prestigeAward','premiumUpgrade','prestigeUpgrade','firstCombined'])assert.equal(day.flights[1][field],false);assert.ok(!day.flights[1].operationStatus);}));
for(const [name,change] of [
 ['mixed positive and no-seat',p=>p.d.querySelectorAll('.bonus-calendar__flight-class ul')[1].append(p.d.querySelector('.bonus-calendar__flight-class li').cloneNode(true))],
 ['duplicate no-seat',p=>{const ul=p.d.querySelectorAll('.bonus-calendar__flight-class ul')[1];ul.append(ul.firstElementChild.cloneNode(true));}],
 ['hidden no-seat item',p=>p.d.querySelectorAll('.bonus-calendar__flight-class li')[1].style.display='none'],
 ['hidden no-seat icon',p=>p.d.querySelector('.bonus-calendar__flight-class .icon.-no-seat').style.display='none'],
 ['wrong no-seat icon',p=>p.d.querySelector('.bonus-calendar__flight-class .icon.-no-seat').className='icon -class-e'],
 ['unknown empty-seat label',p=>p.d.querySelectorAll('.bonus-calendar__flight-class li ._hidden')[1].textContent='좌석 정보 미확인']
])test(name+' cannot authenticate an all-false flight row',()=>withNoSeatPage(p=>{begin(p);change(p);unresolved(p);}));
test('identical row signatures need the full stability interval',()=>withPage(p=>{begin(p);assert.ok(p.run('parse_flights').wait);p.advance(249);assert.ok(p.run('parse_flights').wait);p.advance(1);assert.equal(p.run('parse_flights').next,'parse_flights_open');}));
for(const [name,change] of [
 ['wrong selected date header',p=>p.d.querySelector('#seatScroll').lastChild.textContent='2027년 02월 20일'],
 ['missing date header',p=>p.d.querySelector('#seatScroll').remove()],
 ['wrong selected cell',p=>{p.d.querySelector('#day_21').classList.remove('-selected');p.d.querySelector('#day_20').classList.add('-selected');}],
 ['duplicate selected cells',p=>p.d.querySelector('#day_20').classList.add('-selected')],
 ['hidden flight list',p=>p.d.querySelector('.bonus-calendar__flight-list').style.display='none'],
 ['zero-height flight list',p=>p.d.querySelector('.bonus-calendar__flight-list').dataset.zero='true'],
 ['duplicate flight row',p=>p.d.querySelector('.bonus-calendar__flight-list').append(p.d.querySelector('.bonus-calendar__flight-item').cloneNode(true))],
 ['missing flight number',p=>p.d.querySelector('.bonus-calendar__flight-num').remove()],
 ['missing departure time',p=>p.d.querySelector('.bonus-calendar__flight-time').remove()],
 ['invalid departure time',p=>p.d.querySelector('.bonus-calendar__flight-time').textContent='25:61'],
 ['missing seat classes',p=>p.d.querySelector('.bonus-calendar__flight-class').remove()],
 ['empty seat classes',p=>p.d.querySelector('.bonus-calendar__flight-class ul').replaceChildren()],
 ['unknown marker label',p=>p.d.querySelector('.bonus-calendar__flight-class li ._hidden').textContent='새 좌석 정보'],
 ['wrong icon for its accessibility label',p=>p.d.querySelector('.bonus-calendar__flight-class li .icon').className='icon -class-f'],
 ['duplicate row marker',p=>p.d.querySelector('.bonus-calendar__flight-class ul').append(p.d.querySelector('.bonus-calendar__flight-class li').cloneNode(true))],
 ['hidden only prestige row',p=>p.d.querySelector('.bonus-calendar__flight-item').style.display='none'],
 ['missing row with unique prestige flags',p=>p.d.querySelector('.bonus-calendar__flight-item').remove()],
 ['hidden positive icon',p=>p.d.querySelector('.bonus-calendar__flight-class .icon.-class-p').style.display='none']
])test(name+' stays unknown rather than saving invented zero-seat detail',()=>withPage(p=>{begin(p);change(p);unresolved(p);}));
test('hidden stale duplicate rows do not contaminate the two rendered flights',()=>withPage(p=>{begin(p);const hidden=p.d.querySelector('.bonus-calendar__flight-item').cloneNode(true);hidden.style.display='none';p.d.querySelector('.bonus-calendar__flight-list').append(hidden);assert.equal(complete(p).dates[20].flights.length,2);}));
test('a changed route during detail collection cannot save results',()=>withPage(p=>{begin(p);p.d.querySelector('#modals-travelCalendar-title').textContent='ICN FRA';assert.ok(p.run('parse_flights').error);}));
test('a legitimate accepted date click may retain identical row nodes as observed in the official UI',()=>withPage(p=>{
 p.d.querySelector('#day_21').classList.remove('-selected');p.d.querySelector('#day_20').classList.add('-selected');p.d.querySelector('#seatScroll').lastChild.textContent='2027년 02월 20일';let clicks=0;
 p.d.querySelector('#day_21').onclick=()=>{clicks++;p.d.querySelector('#day_20').classList.remove('-selected');p.d.querySelector('#day_21').classList.add('-selected');p.d.querySelector('#seatScroll').lastChild.textContent='2027년 02월 21일';};begin(p);assert.equal(complete(p).dates[20].flights.length,2);assert.equal(clicks,1);
}));
test('preupdated selected date and header without a current search-result generation cannot authenticate initial rows',()=>withPage(p=>{delete p.w.__tongKeFlightSearch;assert.equal(p.run().next,'parse_flights_open');assert.ok(p.run('parse_flights_open').wait);p.advance(1000);assert.ok(p.run('parse_flights_open').wait);}));
test('decorative icon class mutation cannot authenticate an unproven initial list',()=>withPage(p=>{delete p.w.__tongKeFlightSearch;assert.equal(p.run().next,'parse_flights_open');assert.ok(p.run('parse_flights_open').wait);p.d.querySelector('.bonus-calendar__flight-class .icon').classList.add('cosmetic-transition');p.advance(1000);assert.ok(p.run('parse_flights_open').wait);}));
test('the same search-result generation cannot be reused to authenticate another initial parse',()=>withPage(p=>{begin(p);complete(p);assert.equal(p.run().next,'parse_flights_open');assert.ok(p.run('parse_flights_open').wait);}));
test('a replaced list after one real day activation is accepted after independent date and stability checks',()=>withPage(p=>{
 p.d.querySelector('#day_21').classList.remove('-selected');p.d.querySelector('#day_20').classList.add('-selected');p.d.querySelector('#seatScroll').lastChild.textContent='2027년 02월 20일';let clicks=0;
 p.d.querySelector('#day_21').onclick=()=>{clicks++;p.d.querySelector('#day_20').classList.remove('-selected');p.d.querySelector('#day_21').classList.add('-selected');p.d.querySelector('#seatScroll').lastChild.textContent='2027년 02월 21일';const list=p.d.querySelector('.bonus-calendar__flight-list');list.replaceWith(list.cloneNode(true));};begin(p);assert.equal(complete(p).dates[20].flights.length,2);assert.equal(clicks,1);
}));
test('two positive dates are captured separately and each changed date is activated only once',()=>withPage(p=>{
 let clicks=0;p.d.querySelector('#day_22').onclick=()=>{clicks++;p.d.querySelector('#day_21').classList.remove('-selected');p.d.querySelector('#day_22').classList.add('-selected');p.d.querySelector('#seatScroll').lastChild.textContent='2027년 02월 22일';const list=p.d.querySelector('.bonus-calendar__flight-list');list.replaceWith(list.cloneNode(true));};
 begin(p);assert.ok(p.run('parse_flights').wait);p.advance(250);assert.equal(p.run('parse_flights').next,'parse_flights_open');assert.equal(p.run('parse_flights_open').next,'parse_flights');assert.ok(p.run('parse_flights').wait);p.advance(250);assert.equal(p.run('parse_flights').next,'parse_flights_open');const r=p.run('parse_flights_open');assert.ok(r.data);assert.equal(r.data.dates[20].flightDetailStatus,'CAPTURED');assert.equal(r.data.dates[21].flightDetailStatus,'CAPTURED');assert.equal(clicks,1);
},[21,22]));
test('all explicitly no-mark days remain observed without inventing flight operation',()=>withPage(p=>{const r=p.run();assert.ok(r.data);assert.equal(r.data.flightDetailPolicyVersion,1);for(const d of r.data.dates){assert.equal(d.status,'OBSERVED');assert.equal(d.flightDetailStatus,'NO_MARKS');assert.equal(d.flights.length,0);assert.ok(!d.operationStatus);assert.equal(d.prestigeAward,false);}},[]));
test('capture bounds are echoed and disabled positive dates outside the interval never stall capture',()=>withPage(p=>{p.query.captureStart='2027-02-21';p.query.captureEnd='2027-02-21';p.d.querySelector('#day_22').setAttribute('aria-disabled','true');begin(p);const data=complete(p);assert.equal(data.captureStart,'2027-02-21');assert.equal(data.captureEnd,'2027-02-21');const excluded=data.dates[21];assert.equal(excluded.status,'UNKNOWN');assert.equal(excluded.flightDetailStatus,'UNKNOWN');assert.equal(excluded.flights.length,0);assert.equal(excluded.prestigeAward,false);assert.equal(excluded.awardCalendarFlags.prestigeAward,true);},[21,22]));
test('missing or reversed capture bounds cannot create a production snapshot',()=>withPage(p=>{delete p.query.captureStart;assert.ok(p.run().error);p.query.captureStart='2027-02-22';p.query.captureEnd='2027-02-21';assert.ok(p.run().error);}));
test('the explicit offline parse_test seam remains aggregate-only and is never returned as a next stage',()=>withPage(p=>{const r=p.run('parse_test');assert.ok(r.data);assert.ok(!r.data.flightDetailPolicyVersion);assert.ok(!r.data.dates[20].flights);}));
test('security denial preempts a complete matching detail panel',()=>withPage(p=>{begin(p);p.d.body.insertAdjacentHTML('afterbegin','<p>Access denied</p>');assert.match(p.run('parse_flights').error,/접근 제한/);}));
// The live mobile UI (ICN-LHR, 2026-11-05, observed 2026-11-03) opens a
// separate sibling dialog. Its selected calendar cell changes only after
// '해당 날짜 선택'; the PC-only fixture above cannot reproduce this lifecycle.
function mobile(p,{confirm=true,close=true}={}){
 const content=p.d.querySelector('.bonus-calendar__side-modal-scroll');
 const template=content.cloneNode(true);content.remove();
 p.d.querySelectorAll('td.-selected').forEach(e=>e.classList.remove('-selected'));
 let clicks=0,confirms=0;
 for(const day of [21,22])p.d.querySelector('#day_'+day).onclick=()=>{
  clicks++;const host=p.d.createElement('ke-bonus-seat-side-scroll');
  host.innerHTML='<div id="caleandarSideEl" class="bonus-calendar__side -bonus-search-side -active" role="dialog" aria-labelledby="seatScroll"></div>';
  const panel=host.firstElementChild;panel.append(template.cloneNode(true));
  panel.querySelector('#seatScroll').lastChild.textContent='2027년 02월 '+day+'일';
  const button=panel.querySelector('#bonusCalendarSideBottomEL button');
  button.onclick=()=>{confirms++;if(confirm){p.d.querySelectorAll('td.-selected').forEach(e=>e.classList.remove('-selected'));p.d.querySelector('#day_'+day).classList.add('-selected');}if(close)host.remove();};
  p.d.body.append(host);
 };
 return {get clicks(){return clicks;},get confirms(){return confirms;}};
}
function mobileRows(p){assert.ok(p.run('parse_flights').wait);p.advance(250);const r=p.run('parse_flights');assert.equal(r.next,'parse_flights_confirm',JSON.stringify(r));assert.ok(!r.data);}
function mobileDone(p){assert.equal(p.run('parse_flights_confirm').next,'parse_flights_open');const r=p.run('parse_flights_open');assert.ok(r.data,JSON.stringify(r));return r.data;}
test('mobile sibling detail is captured before confirmation and saved only after the date is selected',()=>withPage(p=>{const m=mobile(p);begin(p);assert.equal(p.d.querySelectorAll('td.-selected').length,0);mobileRows(p);const data=mobileDone(p);assert.equal(data.dates[20].flights.length,2);assert.equal(m.clicks,1);assert.equal(m.confirms,1);}));
test('mobile two-day collection confirms and closes each dialog before opening the next',()=>withPage(p=>{const m=mobile(p);begin(p);mobileRows(p);assert.equal(p.run('parse_flights_confirm').next,'parse_flights_open');assert.equal(p.run('parse_flights_open').next,'parse_flights');mobileRows(p);const data=mobileDone(p);assert.equal(data.dates[20].flightDetailDate,'2027-02-21');assert.equal(data.dates[21].flightDetailDate,'2027-02-22');assert.equal(m.clicks,2);assert.equal(m.confirms,2);},[21,22]));
for(const [name,change] of [
 ['wrong mobile date',p=>p.d.querySelector('#seatScroll').lastChild.textContent='2027년 02월 20일'],
 ['duplicate mobile dialog',p=>p.d.body.append(p.d.querySelector('ke-bonus-seat-side-scroll').cloneNode(true))],
 ['wrong mobile dialog label',p=>p.d.querySelector('#caleandarSideEl').setAttribute('aria-labelledby','unrelated')],
 ['hidden mobile dialog',p=>p.d.querySelector('#caleandarSideEl').style.display='none'],
 ['missing confirmation',p=>p.d.querySelector('#bonusCalendarSideBottomEL button').remove()],
 ['disabled confirmation',p=>p.d.querySelector('#bonusCalendarSideBottomEL button').disabled=true],
 ['wrong mobile seat aggregate',p=>p.d.querySelector('.bonus-calendar__flight-class .icon.-class-p').parentElement.remove()]
])test(name+' cannot save or confirm stale detail',()=>withPage(p=>{const m=mobile(p);begin(p);change(p);unresolved(p);assert.equal(m.confirms,0);}));
test('mobile confirmation without a selected calendar date never advances or saves',()=>withPage(p=>{const m=mobile(p,{confirm:false});begin(p);mobileRows(p);for(let i=0;i<3;i++){const r=p.run('parse_flights_confirm');assert.ok(r.wait&&!r.data&&!r.next);p.advance(1000);}assert.equal(m.confirms,1);}));
test('mobile selected date with a still-open dialog never advances or repeats confirmation',()=>withPage(p=>{const m=mobile(p,{close:false});begin(p);mobileRows(p);for(let i=0;i<3;i++){const r=p.run('parse_flights_confirm');assert.ok(r.wait&&!r.data&&!r.next);p.advance(1000);}assert.equal(m.confirms,1);}));
test('route change during mobile confirmation cannot save pending rows',()=>withPage(p=>{mobile(p);begin(p);mobileRows(p);p.d.querySelector('#modals-travelCalendar-title').textContent='ICN FRA';assert.ok(p.run('parse_flights_confirm').error);}));
test('a stale mobile dialog already open before our date action cannot authenticate results',()=>withPage(p=>{const m=mobile(p);p.d.querySelector('#day_21').click();assert.equal(p.run().next,'parse_flights_open');const r=p.run('parse_flights_open');assert.ok(r.wait&&!r.next&&!r.data);assert.equal(m.clicks,1);assert.equal(m.confirms,0);}));
console.log(`PASS: ${passed} KE per-flight capture checks (captured desktop rows + observed mobile-dialog lifecycle fixtures; no provider requests)`);

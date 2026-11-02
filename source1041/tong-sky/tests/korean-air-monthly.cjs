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

function verify(p){let clicks=0;p.d.querySelectorAll('td[id^="day_"]').forEach(e=>e.onclick=()=>clicks++);const result=p.run();assert.ok(result.data,JSON.stringify(result));assert.equal(clicks,0);assert.equal(result.data.collectionMethod,'MONTHLY_CALENDAR');assert.equal(result.data.dates.length,28);assert.ok(!result.data.flightDetailPolicyVersion&&!result.data.operationWeeks);for(const day of result.data.dates){assert.ok(!day.flights&&!day.flightDetailStatus&&!day.operationStatus);}return result.data;}
test('production monthly parse returns raw marks immediately without clicking a date',()=>withPage(p=>{const d=verify(p).dates[20];assert.equal(d.economyAward,true);assert.equal(d.prestigeAward,true);}));
test('missing entire flight detail panel cannot block monthly capture',()=>withPage(p=>{p.d.querySelector('.bonus-calendar__side-modal-scroll').remove();verify(p);}));
test('wrong detail date and malformed flight rows are irrelevant to raw monthly marks',()=>withPage(p=>{p.d.querySelector('#seatScroll').textContent='잘못된 날짜';p.d.querySelectorAll('.bonus-calendar__flight-num').forEach(e=>e.remove());verify(p);}));
test('separate mobile dialog requires no confirmation or selected-cell updates',()=>withPage(p=>{const panel=p.d.querySelector('.bonus-calendar__side-modal-scroll');p.d.body.append(panel);panel.setAttribute('role','dialog');panel.id='caleandarSideEl';p.d.querySelector('#day_21').classList.remove('-selected');verify(p);}));
test('whole month remains available for weekday inference regardless of old detail bounds',()=>withPage(p=>{p.query.captureStart='2027-02-21';p.query.captureEnd='2027-02-21';p.d.querySelector('#day_22').setAttribute('aria-disabled','true');const data=verify(p);assert.ok(data.dates[21].prestigeAward);assert.equal(data.dates[21].status,'OBSERVED');},[21,22]));
test('monthly marks match the original shipped 1.0.35 adapter',()=>withPage(p=>{const baseline=p.w.eval(fs.readFileSync(path.join(__dirname,'fixtures/calendar-1.0.35.js'),'utf8'))('parse',p.query);assert.deepEqual(JSON.parse(JSON.stringify(verify(p).dates)),JSON.parse(JSON.stringify(baseline.data.dates)));}));
test('removed detail stages cannot start a capture',()=>withPage(p=>{for(const stage of ['parse_flights_open','parse_flights','parse_flights_confirm'])assert.ok(p.run(stage).error);}));
test('security denial stops monthly collection',()=>withPage(p=>{p.d.body.insertAdjacentHTML('afterbegin','<p>Access denied</p>');assert.match(p.run().error,/접근 제한/);}));
console.log(`Korean Air monthly-only regression: ${passed} passed`);

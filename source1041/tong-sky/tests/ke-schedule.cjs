'use strict';
const assert=require('assert'),fs=require('fs'),path=require('path'),{JSDOM}=require('jsdom');
const adapter=fs.readFileSync(path.join(__dirname,'fixtures/ke-schedule-legacy.js'),'utf8');
const q={origin:'ICN',destination:'FRA',date:'2027-01-01'};
// Constructed contract fixture from the independently observed public UI structure.
// This is not a saved official schedule or evidence for dates outside the UI visit.
function fixture({date=q.date,codes=['ICN','FRA'],numbers=[28,29,30,31,1,2,3],months='2026년 12월 ~ 2027년 01월',rows=[{number:'KE945',carrier:'ke',operator:'대한항공 운항',states:[false,true,true,false,true,false,true]}]}={}){
 const title=`<h2 class="_hidden"><span class="_hidden">가는 날</span><span>${date.slice(0,4)}년 ${date.slice(5,7)}월 ${date.slice(8)}일 (금)</span><span> 출발지 </span><span>${codes[0]}</span><span> 서울/인천 도착지 </span><span>${codes[1]}</span><span> 프랑크푸르트</span></h2>`;
 const header=`<thead><tr><th>${months}</th>${numbers.map(n=>`<th><span class="flight-schedule__num">${String(n).padStart(2,'0')}</span><span class="_hidden">${months}</span></th>`).join('')}</tr></thead>`;
 const body=`<tbody>${rows.map(r=>`<tr><th><span class="flight-schedule__tb-time"><span class="_hidden">출발 시간 </span>${r.departureTime||'11:25'} ~ <span class="_hidden">도착 시간 </span>${r.arrivalTime||'17:25'}</span> <ke-flight-numbers><span class="code-share -${r.carrier}"></span><span class="_hidden">${r.operator}</span> ${r.number}</ke-flight-numbers></th>${r.states.map((on,i)=>`<td data-index="${i+1}">${on?'<span accessibility-text="운항 함"></span>':'<span class="_hidden">운항 안함</span>'}</td>`).join('')}</tr>`).join('')}</tbody>`;
 return `<div id="scheduleResultList">${title}<table class="flight-schedule__table">${header}${body}</table></div>`;
}
function page(html=fixture(),url='https://www.koreanair.com/flight-status?departure=ICN&arrival=CDG&departureDate=20261102&arrivalDate=20261102&isSchedule=T'){
 const dom=new JSDOM(html,{url,runScripts:'outside-only'}),w=dom.window,d=w.document;
 Object.defineProperty(w.HTMLElement.prototype,'innerText',{get(){return this.textContent;},configurable:true});
 w.HTMLElement.prototype.getBoundingClientRect=function(){return {left:0,top:0,right:100,bottom:40,width:100,height:this.dataset.zero==='true'?0:40};};
 w.HTMLElement.prototype.getClientRects=function(){for(let e=this;e;e=e.parentElement)if(w.getComputedStyle(e).display==='none')return [];return [this.getBoundingClientRect()];};
 const run=w.eval(adapter);return {w,d,run:(query=q)=>run(query),close:()=>w.close()};
}
let passed=0;
function test(name,fn){try{fn();passed++;}catch(e){e.message=name+': '+e.message;throw e;}}
function withPage(fn,html,url){const p=page(html,url);try{fn(p);}finally{p.close();}}
function cannotProve(p,query=q){const r=p.run(query);assert.ok(!r.data,JSON.stringify(r));assert.ok(r.error||r.wait,JSON.stringify(r));}
test('cross-year seven-day row preserves exact explicit schedule and ignores stale URL conditions',()=>withPage(p=>{
 const r=p.run();assert.ok(r.data,JSON.stringify(r));assert.equal(r.data.schema,2);assert.equal(r.data.completeOperators,true);assert.equal(r.data.partnerFlightRows,0);assert.equal(r.data.weekStart,'2026-12-28');assert.equal(r.data.queriedDate,q.date);
 assert.deepEqual(Array.from(r.data.dates,x=>x.date),['2026-12-28','2026-12-29','2026-12-30','2026-12-31','2027-01-01','2027-01-02','2027-01-03']);
 assert.deepEqual(Array.from(r.data.dates,x=>x.status),['NO_FLIGHT','OPERATING','OPERATING','NO_FLIGHT','OPERATING','NO_FLIGHT','OPERATING']);
 assert.equal(r.data.dates[4].flights[0].number,'KE945');assert.equal(r.data.dates[0].flights.length,1);assert.equal(r.data.dates[0].flights[0].status,'NO_FLIGHT');assert.equal(r.data.dates[4].flights[0].operatorCode,'KE');assert.equal(r.data.dates[4].flights[0].operatorName,'대한항공');assert.equal(r.data.dates[4].flights[0].serviceKind,'DIRECT');assert.equal(r.data.dates[4].flights[0].departureTime,'11:25');assert.equal(r.data.dates[4].flights[0].arrivalTime,'17:25');
 assert.ok(Number.isFinite(Date.parse(r.data.observedAt)));assert.ok(r.data.sourceUrl.includes('arrival=CDG'));
}));
test('following weekly heading uses its exact Jan8 anchor and Jan4 Monday',()=>withPage(p=>{const r=p.run({...q,date:'2027-01-08'});assert.ok(r.data,JSON.stringify(r));assert.equal(r.data.weekStart,'2027-01-04');assert.equal(r.data.dates[6].date,'2027-01-10');},fixture({date:'2027-01-08',numbers:[4,5,6,7,8,9,10],months:'2027년 01월'})));
test('normalized captured public DOM preserves the seven independently observed Jan25–31 states',()=>{
 const html=fs.readFileSync(path.join(__dirname,'../../tests/evidence137/ke-schedule-live-20270129.html'),'utf8');
 withPage(p=>{const r=p.run({...q,date:'2027-01-29'});assert.ok(r.data,JSON.stringify(r));assert.equal(r.data.weekStart,'2027-01-25');assert.equal(r.data.ownFlightRows,1);assert.deepEqual(Array.from(r.data.dates,x=>[x.date,x.status]),[['2027-01-25','NO_FLIGHT'],['2027-01-26','NO_FLIGHT'],['2027-01-27','OPERATING'],['2027-01-28','NO_FLIGHT'],['2027-01-29','OPERATING'],['2027-01-30','NO_FLIGHT'],['2027-01-31','OPERATING']]);assert.deepEqual(Array.from(r.data.dates[2].flights,x=>x.number),['KE945']);},html);
});
test('Monday anchor and leap-day crossing resolve actual dates',()=>withPage(p=>{const r=p.run({...q,date:'2028-02-28'});assert.ok(r.data,JSON.stringify(r));assert.equal(r.data.weekStart,'2028-02-28');assert.equal(r.data.dates[1].date,'2028-02-29');assert.equal(r.data.dates[6].date,'2028-03-05');},fixture({date:'2028-02-28',numbers:[28,29,1,2,3,4,5],months:'2028년 02월 ~ 2028년 03월'})));
test('a second own-operated row prevents a false noflight on its operating day',()=>withPage(p=>{const r=p.run();assert.equal(r.data.dates[0].status,'OPERATING');assert.deepEqual(Array.from(r.data.dates[0].flights.filter(x=>x.status==='OPERATING'),x=>x.number),['KE901']);assert.equal(r.data.dates[4].status,'OPERATING');},fixture({rows:[{number:'KE945',carrier:'ke',operator:'대한항공 운항',states:[false,true,true,false,true,false,true]},{number:'KE901',carrier:'ke',operator:'대한항공 운항',states:[true,false,false,false,false,false,false]}]})));
test('an operating partner remains visible while the direct KE flight is off',()=>withPage(p=>{const r=p.run();assert.equal(r.data.dates[0].status,'OPERATING');assert.equal(r.data.ownFlightRows,1);assert.equal(r.data.partnerFlightRows,1);assert.equal(r.data.dates[0].flights[0].status,'NO_FLIGHT');assert.equal(r.data.dates[0].flights[1].status,'OPERATING');assert.equal(r.data.dates[0].flights[1].operatorCode,'AF');assert.equal(r.data.dates[0].flights[1].serviceKind,'CODESHARE');},fixture({rows:[{number:'KE945',carrier:'ke',operator:'대한항공 운항',states:[false,true,true,false,true,false,true]},{number:'AF888',carrier:'af',operator:'에어프랑스 운항',states:[true,true,true,true,true,true,true]}]})));
test('a complete partner-only table can explicitly prove its own off dates',()=>withPage(p=>{const r=p.run();assert.ok(r.data,JSON.stringify(r));assert.equal(r.data.ownFlightRows,0);assert.equal(r.data.partnerFlightRows,1);assert.equal(r.data.completeOperators,true);for(const day of r.data.dates){assert.equal(day.status,'NO_FLIGHT');assert.equal(day.flights.length,1);assert.equal(day.flights[0].status,'NO_FLIGHT');assert.equal(day.flights[0].operatorCode,'AF');}},fixture({rows:[{number:'KE5901',carrier:'af',operator:'에어프랑스 운항',states:[false,false,false,false,false,false,false]}]})));
test('a KE marketing number with another operator cannot prove own-operated service',()=>withPage(cannotProve,fixture({rows:[{number:'KE5901',carrier:'ke',operator:'아시아나항공 운항',states:[false,false,false,false,false,false,false]}]})));
test('empty schedule remains unknown',()=>withPage(cannotProve,fixture({rows:[]})));
for(const [name,mutate] of [
 ['wrong route',p=>p.d.querySelector('h2').textContent='2027년 01월 01일 출발지 ICN 도착지 CDG'],
 ['reversed route',p=>p.d.querySelector('h2').textContent='2027년 01월 01일 출발지 FRA 도착지 ICN'],
 ['stale completed anchor',p=>p.d.querySelector('h2').textContent='2027년 01월 08일 출발지 ICN 도착지 FRA'],
 ['duplicate heading',p=>p.d.querySelector('#scheduleResultList').prepend(p.d.querySelector('h2').cloneNode(true))],
 ['different month range',p=>p.d.querySelector('thead th').textContent='2027년 12월 ~ 2028년 01월'],
 ['adjacent week day mismatch',p=>p.d.querySelectorAll('.flight-schedule__num')[0].textContent='21'],
 ['missing header day',p=>p.d.querySelector('thead th:last-child').remove()],
 ['duplicate day number',p=>p.d.querySelectorAll('.flight-schedule__num')[1].textContent='28'],
 ['missing row day',p=>p.d.querySelector('tbody td:last-child').remove()],
 ['reordered date index',p=>p.d.querySelector('tbody td').setAttribute('data-index','2')],
 ['unknown cell',p=>p.d.querySelector('tbody td').textContent='자료 확인 중'],
 ['contradictory cell',p=>p.d.querySelector('tbody td').insertAdjacentHTML('beforeend','<span accessibility-text="운항 함"></span>')],
 ['duplicate operated symbol',p=>p.d.querySelectorAll('tbody td')[1].insertAdjacentHTML('beforeend','<span accessibility-text="운항 함"></span>')],
 ['missing operator label',p=>p.d.querySelector('ke-flight-numbers ._hidden').remove()],
 ['invalid flight number',p=>p.d.querySelector('ke-flight-numbers').lastChild.textContent=' UNKNOWN'],
 ['contradictory textual state',p=>p.d.querySelector('tbody td').insertAdjacentHTML('beforeend','<span accessibility-text="운항 함">운항 함</span>')],
 ['duplicate own flight row',p=>p.d.querySelector('tbody').append(p.d.querySelector('tbody tr').cloneNode(true))],
 ['missing result',p=>p.d.querySelector('#scheduleResultList').remove()],
 ['busy result',p=>p.d.querySelector('#scheduleResultList').setAttribute('aria-busy','true')],
 ['hidden whole result',p=>p.d.querySelector('#scheduleResultList').style.display='none'],
 ['transparent table',p=>p.d.querySelector('table').style.opacity='0'],
 ['zero-height table',p=>p.d.querySelector('table').dataset.zero='true'],
 ['duplicate visible table',p=>p.d.querySelector('#scheduleResultList').append(p.d.querySelector('table').cloneNode(true))],
 ['denial page',p=>p.d.body.insertAdjacentHTML('afterbegin','<p>Access denied</p>')]
])test(name+' cannot produce schedule evidence',()=>withPage(p=>{mutate(p);cannotProve(p);}));
test('hidden old table does not invalidate the sole rendered completed result',()=>withPage(p=>{const old=p.d.querySelector('table').cloneNode(true);old.style.display='none';p.d.querySelector('#scheduleResultList').append(old);assert.ok(p.run().data);}));
test('a hidden own-flight row cannot establish operating or noflight dates',()=>withPage(p=>{p.d.querySelector('tbody tr').style.display='none';cannotProve(p);}));
test('hidden stale own rows cannot override the explicit rendered own-flight row',()=>withPage(p=>{const stale=p.d.querySelector('tbody tr').cloneNode(true);stale.style.display='none';for(const td of stale.querySelectorAll('td'))td.innerHTML='<span accessibility-text="운항 함"></span>';p.d.querySelector('tbody').append(stale);const r=p.run();assert.ok(r.data,JSON.stringify(r));assert.equal(r.data.ownFlightRows,1);assert.equal(r.data.dates[0].status,'NO_FLIGHT');}));
test('a rendered partner row beside a hidden own row retains only that partner service',()=>withPage(p=>{p.d.querySelector('tbody tr').style.display='none';const partner=p.d.querySelector('tbody tr').cloneNode(true);partner.style.display='';partner.querySelector('.code-share').className='code-share -af';partner.querySelector('ke-flight-numbers ._hidden').textContent='에어프랑스 운항';p.d.querySelector('tbody').append(partner);const r=p.run();assert.ok(r.data,JSON.stringify(r));assert.equal(r.data.ownFlightRows,0);assert.equal(r.data.partnerFlightRows,1);assert.equal(r.data.dates[0].flights.length,1);assert.equal(r.data.dates[0].flights[0].operatorCode,'AF');}));
test('visually hidden accessibility heading and off labels are still authoritative text',()=>withPage(p=>{for(const e of p.d.querySelectorAll('._hidden'))e.style.display='none';assert.ok(p.run().data);}));
test('foreign origin cannot produce official evidence',()=>withPage(cannotProve,fixture(),'https://example.com/flight-status'));
test('an unrelated official page cannot produce schedule evidence',()=>withPage(cannotProve,fixture(),'https://www.koreanair.com/booking'));
test('invalid calendar date and same-airport query are rejected',()=>withPage(p=>{cannotProve(p,{...q,date:'2027-02-30'});cannotProve(p,{...q,destination:'ICN'});}));
test('captured Feb21 all-operator table retains direct off and AirFrance operating separately',()=>{
 const html=fs.readFileSync(path.join(__dirname,'../../tests/evidence138/ke-all-operators-live-20270221.html'),'utf8');
 const facts=JSON.parse(fs.readFileSync(path.join(__dirname,'../../tests/evidence138/all-operators-live-facts-20270221.json'),'utf8'));
 withPage(p=>{const r=p.run({origin:facts.origin,destination:facts.destination,date:facts.queriedDate});assert.ok(r.data,JSON.stringify(r));assert.equal(r.data.schema,2);assert.equal(r.data.completeOperators,true);assert.equal(r.data.weekStart,facts.weekStart);assert.equal(r.data.ownFlightRows,1);assert.equal(r.data.partnerFlightRows,1);
  for(let i=0;i<7;i++){assert.equal(r.data.dates[i].flights.length,facts.rows.length);for(let j=0;j<facts.rows.length;j++){const actual=r.data.dates[i].flights[j],expected=facts.rows[j];for(const field of ['number','operatorCode','operatorName','departureTime','arrivalTime'])assert.equal(actual[field],expected[field]);assert.equal(actual.status,expected.states[i]?'OPERATING':'NO_FLIGHT');assert.equal(actual.serviceKind,expected.operatorCode==='KE'?'DIRECT':'CODESHARE');}}
  const sun=r.data.dates[6];assert.equal(sun.date,'2027-02-21');assert.equal(sun.status,'OPERATING');assert.equal(sun.flights[0].number,'KE901');assert.equal(sun.flights[0].status,'NO_FLIGHT');assert.equal(sun.flights[1].number,'KE5901');assert.equal(sun.flights[1].status,'OPERATING');assert.equal(sun.flights[1].serviceKind,'CODESHARE');
 },html,facts.source);
});
test('all three observed actual operators are preserved with their marketed KE numbers',()=>withPage(p=>{const r=p.run();assert.ok(r.data,JSON.stringify(r));assert.equal(r.data.ownFlightRows,1);assert.equal(r.data.partnerFlightRows,2);assert.deepEqual(Array.from(r.data.dates[0].flights,f=>[f.number,f.operatorCode,f.operatorName,f.serviceKind]),[['KE901','KE','대한항공','DIRECT'],['KE5903','OZ','아시아나항공','CODESHARE'],['KE5901','AF','에어프랑스','CODESHARE']]);},fixture({rows:[{number:'KE901',carrier:'ke',operator:'대한항공 운항',states:[true,true,true,true,true,true,false]},{number:'KE5903',carrier:'oz',operator:'아시아나항공 운항',departureTime:'10:30',states:[true,true,true,true,true,true,true]},{number:'KE5901',carrier:'af',operator:'에어프랑스 운항',departureTime:'13:20',states:[true,true,true,true,true,true,true]}]})));
test('a KE marketing prefix never replaces the explicit AF actual operator',()=>withPage(p=>{const r=p.run();assert.ok(r.data,JSON.stringify(r));for(const day of r.data.dates){assert.equal(day.flights[0].number,'KE5901');assert.equal(day.flights[0].operatorCode,'AF');assert.equal(day.flights[0].serviceKind,'CODESHARE');}assert.equal(r.data.ownFlightRows,0);},fixture({rows:[{number:'KE5901',carrier:'af',operator:'에어프랑스 운항',states:[true,true,true,true,true,true,true]}]})));
test('an explicitly named further partner is preserved without inventing an operator lookup',()=>withPage(p=>{const r=p.run();assert.ok(r.data,JSON.stringify(r));assert.equal(r.data.dates[0].flights[0].operatorCode,'DL');assert.equal(r.data.dates[0].flights[0].operatorName,'델타항공');assert.equal(r.data.dates[0].flights[0].serviceKind,'CODESHARE');},fixture({rows:[{number:'KE5037',carrier:'dl',operator:'델타항공 운항',states:[true,true,true,true,true,true,true]}]})));
test('same marketed flight at different departure times stays as two distinct services',()=>withPage(p=>{const r=p.run();assert.ok(r.data,JSON.stringify(r));assert.equal(r.data.ownFlightRows,2);assert.deepEqual(Array.from(r.data.dates[0].flights,f=>[f.number,f.departureTime,f.status]),[['KE945','11:25','NO_FLIGHT'],['KE945','12:25','OPERATING']]);},fixture({rows:[{number:'KE945',carrier:'ke',operator:'대한항공 운항',states:[false,true,true,false,true,false,true]},{number:'KE945',carrier:'ke',operator:'대한항공 운항',departureTime:'12:25',states:[true,false,false,true,false,true,false]}]})));
test('same number and departure with distinct actual operators is retained for native ambiguity checking',()=>withPage(p=>{const r=p.run();assert.ok(r.data,JSON.stringify(r));assert.equal(r.data.dates[0].flights.length,2);assert.deepEqual(Array.from(r.data.dates[0].flights,f=>f.operatorCode),['KE','AF']);},fixture({rows:[{number:'KE945',carrier:'ke',operator:'대한항공 운항',states:[true,true,true,true,true,true,true]},{number:'KE945',carrier:'af',operator:'에어프랑스 운항',states:[false,false,false,false,false,false,false]}]})));
test('arrival-time variants are retained as distinct complete schedule identities',()=>withPage(p=>{const r=p.run();assert.ok(r.data,JSON.stringify(r));assert.equal(r.data.dates[0].flights.length,2);assert.deepEqual(Array.from(r.data.dates[0].flights,f=>f.arrivalTime),['17:25','18:25']);},fixture({rows:[{number:'KE945',carrier:'ke',operator:'대한항공 운항',states:[true,true,true,true,true,true,true]},{number:'KE945',carrier:'ke',operator:'대한항공 운항',arrivalTime:'18:25',states:[false,false,false,false,false,false,false]}]})));
test('an unambiguous one-digit displayed hour is normalized rather than losing exact time',()=>withPage(p=>{const r=p.run();assert.ok(r.data,JSON.stringify(r));assert.equal(r.data.dates[0].flights[0].departureTime,'07:05');},fixture({rows:[{number:'KE945',carrier:'ke',operator:'대한항공 운항',departureTime:'7:05',states:[true,true,true,true,true,true,true]}]})));
test('an explicit following-day annotation preserves the local arrival clock',()=>withPage(p=>{p.d.querySelector('.flight-schedule__tb-time').append(' 다음날 도착');const r=p.run();assert.ok(r.data,JSON.stringify(r));assert.equal(r.data.dates[0].flights[0].arrivalTime,'17:25');}));
for(const [name,mutate] of [
 ['missing actual-operator icon',p=>p.d.querySelector('.code-share').remove()],
 ['unknown actual-operator class',p=>p.d.querySelector('.code-share').className='code-share -unknown'],
 ['multiple actual-operator classes',p=>p.d.querySelector('.code-share').className='code-share -ke -af'],
 ['missing named actual operator',p=>p.d.querySelector('ke-flight-numbers ._hidden').textContent='운항'],
 ['unidentified named actual operator',p=>p.d.querySelector('ke-flight-numbers ._hidden').textContent='미확인 운항'],
 ['class and actual-operator label mismatch',p=>p.d.querySelector('ke-flight-numbers ._hidden').textContent='에어프랑스 운항'],
 ['known operator under another class',p=>p.d.querySelector('.code-share').className='code-share -dl'],
 ['duplicate actual-operator labels',p=>p.d.querySelector('ke-flight-numbers').append(p.d.querySelector('ke-flight-numbers ._hidden').cloneNode(true))],
 ['duplicate flight-number components',p=>p.d.querySelector('tbody th').append(p.d.querySelector('ke-flight-numbers').cloneNode(true))],
 ['multiple marketed flight numbers',p=>p.d.querySelector('ke-flight-numbers').append(' KE901')],
 ['missing departure and arrival time block',p=>p.d.querySelector('.flight-schedule__tb-time').remove()],
 ['duplicate time blocks',p=>p.d.querySelector('tbody th').append(p.d.querySelector('.flight-schedule__tb-time').cloneNode(true))],
 ['invalid departure hour',p=>p.d.querySelector('.flight-schedule__tb-time').innerHTML='출발 시간 24:05 ~ 도착 시간 17:25'],
 ['invalid arrival minutes',p=>p.d.querySelector('.flight-schedule__tb-time').innerHTML='출발 시간 11:25 ~ 도착 시간 17:65'],
 ['unlabelled time pair',p=>p.d.querySelector('.flight-schedule__tb-time').textContent='11:25 ~ 17:25'],
 ['third ambiguous time',p=>p.d.querySelector('.flight-schedule__tb-time').append(' 18:25')],
 ['hidden marketed flight identity',p=>p.d.querySelector('ke-flight-numbers').style.display='none'],
 ['hidden departure and arrival time block',p=>p.d.querySelector('.flight-schedule__tb-time').style.display='none'],
 ['hidden weekly date cell',p=>p.d.querySelector('tbody td').style.display='none'],
 ['hidden sole operating marker',p=>p.d.querySelector('[accessibility-text="운항 함"]').style.display='none']
])test(name+' rejects complete per-flight proof',()=>withPage(p=>{mutate(p);cannotProve(p);}));
test('one malformed rendered partner cannot be silently skipped to claim completeness',()=>withPage(cannotProve,fixture({rows:[{number:'KE945',carrier:'ke',operator:'대한항공 운항',states:[true,true,true,true,true,true,true]},{number:'KE5901',carrier:'unknown',operator:'에어프랑스 운항',states:[true,true,true,true,true,true,true]}]})));
test('a hidden stale partner is excluded from the rendered row counts and identity set',()=>withPage(p=>{const hidden=p.d.querySelector('tbody tr').cloneNode(true);hidden.style.display='none';hidden.querySelector('.code-share').className='code-share -af';hidden.querySelector('ke-flight-numbers ._hidden').textContent='에어프랑스 운항';p.d.querySelector('tbody').append(hidden);const r=p.run();assert.ok(r.data,JSON.stringify(r));assert.equal(r.data.ownFlightRows,1);assert.equal(r.data.partnerFlightRows,0);assert.equal(r.data.dates[0].flights.length,1);}));
console.log(`PASS: ${passed} Korean Air weekly schedule parser checks (constructed contracts + normalized captured DOM; no provider requests)`);

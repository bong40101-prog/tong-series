'use strict';
const assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path');
const {JSDOM}=require('jsdom');
const source=fs.readFileSync(path.join(__dirname,'../src/main/assets/asiana.js'),'utf8');
const keSource=fs.readFileSync(path.join(__dirname,'../src/main/assets/calendar.js'),'utf8');
let checks=0;const eq=(a,b,label)=>{checks++;assert.deepEqual(a,b,label);},yes=(v,label)=>{checks++;assert.ok(v,label);};
const prior={carrier:'OZ',origin:'ICN',destination:'CDG',month:'2026-11'},next={...prior,month:'2026-12'};
function calendar(month){const [year,m]=month.split('-'),count=new Date(Number(year),Number(m),0).getDate();let cells='';for(let day=1;day<=count;day++){const date=month+'-'+String(day).padStart(2,'0');cells+='<td class="fc-day-top bg_no_seat" data-date="'+date+'"><span class="fc-day-number">'+day+'</span></td>';}return '<div id="depCalendar"><h2>'+year+'년 '+Number(m)+'월</h2><table><tbody><tr>'+cells+'</tr></tbody></table></div>';}
function setup(){
 const html='<div id="source">좌석 상황은 대한민국 시간 (2026.11.1 23:00)</div><input id="tripType" value="OW"><input id="departureAirport1" value="ICN"><input id="arrivalAirport1" value="CDG"><input id="departureDate1" value="20261101"><span id="span_dep_date">26.11</span><button id="sCalendarMonth">탑승월</button><div id="monthMenu" style="display:none"><button class="month_btn" data-year="2026" data-month="12">12월</button></div><button id="btn_MileageSeat_search">조회</button>'+calendar(prior.month);
 const dom=new JSDOM(html,{url:'https://flyasiana.com/I/KR/KO/MileageSeatSearch.do',runScripts:'outside-only'}),w=dom.window,d=w.document;let now=Date.parse('2026-11-02T00:00:00Z'),requests=0,pickerWorks=true,monthWorks=true;
 Object.defineProperty(d,'readyState',{get:()=> 'complete'});Object.defineProperty(w.HTMLElement.prototype,'innerText',{get(){return this.textContent;},configurable:true});
 w.HTMLElement.prototype.getBoundingClientRect=function(){return {width:100,height:40,left:0,right:100,top:0,bottom:40};};w.HTMLElement.prototype.scrollIntoView=function(){};
 w.Date=class Clock extends Date{constructor(...args){super(...(args.length?args:[now]));}static now(){return now;}};
 d.querySelector('#sCalendarMonth').onclick=()=>{if(pickerWorks)d.querySelector('#monthMenu').style.display='block';};
 d.querySelector('.month_btn').onclick=()=>{if(monthWorks){d.querySelector('#departureDate1').value='20261201';d.querySelector('#span_dep_date').textContent='26.12';d.querySelector('#monthMenu').style.display='none';}};
 d.querySelector('#btn_MileageSeat_search').onclick=()=>requests++;
 const adapter=w.eval(source);return{w,d,dom,run:(stage,q=next)=>adapter(stage,q),requests:()=>requests,advance:ms=>now+=ms,disablePicker:()=>pickerWorks=false,disableMonth:()=>monthWorks=false,close:()=>w.close()};
}
function seed(e){const r=e.run('result',prior);yes(r.data,'Canonical full previous calendar parses normally');eq(r.reuseReady,true,'Only validated form plus full result publishes reuseReady');eq(r.data.month,prior.month);return r;}
function submitReuse(e){eq(e.run('reuse').wait,true);eq(e.run('reuse').wait,true);const r=e.run('reuse');eq(r.next,'result');eq(r.submitted,true);eq(e.requests(),1);return r;}
function replace(e,month=next.month){const p=e.d.createElement('div');p.innerHTML=calendar(month);e.d.querySelector('#depCalendar').replaceWith(p.firstChild);}
async function flush(){await Promise.resolve();await new Promise(resolve=>setImmediate(resolve));}
(async()=>{
 {
  const e=setup();try{seed(e);submitReuse(e);eq(e.run('reuse').wait,true);eq(e.requests(),1,'Repeated reuse never resubmits after the actual search');let r=e.run('result');eq(r.wait,true);yes(!r.data,'Old visible calendar is not saved as the next month');e.d.querySelector('#depCalendar h2').textContent='2026년 12월';r=e.run('result');eq(r.wait,true);yes(!r.data,'A changed heading without new date cells is not a fresh calendar');replace(e);r=e.run('result');yes(r.data);eq({month:r.data.month,source:r.data.sourceUpdatedAt},{month:'2026-12',source:'2026-11-01T23:00:00+09:00'},'Reuse retains the actual displayed source clock instead of freshening it');eq(r.data.dates.length,31);yes(r.data.dates.every(day=>day.status==='OBSERVED'&&!day.prestigeAward),'Only explicit no-seat cells can yield observed no markers');eq(r.reuseReady,true);eq(e.requests(),1);}finally{e.close();}
 }
 {
  const e=setup();try{seed(e);submitReuse(e);e.d.querySelector('#depCalendar').innerHTML=calendar(next.month).replace(/^<div id="depCalendar">/,'').replace(/<\/div>$/,'');await flush();const r=e.run('result');yes(r.data,'An in-place clear/rebuild of date cells can prove a new render');eq(r.data.month,next.month);eq(e.requests(),1);}finally{e.close();}
 }
 for(const [label,mutate] of [
  ['missing month opener',e=>e.d.querySelector('#sCalendarMonth').remove()],['prior result rerendered before reuse',e=>replace(e,prior.month)],['route reset',e=>e.d.querySelector('#departureAirport1').value='LAX'],['trip reset',e=>e.d.querySelector('#tripType').value='RT'],['source clock changed',e=>e.d.querySelector('#source').textContent='좌석 상황은 대한민국 시간 (2026.11.2 23:00)'],['missing target month',e=>e.d.querySelector('.month_btn').remove()]
 ]){
  const e=setup();try{seed(e);mutate(e);const r=e.run('reuse');eq(r.cold,true,label);yes(!r.data,label);eq(e.requests(),0,label);eq(e.run('reuse').wait,true,'Only one cold fallback request per query');eq(e.requests(),0);}finally{e.close();}
 }
 {
  const e=setup();try{seed(e);const r=e.run('reuse',{...next,month:'2027-01'});eq(r.cold,true,'Non-consecutive months use the normal cold page');eq(e.requests(),0);}finally{e.close();}
 }
 {
  const e=setup();try{seed(e);e.disablePicker();eq(e.run('reuse').wait,true);eq(e.run('reuse').wait,true);e.advance(2001);eq(e.run('reuse').cold,true,'Unsupported month UI falls back before submitting');eq(e.requests(),0);}finally{e.close();}
 }
 {
  const e=setup();try{seed(e);e.disableMonth();e.run('reuse');e.run('reuse');eq(e.run('reuse').wait,true);e.advance(2001);eq(e.run('reuse').cold,true,'Month values not reflected cause only a pre-query fallback');eq(e.requests(),0);}finally{e.close();}
 }
 {
  const e=setup();try{seed(e);submitReuse(e);e.d.querySelector('#arrivalAirport1').value='LHR';const r=e.run('result');yes(r.error);yes(!r.cold,'Never reload/retry after an actual query');yes(!r.data);eq(e.requests(),1);}finally{e.close();}
 }
 {
  const e=setup();try{seed(e);submitReuse(e);replace(e,'2027-01');const r=e.run('result');yes(r.error);yes(!r.data);yes(!r.cold);eq(e.requests(),1);}finally{e.close();}
 }
 {
  const e=setup();try{seed(e);submitReuse(e);replace(e,prior.month);const r=e.run('result');eq(r.wait,true,'A replacement still containing the old month waits instead of saving or resubmitting');yes(!r.data);eq(e.requests(),1);}finally{e.close();}
 }
 {
  const e=setup();try{seed(e);submitReuse(e);replace(e);e.d.querySelector('#depCalendar td:last-child').remove();const r=e.run('result');eq(r.wait,true);yes(!r.data,'Incomplete calendar does not invent no-seat dates');eq(e.requests(),1);}finally{e.close();}
 }
 {
  const e=setup();try{seed(e);const b=e.d.createElement('div');b.textContent='보안 확인 CAPTCHA';e.d.body.appendChild(b);const r=e.run('reuse');yes(r.error);yes(!r.cold);eq(e.requests(),0,'Reuse cannot bypass access/security checks');}finally{e.close();}
 }
 {
  const e=setup();try{seed(e);submitReuse(e);replace(e);e.d.querySelector('#source').remove();const r=e.run('result');yes(r.error);yes(!r.data,'Current displayed source time is required after new query');eq(e.requests(),1);}finally{e.close();}
 }
 {
  const e=setup();try{const run=e.w.eval(keSource);const r=run('reuse',next);eq(r.cold,true,'Korean Air without a validated prior calendar stays cold');eq(run('reuse',next).wait,true);eq(e.requests(),0);}finally{e.close();}
 }
 console.log('PASS: '+checks+' bounded Asiana page-reuse assertions (offline constructed DOM; no provider requests)');
})().catch(error=>{console.error(error);process.exitCode=1;});

// Shared browser harness: real captured calendars, used by jsdom and Android WebView.
function testKoreanAirReuse(source, baseline, december, january, alertHTML) {
 const prior={origin:'ICN',destination:'LHR',month:'2026-12',testSource:'2026-11-02T23:00:00+09:00'},target={...prior,month:'2027-01'};
 const adapter=eval(source),original=eval(baseline);let assertions=0,cases=0,clicks=0;
 const check=(condition,message)=>{assertions++;if(!condition)throw new Error(message);};
 const same=(a,b,message)=>check(JSON.stringify(a)===JSON.stringify(b),message);
 const $=s=>document.querySelector(s);
 function setup(){
  window.__tongKeSuccess=null;window.__tongKeReuse=null;window.__tongSource=prior.testSource;clicks=0;
  document.body.innerHTML='<p id="clock">좌석 상황은 대한민국 시간(2026년 11월 2일 23:00) 기준</p>'+december;
  $('#travelCalendarNextBtn').onclick=()=>clicks++;
  const r=adapter('parse',prior);check(!!r.data,'December parses');check(r.reuseReady,'Validated visible December is reusable');
  same(r.data.dates,original('parse',prior).data.dates,'Original 1.0.35 December marks preserved');return r;
 }
 function submit(){const r=adapter('reuse',target);check(r.next==='result'&&r.submitted,'Next month submitted');check(clicks===1,'Exactly one next-month click');}
 function replace(){const box=document.createElement('div');box.innerHTML=january;$('#bonusCalendarTableWrapEl').replaceWith(box.querySelector('#bonusCalendarTableWrapEl'));$('#travelCalendarListBtn').textContent='2027년 1월';}
 function test(fn){setup();fn();cases++;}
 test(()=>{submit();check(adapter('result',target).wait,'Old month waits');check(adapter('reuse',target).submitted,'Repeated stage acknowledges submission');check(clicks===1,'Repeated stage cannot click again');$('#travelCalendarListBtn').textContent='2027년 1월';check(adapter('parse',target).wait,'Heading-only update cannot save December as January');const old=$('#day_1');old.replaceWith(old.cloneNode(true));check(adapter('parse',target).wait,'Partial cell replacement cannot save');replace();check(adapter('result',target).next==='filter','Fresh January proceeds to existing filter check');const r=adapter('parse',target);check(r.data&&r.data.month==='2027-01'&&r.data.dates.length===31,'Full January is saved');same(r.data.dates,original('parse',target).data.dates,'Original 1.0.35 January marks preserved');check(r.data.sourceUpdatedAt===prior.testSource,'Source timestamp is not refreshed');check(clicks===1,'One request throughout');});
 for(const mutate of [()=>$('#travelCalendarNextBtn').remove(),()=>$('#travelCalendarNextBtn').setAttribute('aria-disabled','true'),()=>$('#modals-travelCalendar-title').textContent='LHR ICN',()=>$('#clock').textContent='좌석 상황은 대한민국 시간(2026년 11월 3일 23:00) 기준',()=>$('#day_1').remove(),()=>window.__tongKeSuccess=null,()=>$('#travelCalendarPopup').style.display='none'])test(()=>{mutate();check(adapter('reuse',target).cold,'Unverifiable prior page falls back before submission');check(adapter('reuse',target).wait,'Only one cold fallback');check(clicks===0,'No accidental month request');});
 for(const q of [{...target,month:'2027-02'},{...target,origin:'LHR',destination:'ICN'}])test(()=>{check(adapter('reuse',q).cold,'Different route or nonconsecutive month stays cold');check(clicks===0,'No wrong request');});
 test(()=>{submit();replace();$('#modals-travelCalendar-title').textContent='LHR ICN';check(adapter('parse',target).error,'Wrong direction rejected');check(clicks===1,'No retry after submission');});
 test(()=>{submit();replace();$('#clock').textContent='좌석 상황은 대한민국 시간(2026년 11월 3일 23:00) 기준';const r=adapter('parse',target);check(r.error&&!r.data&&!r.cold,'Clock change cannot create a fresh success or retry');});
 test(()=>{submit();replace();$('#day_31').remove();check(adapter('parse',target).error,'Incomplete new month rejected');});
 test(()=>{submit();replace();$('#travelCalendarListBtn').textContent='2026년 12월';check(adapter('parse',target).wait,'Replaced dates with old title wait');});
 test(()=>{document.body.insertAdjacentHTML('beforeend',alertHTML);for(const stage of ['reuse','result','filter','parse']){const r=adapter(stage,target);check(r.code==='UNSUPPORTED_ITINERARY'&&!r.data,'Official unavailable itinerary classified');}$('#keAlertModalTitleless').style.display='none';check(!adapter('parse',prior).code,'Hidden prior alert ignored');});
 test(()=>{document.body.insertAdjacentHTML('beforeend',alertHTML+'<p>Access denied</p>');const r=adapter('reuse',target);check(r.error&&!r.code&&!r.cold,'Security block takes precedence over skip/fallback');check(clicks===0,'Security never submits');});
 return {cases,assertions,width:innerWidth};
}

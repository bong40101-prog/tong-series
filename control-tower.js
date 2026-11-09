(() => {
  const endpoint='https://korea-futures-cloudflare.pages.dev/api/tong-series-stats';
  const cards=[...document.querySelectorAll('[data-site]')];
  const status=document.getElementById('stats-status');
  const dateLabel=document.getElementById('tower-date');
  const dayFormat=new Intl.DateTimeFormat('sv-SE',{timeZone:'Asia/Seoul',year:'numeric',month:'2-digit',day:'2-digit'});
  const timeFormat=new Intl.DateTimeFormat('ko-KR',{timeZone:'Asia/Seoul',hour:'2-digit',minute:'2-digit',hour12:false});
  let busy=false, lastSuccess=0, serverDay='',resetTimer;
  function dateNow(){return dayFormat.format(new Date())}
  function updateDate(){dateLabel.textContent=new Intl.DateTimeFormat('ko-KR',{timeZone:'Asia/Seoul',month:'long',day:'numeric',weekday:'long'}).format(new Date());}
  function unavailable(card){card.dataset.state='unavailable';card.querySelector('.visitor-value').textContent='—';card.querySelector('[data-counter-state]').textContent='집계 연결 지연';}
  async function refresh(){
    updateDate();if(busy)return;busy=true;
    const controller=new AbortController(),timer=setTimeout(()=>controller.abort(),8000);
    try{
      const response=await fetch(endpoint,{credentials:'omit',cache:'no-store',signal:controller.signal});
      if(!response.ok)throw Error('Statistics unavailable');
      const data=await response.json();
      const now=Date.parse(data.serverNow), reset=Date.parse(data.nextResetAt);
      if(!Array.isArray(data.sites)||!/^\d{4}-\d{2}-\d{2}$/.test(data.date)||!Number.isFinite(now)||!Number.isFinite(reset)||reset-now<0||reset-now>86401000)throw Error('Invalid statistics');
      const changedDay=serverDay!==data.date;serverDay=data.date;
      let available=0;
      cards.forEach(card=>{
        const row=data.sites.find(x=>x.id===card.dataset.site);
        if(row?.status==='ok'&&Number.isSafeInteger(row.dailyVisitors)&&row.dailyVisitors>=0){
          card.dataset.state='ok';card.dataset.date=data.date;
          card.querySelector('.visitor-value').textContent=row.dailyVisitors.toLocaleString('ko-KR');
          card.querySelector('[data-counter-state]').textContent='오늘 방문자';available++;
        }else if(!changedDay&&card.dataset.date===data.date){card.dataset.state='stale';card.querySelector('[data-counter-state]').textContent='이전 집계 · 갱신 지연';}
        else unavailable(card);
      });
      lastSuccess=now;status.textContent=timeFormat.format(new Date(now))+' 확인 · 1분마다 갱신'+(available<3?' · 일부 연결 지연':'');
      clearTimeout(resetTimer);resetTimer=setTimeout(refresh,reset-now+1000);
    }catch{
      cards.forEach(card=>{
        if(card.dataset.date===dateNow()){card.dataset.state='stale';card.querySelector('[data-counter-state]').textContent='이전 집계 · 갱신 지연';}
        else unavailable(card);
      });
      status.textContent=lastSuccess&&serverDay===dateNow()?timeFormat.format(new Date(lastSuccess))+' 확인 · 갱신 재시도 중':'방문자 집계 연결 재시도 중';
    }finally{clearTimeout(timer);busy=false;}
  }
  refresh();setInterval(()=>{updateDate();if(!document.hidden)refresh();},60000);
  document.addEventListener('visibilitychange',()=>{if(!document.hidden)refresh();});
})();

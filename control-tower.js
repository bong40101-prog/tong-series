(() => {
  const dateLabel=document.getElementById('tower-date');
  const updateDate=()=>{if(dateLabel)dateLabel.textContent=new Intl.DateTimeFormat('ko-KR',{timeZone:'Asia/Seoul',month:'long',day:'numeric',weekday:'long'}).format(new Date());};
  const endpoint='https://korea-futures-cloudflare.pages.dev/api/tong-series-stats';
  let busy=false;
  async function refreshVisitors(){
    if(busy)return;busy=true;
    const controller=new AbortController(),timer=setTimeout(()=>controller.abort(),8000);
    try{
      const response=await fetch(endpoint,{credentials:'omit',cache:'no-store',signal:controller.signal});
      if(!response.ok)throw Error('Statistics unavailable');
      const data=await response.json();
      const sites=Array.isArray(data.sites)?data.sites:[];
      document.querySelectorAll('[data-site]').forEach(card=>{
        const value=card.querySelector('[data-visitors]');if(!value)return;
        const row=sites.find(x=>x.id===card.dataset.site);
        if(row&&row.status==='ok'&&Number.isSafeInteger(row.dailyVisitors)&&row.dailyVisitors>=0)value.textContent=row.dailyVisitors.toLocaleString('ko-KR')+'명';
      });
    }catch{}finally{clearTimeout(timer);busy=false;}
  }
  updateDate();refreshVisitors();
  setInterval(()=>{if(!document.hidden){updateDate();refreshVisitors();}},60000);
  document.addEventListener('visibilitychange',()=>{if(!document.hidden){updateDate();refreshVisitors();}});
})();

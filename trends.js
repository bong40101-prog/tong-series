(() => {
  const list=document.getElementById('trends-list'),status=document.getElementById('trends-status');
  const buttons=[...document.querySelectorAll('.trend-tab')],caption=document.getElementById('trend-caption');
  const empty=document.getElementById('trend-empty'),source=document.getElementById('trends-source');
  let groups=new Map(),selected='all',busy=false,failed=false;
  const fmt=new Intl.DateTimeFormat('ko-KR',{timeZone:'Asia/Seoul',month:'numeric',day:'numeric',hour:'2-digit',minute:'2-digit',hour12:false});
  function render(){
    const group=groups.get(selected);
    document.getElementById('trends-panel').setAttribute('aria-labelledby','trend-tab-'+selected);
    buttons.forEach(b=>{const active=b.dataset.category===selected;b.setAttribute('aria-selected',String(active));b.tabIndex=active?0:-1;const g=groups.get(b.dataset.category);b.querySelector('small').textContent=g?.status==='ok'?String(g.items.length):'—';});
    list.replaceChildren();empty.hidden=true;
    if(!group||group.status!=='ok'){
      caption.textContent=selected==='all'?'대한민국 · 최근 24시간':'대한민국 · 최근 7일';
      status.textContent=busy?'최신 검색어를 불러오는 중…':'이 분야의 검색어를 불러오지 못했습니다.';
      empty.textContent='자료 연결이 복구되면 자동으로 다시 불러옵니다.';empty.hidden=busy;
      source.href='https://trends.google.com/trending?geo=KR';return;
    }
    const stamp=Date.parse(group.updatedAt),stale=Date.now()-stamp>3600000;
    const period=group.windowHours===24?'최근 24시간':'최근 7일';
    caption.textContent=group.label+' · 대한민국 · '+period+' · '+group.items.length+'개 표시'+(selected==='camping'?' · 캠핑 관련어 기준':selected==='people'?' · 구글 엔터테인먼트 분류':'');
    status.textContent=fmt.format(new Date(stamp))+' 수집'+(stale||group.refreshFailed?' · 갱신 지연, 이전 자료':'')+(failed?' · 연결 재시도 중':'');
    source.href=group.windowHours===24?'https://trends.google.com/trending?geo=KR&hours=24':'https://trends.google.com/trending?geo=KR&hours=168';
    list.style.setProperty('--trend-rows',Math.max(1,Math.ceil(group.items.length/2)));
    list.setAttribute('aria-label',group.label+' 급상승 검색어');
    const fragment=document.createDocumentFragment();
    group.items.forEach((item,index)=>{
      const li=document.createElement('li'),a=document.createElement('a'),rank=document.createElement('span'),title=document.createElement('span');
      a.href='https://www.google.com/search?q='+encodeURIComponent(item.title);a.target='_blank';a.rel='noopener noreferrer';
      rank.className='trend-rank';rank.textContent=String(index+1).padStart(2,'0');rank.setAttribute('aria-hidden','true');
      title.className='trend-title';title.textContent=item.title;a.append(rank,title);li.append(a);fragment.append(li);
    });list.append(fragment);
    if(!group.items.length){empty.textContent='최근 7일 동안 구글 급상승 목록에서 '+group.label+' 관련 검색어가 확인되지 않았어요. 새로운 검색어가 잡히면 여기에 표시됩니다.';empty.hidden=false;}
  }
  function choose(id,focus=false){selected=id;render();if(focus)buttons.find(b=>b.dataset.category===id)?.focus();}
  buttons.forEach((b,i)=>{
    b.addEventListener('click',()=>choose(b.dataset.category));
    b.addEventListener('keydown',event=>{let n;if(event.key==='ArrowRight')n=(i+1)%buttons.length;else if(event.key==='ArrowLeft')n=(i+buttons.length-1)%buttons.length;else if(event.key==='Home')n=0;else if(event.key==='End')n=buttons.length-1;else return;event.preventDefault();choose(buttons[n].dataset.category,true);});
  });
  async function refresh(){
    if(busy)return;busy=true;
    const controller=new AbortController(),timer=setTimeout(()=>controller.abort(),15000);
    try{
      const response=await fetch('./trends.json?t='+Math.floor(Date.now()/60000),{cache:'no-store',signal:controller.signal});
      if(!response.ok)throw Error('Feed unavailable');const data=await response.json();
      if(data.schema!==2||!Array.isArray(data.groups))throw Error('Invalid feed');
      let valid=0;
      data.groups.forEach(g=>{
        if(!buttons.some(b=>b.dataset.category===g.id)||g.status!=='ok'||!Number.isFinite(Date.parse(g.updatedAt))||![24,168].includes(g.windowHours)||!Array.isArray(g.items))return;
        const items=g.items.filter(x=>x&&typeof x.title==='string'&&x.title.trim()).slice(0,20);
        if(items.length!==g.items.length)return;
        const prior=groups.get(g.id);if(!prior||Date.parse(g.updatedAt)>=Date.parse(prior.updatedAt))groups.set(g.id,{...g,items});valid++;
      });if(!valid)throw Error('No valid categories');failed=false;
    }catch{failed=true;}finally{clearTimeout(timer);busy=false;render();}
  }
  render();refresh();setInterval(()=>{if(!document.hidden)refresh();},60000);
  document.addEventListener('visibilitychange',()=>{if(!document.hidden)refresh();});
})();

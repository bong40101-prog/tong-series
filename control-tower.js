(() => {
  const dateLabel=document.getElementById('tower-date');
  if(!dateLabel)return;
  const updateDate=()=>{dateLabel.textContent=new Intl.DateTimeFormat('ko-KR',{timeZone:'Asia/Seoul',month:'long',day:'numeric',weekday:'long'}).format(new Date());};
  updateDate();
  setInterval(()=>{if(!document.hidden)updateDate();},60000);
  document.addEventListener('visibilitychange',()=>{if(!document.hidden)updateDate();});
})();

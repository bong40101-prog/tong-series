(() => {
  const list = document.getElementById('trends-list');
  const status = document.getElementById('trends-status');
  let lastUpdated = 0;
  let busy = false;
  function showTime() {
    if (!lastUpdated) return;
    const stale = Date.now() - lastUpdated > 60 * 60 * 1000;
    status.textContent = '마지막 수집: ' + new Intl.DateTimeFormat('ko-KR', {
      timeZone: 'Asia/Seoul', month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit', hour12: false
    }).format(new Date(lastUpdated)) + ' (한국시간)' + (stale ? ' · 갱신 지연, 이전 자료 표시 중' : ' · 자동 갱신');
  }
  async function refresh() {
    if (busy) return;
    busy = true;
    try {
      const response = await fetch('./trends.json?t=' + Math.floor(Date.now() / 60000), {cache: 'no-store', signal: AbortSignal.timeout(15000)});
      if (!response.ok) throw new Error('HTTP ' + response.status);
      const data = await response.json();
      const updated = Date.parse(data.updatedAt);
      const entries = (Array.isArray(data.items) ? data.items : []).filter(x => x && typeof x.title === 'string' && x.title.trim()).slice(0, 20);
      if (!entries.length || !Number.isFinite(updated)) throw new Error('Invalid trends');
      const fragment = document.createDocumentFragment();
      entries.forEach((item, index) => {
        const li = document.createElement('li');
        const link = document.createElement('a');
        link.href = 'https://www.google.com/search?q=' + encodeURIComponent(item.title);
        link.target = '_blank'; link.rel = 'noopener noreferrer';
        const rank = document.createElement('span'); rank.className = 'trend-rank'; rank.textContent = index + 1;
        rank.setAttribute('aria-hidden', 'true');
        const title = document.createElement('span'); title.className = 'trend-title'; title.textContent = item.title;
        link.append(rank, title); li.append(link); fragment.append(li);
      });
      list.replaceChildren(fragment);
      lastUpdated = updated; showTime();
    } catch (error) {
      if (lastUpdated) { showTime(); status.textContent += ' · 최신 자료 연결 재시도 중'; }
      else status.textContent = '검색어를 불러오지 못했습니다. 아래 구글 트렌드에서 확인할 수 있어요.';
    } finally { busy = false; }
  }
  refresh();
  setInterval(() => { showTime(); if (!document.hidden) refresh(); }, 60000);
  document.addEventListener('visibilitychange', () => { if (!document.hidden) refresh(); });
})();

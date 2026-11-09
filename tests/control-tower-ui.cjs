const {chromium}=require('playwright'),assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path'),{spawn}=require('node:child_process');
const root=path.resolve(__dirname,'..'),feed=JSON.parse(fs.readFileSync(path.join(root,'trends.json'))),evidence=path.join(root,'test-evidence');fs.mkdirSync(evidence,{recursive:true});
const server=spawn('python3',['-m','http.server','8156','--bind','127.0.0.1','--directory',root],{stdio:'ignore'});
function stats(){const now=new Date(),date=new Date(+now+32400000).toISOString().slice(0,10);return {date,serverNow:now.toISOString(),nextResetAt:new Date(Date.parse(date+'T00:00:00+09:00')+86400000).toISOString(),sites:[{id:'sky',status:'ok',dailyVisitors:53},{id:'radar',status:'ok',dailyVisitors:127},{id:'food',status:'ok',dailyVisitors:0}]};}
(async()=>{let browser;try{
 for(let i=0;i<30;i++){try{if((await fetch('http://127.0.0.1:8156/')).ok)break;}catch{}await new Promise(r=>setTimeout(r,100));}
 browser=await chromium.launch({headless:true});const results=[];
 for(const [width,height] of [[1920,1080],[1440,1000],[1280,900],[820,1000],[620,900],[420,850],[390,844],[320,740]]){
  const page=await browser.newPage({viewport:{width,height},locale:'ko-KR'}),errors=[];page.on('pageerror',e=>errors.push(e.message));
  await page.route('https://korea-futures-cloudflare.pages.dev/api/tong-series-stats',r=>{assert.equal(r.request().method(),'GET');return r.fulfill({contentType:'application/json',body:JSON.stringify(stats())});});
  await page.goto('http://127.0.0.1:8156/');await page.waitForFunction(()=>document.querySelector('[data-site="sky"] .visitor-value').textContent==='53');
  assert.equal(await page.locator('[data-site="food"] .visitor-value').textContent(),'0');
  assert.equal(await page.locator('[role="tab"]').count(),10);assert.equal(await page.locator('.site-open').count(),3);
  for(const group of feed.groups){
   await page.locator('[data-category="'+group.id+'"]').click();
   assert.equal(await page.locator('#trends-list li').count(),group.items.length);
   assert.equal(await page.locator('#trends-panel').getAttribute('aria-labelledby'),'trend-tab-'+group.id);
   assert.equal(await page.locator('[role="tab"][aria-selected="true"]').count(),1);
   assert((await page.locator('#trend-caption').textContent()).includes(group.windowHours===24?'24시간':'7일'));
  }
  await page.locator('[data-category="camping"]').click();assert.equal(await page.locator('#trend-empty').isVisible(),feed.groups.find(g=>g.id==='camping').items.length===0);
  await page.locator('[data-category="all"]').click();await page.locator('[data-category="all"]').press('ArrowRight');assert.equal(await page.locator('[role="tab"][aria-selected="true"]').getAttribute('data-category'),'food');
  assert.equal(await page.locator('.app').count(),4);assert.equal(await page.locator('#tongsky').count(),0);
  const downloads=await page.locator('.app a[download]').evaluateAll(es=>es.map(e=>e.getAttribute('href')));
  assert.deepEqual(downloads,['TongCatch-0.1.10.apk','TongHome-0.3.16.apk','Tongstagram-1.1.7.apk','TongGlow-1.0.5.apk','TongGlow-1.0.5-install.zip']);
  const geo=await page.evaluate(()=>({width:document.documentElement.scrollWidth,viewport:innerWidth,cards:[...document.querySelectorAll('.site-card')].map(e=>{const r=e.getBoundingClientRect();return{x:r.x,y:r.y,width:r.width,bottom:r.bottom};})}));
  assert(geo.width<=width+1,'overflow at '+width);
  if(width<=620)assert(geo.cards[1].y>=geo.cards[0].bottom,'mobile cards stack');else assert(Math.abs(geo.cards[0].y-geo.cards[1].y)<2,'desktop cards align');
  await page.locator('[data-category="all"]').click();await page.screenshot({path:path.join(evidence,'tower-'+width+'.png'),fullPage:true});
  await page.route('https://korea-futures-cloudflare.pages.dev/api/tong-series-stats',r=>r.fulfill({status:503,body:'unavailable'}));
  await page.route('**/trends.json*',r=>r.fulfill({status:503,body:'unavailable'}));
  await page.reload();await page.waitForFunction(()=>document.getElementById('stats-status').textContent.includes('재시도'));
  assert.deepEqual(await page.locator('.visitor-value').allTextContents(),['—','—','—']);assert.equal(await page.locator('.site-open').count(),3);assert.equal(await page.locator('.app').count(),4);assert.equal(await page.locator('[role="tab"]').count(),10);assert.equal(errors.length,0,errors.join('\n'));
  results.push({width,height,tenCategoryButtons:true,noOverflow:true,readOnlyVisitorFetch:true,downloadsPreserved:true,failuresIsolated:true});await page.close();
 }
 fs.writeFileSync(path.join(evidence,'results.json'),JSON.stringify({passed:true,results},null,2));console.log(JSON.stringify({passed:true,viewports:results.length,categories:10}));
}finally{if(browser)await browser.close();server.kill();}})().catch(e=>{console.error(e);process.exitCode=1});

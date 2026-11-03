const fs=require('fs'),path=require('path'),{JSDOM}=require('jsdom');
const read=p=>fs.readFileSync(path.join(__dirname,p),'utf8');
const dom=new JSDOM('<body></body>',{url:'https://www.koreanair.com/booking/book-and-manage/award-seat-availability',runScripts:'outside-only'}),w=dom.window;
Object.defineProperty(w.HTMLElement.prototype,'innerText',{get(){return this.textContent;},configurable:true});
w.HTMLElement.prototype.scrollIntoView=function(){};
w.HTMLElement.prototype.getBoundingClientRect=function(){return {left:0,top:0,right:100,bottom:40,width:100,height:40};};
w.HTMLElement.prototype.getClientRects=function(){return this.isConnected?[this.getBoundingClientRect()]:[];};
try{w.eval(read('korean-air-reuse-harness.js'));const result=w.testKoreanAirReuse(read('../src/main/assets/calendar.js'),read('fixtures/calendar-1.0.35.js'),read('fixtures/ke-reuse-live-202612.html'),read('fixtures/ke-reuse-live-202701.html'),read('fixtures/ke-unsupported-live.html'));console.log('PASS: Korean Air next-month reuse '+JSON.stringify(result));}finally{w.close();}

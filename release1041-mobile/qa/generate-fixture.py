from pathlib import Path
root=Path(__file__).resolve().parents[1]
rows=[]
for day in range(1,31):
    marks='<ul class="bonus-calendar__icons"><li>일반석 보너스</li><li>프레스티지석 좌석승급</li></ul>' if day in (3,4) else '<span class="ux-class-icon -no-seat -gray">좌석 없음</span>'
    rows.append(f'<td id="day_{day}" role="button"><div class="bonus-calendar__day"><span aria-hidden="true">{day}</span>{marks}</div></td>')
table=''.join('<tr>'+''.join(rows[i:i+7])+'</tr>' for i in range(0,30,7))
html='''<!doctype html><meta name="viewport" content="width=device-width,initial-scale=1"><meta charset="utf-8">
<style>body{margin:0;font:12px sans-serif}table{width:100%;table-layout:fixed}td{height:35px}.bonus-calendar__icons{padding:0;font-size:8px}.bonus-calendar__side{position:fixed;bottom:0;left:0;width:100%;height:300px;background:white}.icon{display:inline-block;width:14px;height:14px}.bonus-calendar__class{padding:0}.bonus-calendar__flight-list{padding:10px}.bonus-calendar__flight-num,.bonus-calendar__flight-time{display:inline-block;padding:8px}button{padding:12px}.bonus-calendar__class-item{display:block}</style>
<div id="travelCalendarPopup" role="dialog" aria-labelledby="modals-travelCalendar-title"><h3 id="modals-travelCalendar-title">ICN LHR</h3><button id="travelCalendarListBtn">2026년 11월</button><div id="bonusCalendarTableWrapEl"><table class="bonus-calendar__table"><tbody>TABLE</tbody></table></div></div>
<script>
window.actions={clicks:0,confirms:0};
for(const day of [3,4])document.getElementById('day_'+day).onclick=()=>{
 actions.clicks++;const host=document.createElement('ke-bonus-seat-side-scroll');
 host.innerHTML='<div id="caleandarSideEl" class="bonus-calendar__side -bonus-search-side -active" role="dialog" aria-labelledby="seatScroll"><div class="bonus-calendar__side-modal-scroll"><h4 id="seatScroll"><span>항공편 별 좌석 상황</span> 2026년 11월 '+String(window.testMode==='bad-date'?9:day).padStart(2,'0')+'일 </h4><div id="bonusSeatSideScrollEl"><ul class="bonus-calendar__flight-list"><li class="bonus-calendar__flight-item"><span class="bonus-calendar__flight-num">KE907</span><span class="bonus-calendar__flight-time">10:40</span><div class="bonus-calendar__flight-class"><ul class="bonus-calendar__class"><li class="bonus-calendar__class-item"><span class="icon -class-e"></span><span>일반석 보너스</span></li><li class="bonus-calendar__class-item"><span class="icon -class-u"></span><span>프레스티지석 좌석승급</span></li></ul></div></li></ul></div><button>해당 날짜 선택</button></div></div>';
 host.querySelector('button').onclick=()=>{actions.confirms++;if(window.testMode!=='no-confirm'){document.querySelectorAll('td.-selected').forEach(e=>e.classList.remove('-selected'));document.getElementById('day_'+day).classList.add('-selected');}host.remove();};document.body.append(host);
};
</script>'''.replace('TABLE',table)
(root/'probe/assets/fixture.html').write_text(html)

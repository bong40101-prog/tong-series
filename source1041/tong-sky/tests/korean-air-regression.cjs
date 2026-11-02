'use strict';
const assert = require('assert');
const fs = require('fs');
const path = require('path');
const {JSDOM} = require('jsdom');

// Exercise the production adapter against the existing captured public calendar.
// Layout is supplied because jsdom does not render; DOM events and selectors are real.
const adapter = fs.readFileSync(path.join(__dirname, '../src/main/assets/calendar.js'), 'utf8');
const calendar = fs.readFileSync(path.join(__dirname, '../src/androidTest/assets/calendar.html'), 'utf8');
const query = {origin: 'ICN', destination: 'CDG', month: '2026-11', testSource: '2026-10-29T07:00:00+09:00'};
let checks = 0;
function test(name, fn) {
  try { fn(); checks++; }
  catch (e) { e.message = name + ': ' + e.message; throw e; }
}
function page(html = calendar) {
  const dom = new JSDOM(html, {url: 'https://www.koreanair.com/booking/book-and-manage/award-seat-availability', runScripts: 'outside-only'});
  const w = dom.window;
  Object.defineProperty(w.HTMLElement.prototype, 'innerText', {get() { return this.textContent; }, configurable: true});
  w.HTMLElement.prototype.scrollIntoView = function() {};
  w.HTMLElement.prototype.getBoundingClientRect = function() {
    return {left: 10, right: 110, top: 10, bottom: 50, width: 100, height: 40};
  };
  w.HTMLElement.prototype.getClientRects = function() {
    for (let e = this; e; e = e.parentElement) if (w.getComputedStyle(e).display === 'none') return [];
    return [this.getBoundingClientRect()];
  };
  const run = w.eval(adapter);
  return {w, d: w.document, run: (stage = 'parse', q = query) => run(stage, q), close: () => w.close()};
}
function withPage(fn, html) {
  const p = page(html);
  try { fn(p); } finally { p.close(); }
}
function badCalendar(name, change, expected) {
  test(name, () => withPage(p => {
    change(p.d, p.w);
    const r = p.run();
    assert.match(r.error || '', expected);
    assert.ok(!r.data);
  }));
}

test('captured 30-day calendar preserves awards, upgrades, and explicit no-seat', () => withPage(p => {
  const r = p.run();
  assert.ok(r.data, JSON.stringify(r));
  assert.equal(r.data.origin, 'ICN');
  assert.equal(r.data.destination, 'CDG');
  assert.equal(r.data.month, '2026-11');
  assert.equal(r.data.sourceUpdatedAt, query.testSource);
  assert.equal(r.data.dates.length, 30);
  assert.deepEqual(Array.from(r.data.dates, x => x.date), Array.from({length: 30}, (_, i) => '2026-11-' + String(i + 1).padStart(2, '0')));
  for (const day of [1, 8, 15, 22]) {
    const x = r.data.dates[day - 1];
    assert.equal(x.status, 'OBSERVED');
    for (const key of ['economyAward', 'premiumAward', 'prestigeAward', 'premiumUpgrade', 'prestigeUpgrade', 'firstCombined']) assert.equal(x[key], false);
  }
  assert.equal(r.data.dates[1].economyAward, true);
  assert.equal(r.data.dates[3].prestigeUpgrade, true);
  assert.equal(r.data.dates[3].prestigeAward, false);
}));
test('all six seat labels map to their distinct fields', () => withPage(p => {
  p.d.querySelector('#day_2 .bonus-calendar__icons').innerHTML = ['일반석 보너스', '프리미엄석 보너스', '프레스티지석 보너스', '프리미엄석 좌석승급', '프레스티지석 좌석승급', '일등석 보너스/좌석승급'].map(x => '<li>' + x + '</li>').join('');
  const x = p.run().data.dates[1];
  for (const key of ['economyAward', 'premiumAward', 'prestigeAward', 'premiumUpgrade', 'prestigeUpgrade', 'firstCombined']) assert.equal(x[key], true);
}));
for (const [name, hide] of [
  ['display-hidden marker', e => e.style.display = 'none'],
  ['visibility-hidden marker', e => e.style.visibility = 'hidden'],
  ['transparent marker', e => e.style.opacity = '0'],
  ['hidden icon container', e => e.parentElement.style.display = 'none'],
  ['hidden date cell', e => e.closest('td').style.display = 'none']
]) test(name + ' cannot become a saved seat observation', () => withPage(p => {
  const marker = p.d.querySelector('#day_2 li');
  marker.textContent = '프레스티지석 보너스'; hide(marker);
  const r = p.run(); assert.ok(r.data, JSON.stringify(r));
  const x = r.data.dates[1]; assert.equal(x.status, 'UNKNOWN');
  for (const field of ['economyAward', 'premiumAward', 'prestigeAward', 'premiumUpgrade', 'prestigeUpgrade', 'firstCombined']) assert.equal(x[field], false);
}));
test('hidden template icons do not hide a distinct visible observation', () => withPage(p => {
  const icons = p.d.querySelector('#day_2 .bonus-calendar__icons');
  icons.insertAdjacentHTML('beforeend', '<li style="display:none">프레스티지석 보너스</li><li style="display:none">알 수 없는 템플릿</li><li>프레스티지석 좌석승급</li>');
  const x = p.run().data.dates[1];
  assert.equal(x.status, 'OBSERVED'); assert.equal(x.economyAward, true);
  assert.equal(x.prestigeUpgrade, true); assert.equal(x.prestigeAward, false);
}));
test('hidden no-seat icon cannot establish an explicit empty observation', () => withPage(p => {
  p.d.querySelector('#day_1 .ux-class-icon').style.display = 'none';
  const x = p.run().data.dates[0]; assert.equal(x.status, 'UNKNOWN');
}));
test('a visible marker remains valid beside a hidden stale no-seat icon', () => withPage(p => {
  const empty = p.d.querySelector('#day_1 .ux-class-icon').cloneNode(true); empty.style.display = 'none';
  p.d.querySelector('#day_2 .bonus-calendar__day').append(empty);
  const x = p.run().data.dates[1]; assert.equal(x.status, 'OBSERVED'); assert.equal(x.economyAward, true);
}));
badCalendar('other route cannot be saved', d => d.querySelector('#modals-travelCalendar-title').textContent = 'ICN LHR', /노선 불일치/);
badCalendar('reversed route cannot be saved', d => d.querySelector('#modals-travelCalendar-title').textContent = 'CDG ICN', /노선 불일치/);
badCalendar('other month cannot be saved', d => d.querySelector('#travelCalendarListBtn').textContent = '2026년 10월', /월 불일치/);
badCalendar('missing date cannot be saved', d => d.querySelector('#day_30').remove(), /한 달 날짜가 완전하지/);
badCalendar('duplicate date cannot be saved', d => d.querySelector('#day_30').id = 'day_29', /날짜 누락·중복/);
badCalendar('out-of-month date cannot be saved', d => d.querySelector('#day_30').id = 'day_31', /날짜 누락·중복/);
badCalendar('date label must match its canonical day', d => d.querySelector('#day_2 .bonus-calendar__day > span').textContent = '3', /날짜 표시 불일치/);
badCalendar('unknown marker cannot be interpreted as no-seat', d => d.querySelector('#day_2 li').textContent = '새 좌석 등급', /알 수 없거나 중복된 좌석 표식/);
badCalendar('duplicate marker cannot be saved', d => d.querySelector('#day_2 ul').append(d.querySelector('#day_2 li').cloneNode(true)), /알 수 없거나 중복된 좌석 표식/);
badCalendar('seat and no-seat conflict cannot be saved', d => d.querySelector('#day_2 .bonus-calendar__day').append(d.querySelector('#day_1 .ux-class-icon').cloneNode(true)), /좌석 없음 표식 불일치/);
badCalendar('duplicate calendar table cannot be saved', d => d.querySelector('#bonusCalendarTableWrapEl').append(d.querySelector('table').cloneNode(true)), /달력 구조 변경/);
badCalendar('unexpected padding cell contents cannot be saved', d => d.querySelector('tbody td[aria-hidden="true"]').textContent = '31', /확인되지 않은 날짜 셀/);
test('an entirely unresolved calendar waits rather than inventing no-seat', () => withPage(p => {
  for (const e of p.d.querySelectorAll('.bonus-calendar__icons,.ux-class-icon.-no-seat')) e.remove();
  const r = p.run();
  assert.equal(r.wait, true);
  assert.ok(!r.data && !r.error);
}));
test('missing source timestamp cannot be saved', () => withPage(p => assert.match(p.run('parse_test', {origin: 'ICN', destination: 'CDG', month: '2026-11'}).error, /기준 시각/)));
test('invalid source timestamp cannot be saved', () => withPage(p => assert.match(p.run('parse_test', {...query, testSource: 'invalid'}).error, /기준 시각/)));
test('production one-way stage captures the public source timestamp', () => withPage(p => {
  p.d.body.insertAdjacentHTML('afterbegin', '<p>좌석 상황은 대한민국 시간(2026년 10월 29일 07:00) 기준</p><input id="bonusTripType_OW" type="radio" checked>');
  assert.equal(p.run('oneway').next, 'origin_open');
  assert.equal(p.run('parse_test', {origin: 'ICN', destination: 'CDG', month: '2026-11'}).data.sourceUpdatedAt, query.testSource);
}));
test('security check preempts search interaction', () => withPage(p => {
  let clicks = 0;
  p.d.querySelector('button').onclick = () => clicks++;
  assert.match(p.run('search').error, /보안 확인 또는 접근 제한/);
  assert.equal(clicks, 0);
}, '<p>Access denied</p><button>조회</button>'));

const suggestion = '<input aria-label="출발지 검색" value="IC"><div role="option">ICN 서울/인천</div>';
test('stale airport input must not tap a matching old suggestion', () => withPage(p => {
  p.d.elementFromPoint = () => p.d.querySelector('[role="option"]');
  assert.equal(p.run('origin_pick').wait, true);
}, suggestion));
test('exact airport input produces native tap coordinates without DOM click', () => withPage(p => {
  const option = p.d.querySelector('[role="option"]');
  let clicks = 0;
  option.onclick = () => clicks++;
  p.d.querySelector('input').value = 'ICN';
  p.d.elementFromPoint = () => option;
  const r = p.run('origin_pick');
  assert.equal(r.next, 'origin_selected');
  assert.equal(r.tap.x, 60);
  assert.equal(r.tap.y, 30);
  assert.equal(clicks, 0);
}, suggestion));
test('an overlay blocking the suggestion prevents a native tap', () => withPage(p => {
  p.d.querySelector('input').value = 'ICN';
  p.d.elementFromPoint = () => p.d.body;
  assert.equal(p.run('origin_pick').wait, true);
}, suggestion));

test('valid result opens the filter after verifying route and month', () => withPage(p => {
  let clicks = 0;
  p.d.querySelector('[role="dialog"]').insertAdjacentHTML('beforeend', '<button id="bonusFilterBtn">필터</button>');
  p.d.querySelector('#bonusFilterBtn').onclick = () => clicks++;
  assert.equal(p.run('result').next, 'filter');
  assert.equal(clicks, 1);
}));
test('result waits for actual day cells', () => withPage(p => {
  p.d.querySelector('tbody').remove();
  assert.equal(p.run('result').wait, true);
}));
test('result rejects mismatched route before opening filter', () => withPage(p => {
  p.d.querySelector('#modals-travelCalendar-title').textContent = 'ICN NRT';
  assert.match(p.run('result').error, /노선이 요청한 노선과 다릅니다/);
}));
test('result rejects mismatched month before opening filter', () => withPage(p => {
  p.d.querySelector('#travelCalendarListBtn').textContent = '2026년 12월';
  assert.match(p.run('result').error, /연도·월이 다릅니다/);
}));

const filterLabels = ['일반석 보너스', '프리미엄석 보너스', '프리미엄석 좌석승급', '프레스티지석 보너스', '프레스티지석 좌석승급', '일등석 보너스/좌석승급'];
const filter = '<div role="dialog" aria-label="보너스 좌석 필터">' + filterLabels.map((x, i) => '<input type="checkbox" id="f' + i + '" checked><label for="f' + i + '">' + x + '</label>').join('') + '<button>선택 완료</button></div>';
test('all six verified filters allow parsing', () => withPage(p => {
  let clicks = 0;
  p.d.querySelector('button').onclick = () => clicks++;
  assert.equal(p.run('filter').next, 'parse');
  assert.equal(clicks, 1);
}, filter));
test('a hidden seat class stops parsing', () => withPage(p => {
  p.d.querySelector('input').checked = false;
  assert.match(p.run('filter').error, /일부 숨겨져/);
}, filter));
test('a changed filter label stops parsing', () => withPage(p => {
  p.d.querySelector('label').textContent = '변경된 등급';
  assert.match(p.run('filter').error, /일부 숨겨져/);
}, filter));
test('an incomplete filter structure stops parsing', () => withPage(p => {
  p.d.querySelector('input').remove();
  assert.match(p.run('filter').error, /필터 구조가 달라/);
}, filter));
test('waiting for result never re-submits the search', () => withPage(p => {
  let clicks = 0;
  Array.from(p.d.querySelectorAll('button')).find(e => e.textContent === '조회').onclick = () => clicks++;
  assert.equal(p.run('search').next, 'result');
  for (let i = 0; i < 5; i++) assert.equal(p.run('result').wait, true);
  assert.equal(clicks, 1);
}, '<input id="bonusTripType_OW" type="radio" checked><button id="departureBtn1">출발지 ICN</button><button id="destinationBtn1">도착지 CDG</button><button>조회</button>'));

console.log(`PASS: ${checks} Korean Air production-adapter checks (captured calendar, integrity, native selection, filter, security, no resubmit)`);

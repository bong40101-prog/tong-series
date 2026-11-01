/* TongSKY foreground AF search driver. Ordinary website navigation and visible form
 * clicks only. No provider API calls, credentials, profile values or cookie reads.
 * URL grammar is an interoperability candidate from public clients, not a claim of
 * authenticated live compatibility. Native must bind and validate each response.
 */
(function () {
  'use strict';
  if (window.top !== window) return;
  if (window.__tongAFAuto && window.__tongAFAuto.version === 1) return;
  var HOST = /^(?:wwws\.|www\.)?airfrance\.(?:co\.kr|fr|com)$/;
  var AUTH_HOST = /^(?:identity|login)\.airfranceklm\.com$|^(?:identity|login)\.airfrance\.com$/;
  var AUTH_PATH = /(?:^|\/)(?:auth|oauth|login|login-result)(?:\/|$)/i;
  var FORBIDDEN = /(?:^|\/)(?:account|profile|checkout|payment|shopping-cart|booking-confirmation)(?:\/|$)/i;
  // Native owns login UI. Do not even install a driver namespace on those pages.
  try {
    var installation = new URL(location.href);
    if (installation.protocol !== 'https:' || !HOST.test(installation.hostname) || installation.username || installation.password ||
      installation.port && installation.port !== '443' || AUTH_PATH.test(installation.pathname) || FORBIDDEN.test(installation.pathname) ||
      !/^\/(?:[a-z]{2}\/?)?$|^\/(?:[a-z]{2}\/)?search(?:\/|$)/i.test(installation.pathname)) return;
  } catch (_) { return; }
  var CABINS = { ECONOMY: /^(?:economy|économique|이코노미|일반석)$/i,
    PREMIUM: /^(?:premium(?: economy)?|프리미엄(?: 이코노미)?)$/i,
    BUSINESS: /^(?:business|affaires|비즈니스|프레스티지)$/i,
    FIRST: /^(?:first|la première|퍼스트|일등석)$/i };
  var current = null, stopped = Object.create(null), submitted = Object.create(null), order = [], foreground = true;
  var MAX_PHASE_MS = 45000;
  var MAX_RESULT_MS = 20000, MAX_RESULT_STEPS = 20, MIN_RESULT_MS = 1500, RESULT_QUIET_MS = 1000;
  function invalid(code) { throw new Error(code || 'invalid_task'); }
  function safeId(v) { if (typeof v !== 'string' || !/^[A-Za-z0-9_.:-]{1,120}$/.test(v)) invalid('invalid_task_id'); return v; }
  function iso(v) {
    if (typeof v !== 'string' || !/^\d{4}-\d{2}-\d{2}$/.test(v)) invalid('invalid_date');
    var n = Date.parse(v + 'T00:00:00Z');
    if (!Number.isFinite(n) || new Date(n).toISOString().slice(0, 10) !== v) invalid('invalid_date');
    return v;
  }
  function station(v, fallbackType) {
    var code = typeof v === 'string' ? v : v && v.code;
    var type = typeof v === 'string' ? fallbackType || 'AIRPORT' : v && v.type || fallbackType || 'AIRPORT';
    if (typeof code !== 'string' || !/^[A-Z]{3}$/.test(code) || !/^(?:CITY|AIRPORT)$/.test(type)) invalid('invalid_station');
    return { code: code, type: type };
  }
  function task(value) {
    if (!value || typeof value !== 'object') invalid();
    var t = { id: safeId(value.id || value.taskKey), origin: station(value.origin, value.originType),
      destination: station(value.destination, value.destinationType), date: iso(value.date || value.departureDate),
      cabin: value.cabin, trip: value.trip || (value.returnDate ? 'ROUND_TRIP' : 'ONE_WAY'), returnDate: value.returnDate == null ? null : iso(value.returnDate), adults: value.adults == null ? 1 : value.adults };
    if (t.origin.code === t.destination.code || !CABINS[t.cabin] || !/^(?:ONE_WAY|ROUND_TRIP)$/.test(t.trip) || t.adults !== 1) invalid('invalid_search_scope');
    if (t.trip === 'ONE_WAY' && t.returnDate !== null || t.returnDate !== null && t.returnDate < t.date) invalid('invalid_return_date');
    if (value.children != null && value.children !== 0 || value.infants != null && value.infants !== 0) invalid('invalid_passengers');
    return t;
  }
  function origin(value) {
    var u; try { u = new URL(value || location.origin); } catch (_) { invalid('unsupported_origin'); }
    if (u.protocol !== 'https:' || !HOST.test(u.hostname) || u.username || u.password || u.port && u.port !== '443') invalid('unsupported_origin');
    return u.origin;
  }
  function connections(t) {
    var a = t.origin.code + ':' + (t.origin.type === 'CITY' ? 'C' : 'A');
    var b = t.destination.code + ':' + (t.destination.type === 'CITY' ? 'C' : 'A');
    var out = a + ':' + t.date.replace(/-/g, '') + '>' + b;
    if (t.trip === 'ROUND_TRIP') out += '-' + b + (t.returnDate ? ':' + t.returnDate.replace(/-/g, '') : '') + '>' + a;
    return out;
  }
  function buildNavigation(value, base) {
    var t = task(value), u = new URL('/search/offers', origin(base));
    u.searchParams.set('pax', '1:0:0:0:0:0:0:0'); u.searchParams.set('cabinClass', t.cabin);
    u.searchParams.set('activeConnection', '0'); u.searchParams.set('connections', connections(t)); u.searchParams.set('bookingFlow', 'REWARD');
    return { url: u.href, taskKey: t.id, evidence: 'public_search_url_candidate',
      inferred: t.origin.type === 'CITY' || t.destination.type === 'CITY' || t.trip === 'ROUND_TRIP' && t.returnDate === null };
  }
  function key(token, t) { return token + ':' + t.id; }
  function remember(k) { if (!submitted[k]) { submitted[k] = true; order.push(k); if (order.length > 1024) delete submitted[order.shift()]; } }
  function result(state, phase, reason, extra) {
    var out = { state: state, phase: phase, reason: reason || '', taskKey: current ? current.task.id : '' };
    if (extra) Object.keys(extra).forEach(function (k) { out[k] = extra[k]; });
    if (current) current.last = out; return out;
  }
  function init(value, token) {
    token = safeId(token); var t = task(value), fingerprint = JSON.stringify(t);
    if (!current || current.token !== token || current.task.id !== t.id) current = { token: token, task: t, fingerprint: fingerprint,
      navigated: false, started: Date.now(), phase: 'prepare', phaseAt: Date.now(), last: null, clicked: false, action: null, actions: 0 };
    else if (current.fingerprint !== fingerprint) invalid('task_changed');
    return current;
  }
  function markNavigated(value, token) { try { var c = init(value, token); c.navigated = true; return result('working', 'wait_official_page', ''); } catch (e) { return result('unsupported', 'prepare', e.message); } }
  function text(el) { return el ? String(el.innerText == null ? el.textContent || '' : el.innerText).replace(/\s+/g, ' ').trim().slice(0, 400) : ''; }
  function visible(el) {
    if (!el || el.disabled || el.getAttribute('aria-disabled') === 'true') return false;
    for (var p = el, i = 0; p && i < 12; p = p.parentElement, i++) {
      if (p.hidden || p.getAttribute('aria-hidden') === 'true') return false;
      var style = window.getComputedStyle ? window.getComputedStyle(p) : null;
      if (style && (style.display === 'none' || style.visibility === 'hidden' || style.visibility === 'collapse' || style.opacity === '0')) return false;
    }
    var r = el.getBoundingClientRect && el.getBoundingClientRect(); return !r || r.width > 0 && r.height > 0;
  }
  function all(root, selector) { try { return Array.prototype.slice.call(root.querySelectorAll(selector)); } catch (_) { return []; } }
  function one(root, selector) { try { return root.querySelector(selector); } catch (_) { return null; } }
  function denialReason() {
    var title = String(document.title || '').slice(0, 200);
    if (/access denied|too many requests|forbidden|security check|접근.*제한/i.test(title)) return 'DOCUMENT_DENIAL_TITLE';
    var nodes = all(document, '#captcha,#challenge-container,[data-captcha],iframe[src*="captcha"],iframe[title*="captcha" i]');
    for (var i = 0; i < nodes.length; i++) {
      var node = nodes[i]; if (!inViewport(node)) continue;
      var r = node.getBoundingClientRect(), width = window.innerWidth, height = window.innerHeight;
      var modal = node.closest && node.closest('[aria-modal="true"],dialog[open],[role="dialog"]');
      var label = (node.getAttribute('title') || '') + ' ' + (node.getAttribute('aria-label') || '');
      var intersectionWidth = Math.max(0, Math.min(width, r.right) - Math.max(0, r.left));
      var intersectionHeight = Math.max(0, Math.min(height, r.bottom) - Math.max(0, r.top));
      var modalChallenge = modal && inViewport(modal);
      var largeSurface = intersectionWidth >= width * 0.45 && intersectionHeight >= height * 0.2 && intersectionWidth * intersectionHeight >= width * height * 0.28;
      var verificationFrame = /challenge|verification|verify|보안|인증|human|robot/i.test(label) && intersectionWidth >= Math.min(200, width * 0.6) && intersectionHeight >= 100;
      if (!(modalChallenge || largeSurface || verificationFrame)) continue;
      // Presence of a background reCAPTCHA badge is not evidence that the result
      // page was denied. An actual visible verification surface still stops us.
      if (typeof document.elementFromPoint === 'function') {
        var x = Math.max(0, Math.min(width - 1, Math.max(0, r.left) + intersectionWidth / 2));
        var y = Math.max(0, Math.min(height - 1, Math.max(0, r.top) + intersectionHeight / 2));
        var hit = document.elementFromPoint(x, y);
        if (!hit || !(hit === node || node.contains(hit) || modalChallenge && hit === modal)) continue;
      }
      return 'BLOCKING_VERIFICATION_UI';
    }
    return '';
  }
  function queryDenial(signal) {
    if (!signal || signal.status !== 'access_restricted' && signal.state !== 'BLOCKED') return '';
    return /^(?:(?:UNRECOGNIZED_)?QUERY_HTTP_403|(?:UNRECOGNIZED_)?QUERY_HTTP_429)$/.test(signal.reasonCode || '') ? signal.reasonCode : 'QUERY_ACCESS_RESTRICTED';
  }
  function inspectAccess(signal) {
    var why = queryDenial(signal) || denialReason();
    return { blocked: !!why, reason: why };
  }
  function setPhase(name) { if (current.phase !== name) { current.phase = name; current.phaseAt = Date.now(); } }
  function wait(name, reason) { setPhase(name); return Date.now() - current.phaseAt > MAX_PHASE_MS ? result('unsupported', name, reason) : result('working', name, ''); }
  function click(el, phase) {
    if (!foreground) return result('working', 'paused', 'APP_NOT_FOREGROUND');
    if (!visible(el) || typeof el.click !== 'function') return result('unsupported', phase, 'control_not_available');
    if (++current.actions > 64) return result('unsupported', phase, 'action_limit');
    setPhase(phase); try { el.click(); return result('working', phase, ''); } catch (_) { return result('unsupported', phase, 'control_action_failed'); }
  }
  function codePresent(node, code) {
    if (!node) return false; var field = one(node, '[data-testid="bwc-station-picker__input"],input');
    return new RegExp('(?:^|[^A-Z])' + code + '(?:[^A-Z]|$)').test((field && field.value || '') + ' ' + text(node));
  }
  function queryProof(t, u) {
    return u.searchParams.get('connections') === connections(t) && u.searchParams.get('cabinClass') === t.cabin &&
      u.searchParams.get('bookingFlow') === 'REWARD' && u.searchParams.get('pax') === '1:0:0:0:0:0:0:0' && u.searchParams.get('activeConnection') === '0';
  }
  function valueText(el) {
    if (!el) return ''; if (el.tagName === 'SELECT') { var o = el.options[el.selectedIndex]; return (el.value || '') + ' ' + text(o); }
    return text(el);
  }
  function cabinProof(root, expected) {
    return all(root, 'select,[role="combobox"],[data-testid*="cabin"],[aria-label*="cabin" i],[aria-label*="좌석"]').some(function (e) {
      if (!visible(e)) return false; if (e.value === expected) return true;
      return CABINS[expected].test(valueText(e)) || CABINS[expected].test(text(one(e, 'option:checked')));
    });
  }
  function tripProof(root, t, queryBound) {
    var e = one(root, '[data-testid="bwsfe-widget__trip-type-selector"]');
    if (!e || !visible(e)) return queryBound;
    var value = valueText(e);
    return t.trip === 'ROUND_TRIP' ? /round[ -]?trip|aller[ -]retour|왕복/i.test(value) : /one[ -]?way|aller simple|편도/i.test(value);
  }
  function paxProof(root) {
    var n = one(root, 'input[name="adults"],input[data-testid="adults"]');
    if (n && String(n.value) === '1') return all(root, 'input[name="children"],input[name="infants"]').every(function (e) { return String(e.value) === '0'; });
    return all(root, '.bw-search-widget__passengers-dialog-opener,[data-testid*="passenger"],[aria-label*="passenger" i],button,[role="button"]').some(function (e) {
      if (!visible(e)) return false; var v = text(e);
      return /(?:^|\D)1\s*(?:성인|adult(?:e)?s?)(?:$|\b|\s)/i.test(v) && !/[1-9]\s*(?:children|child|infant|enfant|어린이|유아)/i.test(v);
    });
  }
  function milesControl(root) {
    return all(root, 'input[type="checkbox"],[role="switch"],[role="tab"],button[aria-pressed]').find(function (e) {
      var label = e.getAttribute('aria-label') || ''; var id = e.id;
      if (e.labels) Array.prototype.forEach.call(e.labels, function (l) { label += ' ' + text(l); });
      if (id) all(root, 'label').forEach(function (l) { if (l.htmlFor === id) label += ' ' + text(l); });
      label += ' ' + text(e) + ' ' + (e.getAttribute('data-testid') || '');
      return visible(controlTarget(e)) && /miles|마일|reward/i.test(label);
    });
  }
  function controlTarget(e) {
    if (e && e.labels) for (var i = 0; i < e.labels.length; i++) if (visible(e.labels[i])) return e.labels[i];
    if (visible(e)) return e;
    var label = e && e.closest && e.closest('label'); return visible(label) ? label : null;
  }
  function inViewport(el) {
    if (!visible(el)) return false;
    var r = el.getBoundingClientRect && el.getBoundingClientRect();
    if (!r || ![r.left, r.top, r.right, r.bottom, window.innerWidth, window.innerHeight].every(Number.isFinite)) return false;
    return r.right > 0 && r.bottom > 0 && r.left < window.innerWidth && r.top < window.innerHeight;
  }
  function siteChoiceRequired() {
    return all(document, '#accept_cookies_btn,#onetrust-banner-sdk,#onetrust-accept-btn-handler,#onetrust-reject-all-handler').some(inViewport);
  }
  function searchInteractable(button) {
    if (!foreground) return false;
    if (typeof button.scrollIntoView === 'function') button.scrollIntoView({ block: 'center', inline: 'nearest' });
    if (typeof document.elementFromPoint !== 'function') return true;
    var r = button.getBoundingClientRect(), width = window.innerWidth, height = window.innerHeight;
    if (![r.left, r.top, r.right, r.bottom, width, height].every(Number.isFinite)) return true;
    if (r.right <= 0 || r.bottom <= 0 || r.left >= width || r.top >= height) return false;
    var x = Math.max(0, Math.min(width - 1, r.left + r.width / 2));
    var y = Math.max(0, Math.min(height - 1, r.top + r.height / 2));
    var hit = document.elementFromPoint(x, y); return !!hit && (hit === button || button.contains(hit));
  }
  function selected(e) { return e && (e.checked === true || e.getAttribute('aria-checked') === 'true' || e.getAttribute('aria-selected') === 'true' || e.getAttribute('aria-pressed') === 'true'); }
  function dateTextMatches(value, isoDate, queryBound) {
    if (value.indexOf(isoDate) >= 0 || value.indexOf(isoDate.replace(/-/g, '')) >= 0) return true;
    if (!queryBound) return false; var parts = isoDate.split('-'), day = Number(parts[2]), month = Number(parts[1]);
    var months = ['jan(?:uary|vier)?', 'feb(?:ruary)?|févr(?:ier)?', 'mar(?:ch|s)?', 'apr(?:il)?|avr(?:il)?', 'may|mai', 'jun(?:e)?|juin', 'jul(?:y)?|juil(?:let)?', 'aug(?:ust)?|août', 'sep(?:tember|tembre)?', 'oct(?:ober|obre)?', 'nov(?:ember|embre)?', 'dec(?:ember)?|déc(?:embre)?'];
    return new RegExp('(?:^|\\D)0?' + day + '(?:\\D|$)').test(value) && (new RegExp('(?:^|\\D)' + month + '\\s*월').test(value) || new RegExp(months[month - 1], 'i').test(value));
  }
  function dateProof(root, t, queryBound) {
    var nodes = all(root, 'input[type="date"],input[name*="date" i],[data-testid="bwsfe-datepicker__toggle-button"],[aria-label*="date" i],[aria-label*="여행일자"]');
    var values = nodes.filter(visible).map(function (e) { return (e.value || '') + ' ' + text(e) + ' ' + (e.getAttribute('aria-label') || ''); });
    var departure = values.some(function (v) { return dateTextMatches(v, t.date, queryBound); });
    return departure && (!t.returnDate || values.some(function (v) { return dateTextMatches(v, t.returnDate, queryBound); }));
  }
  function dayId(value) { var d = value.split('-'); return 'bwc-day_' + d[0] + '_' + (Number(d[1]) - 1) + '_' + Number(d[2]); }
  function chooseDates(root, t) {
    if (current.action === 'calendar_confirm') {
      current.action = null; return wait('verify_dates', 'dates_not_verified');
    }
    var dialog = one(document, '[role="dialog"]'), day = document.getElementById(dayId(current.action === 'calendar_return' ? t.returnDate : t.date));
    if (!dialog || !visible(dialog)) {
      var toggle = one(root, '[data-testid="bwsfe-datepicker__toggle-button"]');
      if (!toggle || !visible(toggle)) return wait('verify_dates', 'date_controls_not_found');
      if (current.action === 'calendar_open') return wait('calendar_open', 'calendar_not_opened');
      current.action = 'calendar_open'; return click(toggle, 'calendar_open');
    }
    if (current.action === 'calendar_selected' || current.action === 'calendar_return_selected') {
      var confirm = one(dialog, '[data-testid="bwc-calendar__confirm-button"]');
      if (!confirm) return wait('calendar_confirm', 'calendar_confirm_not_found');
      current.action = 'calendar_confirm'; return click(confirm, 'calendar_confirm');
    }
    if (day && visible(day)) {
      if (current.action === 'calendar_return') current.action = 'calendar_return_selected';
      else current.action = t.returnDate ? 'calendar_return' : 'calendar_selected';
      return click(day, current.action === 'calendar_return' ? 'calendar_departure' : 'calendar_date');
    }
    return wait('calendar_date', 'date_not_visible');
  }
  function step(value, token, base) {
    if (!foreground) return result('working', 'paused', 'APP_NOT_FOREGROUND');
    var c, u;
    try { c = init(value, token); u = new URL(location.href); } catch (e) { return result('unsupported', 'prepare', e.message); }
    if (stopped[c.token]) return result('stopped', 'stopped', '');
    if (AUTH_HOST.test(u.hostname) || AUTH_PATH.test(u.pathname)) return result('waiting_login', 'official_login', '');
    if (u.protocol !== 'https:' || !HOST.test(u.hostname) || u.username || u.password || u.port && u.port !== '443') return result('blocked', 'page', 'unsupported_origin');
    if (FORBIDDEN.test(u.pathname)) return result('unsupported', 'page', 'not_search_page');
    var denied = denialReason();
    if (denied) { stopped[c.token] = true; return result('blocked', 'site_access', denied); }
    var k = key(c.token, c.task);
    var signal = null;
    try { signal = window.__tongAF && typeof window.__tongAF.requestState === 'function' ? window.__tongAF.requestState() : null; }
    catch (_) { return result('unsupported', 'observe_request', 'observer_state_unavailable'); }
    denied = queryDenial(signal);
    if (denied) { stopped[c.token] = true; return result('blocked', 'site_access', denied); }
    if (signal && signal.status === 'login_required') return result('waiting_login', 'official_login', '');
    if (submitted[k] || c.clicked) return result('submitted', 'wait_response', '');
    if (signal && signal.started === true && (!signal.taskId || signal.taskId === c.task.id) && (!signal.runToken || signal.runToken === c.token)) { remember(k); return result('submitted', 'wait_response', ''); }
    if (siteChoiceRequired()) return result('waiting_login', 'site_consent', 'official_site_choice_required');
    if (all(document, 'input[type="password"]').some(visible)) return result('waiting_login', 'official_login', '');
    if (!c.navigated) {
      var nav; try { nav = buildNavigation(c.task, base || u.origin); } catch (e) { return result('unsupported', 'prepare', e.message); }
      c.navigated = true; return result('navigate', 'official_search', '', nav);
    }
    if (!/^\/(?:[a-z]{2}\/)?search(?:\/|$)/i.test(u.pathname)) return wait('wait_official_page', 'search_page_not_opened');
    var button = one(document, '[data-testid="bwsfe-widget__search-button"]');
    if (!button || !visible(button)) return wait('wait_search_form', 'search_form_not_found');
    if (one(button, '.bwc-button-content--loading') || button.getAttribute('aria-busy') === 'true') return wait('wait_site_request', 'site_request_not_observed');
    var root = button.closest && (button.closest('form') || button.closest('[data-testid="bwsfe-widget"]') || button.closest('.bw-search-widget'));
    if (!root) for (var parent = button.parentElement, depth = 0; parent && depth < 8; parent = parent.parentElement, depth++) {
      if (one(parent, '[data-testid="bwsfe-connection-picker__station-picker--origin"]') && one(parent, '[data-testid="bwsfe-connection-picker__station-picker--destination"]')) { root = parent; break; }
    }
    if (!root) return wait('verify_form', 'search_widget_not_found');
    var queryBound = queryProof(c.task, u);
    if (u.searchParams.has('connections') && !queryBound) return result('unsupported', 'verify_form', 'search_context_mismatch');
    if (!tripProof(root, c.task, queryBound)) return wait('verify_trip', 'trip_not_verified');
    var from = one(root, '[data-testid="bwsfe-connection-picker__station-picker--origin"]');
    var to = one(root, '[data-testid="bwsfe-connection-picker__station-picker--destination"]');
    if (!codePresent(from, c.task.origin.code) || !codePresent(to, c.task.destination.code)) return wait('verify_route', 'route_not_verified');
    if (!cabinProof(root, c.task.cabin)) return wait('verify_cabin', 'cabin_not_verified');
    if (!paxProof(root)) return wait('verify_passengers', 'passengers_not_verified');
    var miles = milesControl(root);
    if (!miles) return wait('verify_miles', 'miles_control_not_found');
    if (!selected(miles)) {
      if (current.action === 'miles_clicked') return wait('verify_miles', 'miles_not_selected');
      current.action = 'miles_clicked'; return click(controlTarget(miles), 'select_miles');
    }
    if (!dateProof(root, c.task, queryBound)) return chooseDates(root, c.task);
    if (typeof button.click !== 'function') return result('unsupported', 'submit', 'search_action_unavailable');
    if (!searchInteractable(button)) return wait('search_interaction', 'search_button_obscured');
    if (!foreground) return result('working', 'paused', 'APP_NOT_FOREGROUND');
    // Claim before a click can synchronously navigate or issue a request.
    c.clicked = true; remember(k);
    try { button.click(); return result('submitted', 'wait_response', ''); }
    catch (_) { return result('unsupported', 'submit', 'search_action_failed'); }
  }
  function scrollMetrics(node, root) {
    if (!node) return null;
    var viewport = root ? Number(node.clientHeight) || Number(window.innerHeight) : Number(node.clientHeight);
    var height = Number(node.scrollHeight), top = Number(node.scrollTop);
    if (root) {
      height = Math.max(height, Number(document.documentElement && document.documentElement.scrollHeight) || 0, Number(document.body && document.body.scrollHeight) || 0);
      top = top || Number(window.pageYOffset) || 0;
    }
    if (![viewport, height, top].every(Number.isFinite) || viewport <= 0 || height < 0 || top < 0) return null;
    return { node: node, root: root, viewport: viewport, height: Math.max(height, viewport), top: top, bottom: top + viewport >= height - 3 };
  }
  function resultScroller(collect) {
    var root = document.scrollingElement || document.documentElement || document.body;
    var page = scrollMetrics(root, true), candidates = [];
    if (page && page.height > page.viewport + 3 && !page.bottom && collect.rootStalls < 2) return page;
    var mains = all(document, 'main,[role="main"]').slice(0, 8);
    mains.forEach(function (main) { candidates.push(main); candidates = candidates.concat(all(main, 'div,section,article').slice(0, 192)); });
    var selected = null, area = 0;
    candidates.forEach(function (node) {
      if (!inViewport(node)) return;
      var style = window.getComputedStyle ? window.getComputedStyle(node) : null;
      if (!style || !/^(?:auto|scroll|overlay)$/.test(style.overflowY || '')) return;
      var m = scrollMetrics(node, false); if (!m || m.height <= m.viewport + 3) return;
      var r = node.getBoundingClientRect(), score = r.width * Math.min(r.height, m.viewport);
      if (score > area) { selected = m; area = score; }
    });
    return selected || page;
  }
  function clearQuiet(collect) { collect.quietSince = null; collect.stableChecks = 0; }
  function setForeground(value) {
    var next = value === true, now = Date.now();
    if (next === foreground) return foreground;
    if (current && current.collect) {
      var collect = current.collect;
      if (!next && foreground) collect.pausedAt = now;
      if (next && !foreground && collect.pausedAt != null) { collect.began += Math.max(0, now - collect.pausedAt); collect.pausedAt = null; }
      clearQuiet(collect);
    }
    foreground = next; return foreground;
  }
  function exactObject(value, keys) {
    if (!value || typeof value !== 'object' || Array.isArray(value)) return false;
    var actual = Object.keys(value);
    if (actual.length !== keys.length || !actual.every(function (key) { return keys.indexOf(key) >= 0; })) return false;
    return keys.every(function (key) { var descriptor = Object.getOwnPropertyDescriptor(value, key); return descriptor && Object.prototype.hasOwnProperty.call(descriptor, 'value'); });
  }
  function collectionProof(signal, t, token) {
    var kind = signal.collectionKind;
    if (kind === undefined || kind === 'offers') {
      if (signal.domProof !== undefined) throw new Error('RESULT_DOM_PROOF_INVALID');
      return null; // Backwards compatible network-offers-only caller.
    }
    if (kind !== 'calendar_dom') throw new Error('RESULT_COLLECTION_UNSUPPORTED');
    var p = signal.domProof;
    if (p == null) throw new Error('RESULT_DOM_PROOF_MISSING');
    var keys = ['schema', 'taskId', 'runToken', 'date', 'selectedDate', 'origin', 'destination', 'cabin', 'trip', 'returnDate', 'adults', 'bookingFlow', 'approvedCandidate', 'fingerprint', 'cardCount', 'loading', 'pageGeneration'];
    if (!exactObject(p, keys) || !exactObject(p.origin, ['code', 'type']) || !exactObject(p.destination, ['code', 'type'])) throw new Error('RESULT_DOM_PROOF_INVALID');
    if (p.schema !== 1 || p.approvedCandidate !== true || p.adults !== 1 || p.bookingFlow !== 'REWARD' ||
      typeof p.loading !== 'boolean' || typeof p.fingerprint !== 'string' || !/^afdom-[a-f0-9]{16}$/.test(p.fingerprint) ||
      !Number.isSafeInteger(p.cardCount) || p.cardCount < 1 || p.cardCount > 128 ||
      !Number.isSafeInteger(p.pageGeneration) || p.pageGeneration < 0 || !Number.isSafeInteger(signal.pageGeneration) || signal.pageGeneration < 0) throw new Error('RESULT_DOM_PROOF_INVALID');
    if (p.taskId !== t.id || p.runToken !== token || p.date !== t.date || p.selectedDate !== t.date ||
      p.origin.code !== t.origin.code || p.origin.type !== t.origin.type || p.destination.code !== t.destination.code || p.destination.type !== t.destination.type ||
      p.cabin !== t.cabin || p.trip !== t.trip || p.returnDate !== t.returnDate || p.pageGeneration !== signal.pageGeneration) throw new Error('RESULT_DOM_PROOF_MISMATCH');
    return { fingerprint: p.fingerprint, cardCount: p.cardCount, loading: p.loading, pageGeneration: p.pageGeneration };
  }
  function settleResults(value, token, signal) {
    if (!foreground) return result('working', 'paused', 'APP_NOT_FOREGROUND');
    var t, u, c = current;
    try { t = task(value); token = safeId(token); u = new URL(location.href); }
    catch (_) { return result('unsupported', 'collect_results', 'RESULT_TASK_MISMATCH'); }
    if (!c || c.token !== token || c.task.id !== t.id || c.fingerprint !== JSON.stringify(t)) return result('unsupported', 'collect_results', 'RESULT_TASK_MISMATCH');
    if (stopped[token]) return result('stopped', 'stopped', '');
    if (AUTH_HOST.test(u.hostname) || AUTH_PATH.test(u.pathname)) { c.collect = null; return result('waiting_login', 'official_login', ''); }
    if (u.protocol !== 'https:' || !HOST.test(u.hostname) || u.username || u.password || u.port && u.port !== '443' || FORBIDDEN.test(u.pathname) || !/^\/(?:[a-z]{2}\/)?search(?:\/|$)/i.test(u.pathname)) return result('unsupported', 'collect_results', 'RESULT_SCOPE_MISMATCH');
    if (!signal || signal.taskId !== t.id || signal.runToken !== token) return result('unsupported', 'collect_results', 'RESULT_TASK_MISMATCH');
    if (!Number.isSafeInteger(signal.pending) || signal.pending < 0 || !Number.isSafeInteger(signal.generation) || signal.generation < 0) return result('unsupported', 'collect_results', 'RESULT_SIGNAL_INVALID');
    var denied = queryDenial(signal) || denialReason();
    if (denied) { stopped[token] = true; return result('blocked', 'site_access', denied); }
    if (signal.status === 'login_required' || all(document, 'input[type="password"]').some(visible)) { c.collect = null; return result('waiting_login', 'official_login', ''); }
    var dom;
    try { dom = collectionProof(signal, t, token); }
    catch (e) { return result('unsupported', 'collect_results', /^(?:RESULT_DOM_PROOF_INVALID|RESULT_DOM_PROOF_MISSING|RESULT_DOM_PROOF_MISMATCH|RESULT_COLLECTION_UNSUPPORTED)$/.test(e && e.message || '') ? e.message : 'RESULT_DOM_PROOF_INVALID'); }
    if (c.collect && c.collect.domRequired && !dom) return result('unsupported', 'collect_results', 'RESULT_DOM_PROOF_MISSING');
    // A native-approved, independently validated displayed-fare candidate can
    // survive only these two JSON-format failures. It never weakens access,
    // ownership, malformed date/station, capacity or timeout failures.
    var softFormat = signal.status === 'unsupported_response' && signal.collectionKind === 'calendar_dom' && !!dom && /^(?:SCHEMA_MISMATCH|ROUND_TRIP_PRICE)$/.test(signal.reasonCode || '');
    if ((signal.status === 'unsupported_response' || signal.state === 'FAILED') && !softFormat) return result('unsupported', 'collect_results', 'RESULT_RESPONSE_UNSUPPORTED');
    var now = Date.now();
    var collect = c.collect || (c.collect = { began: now, steps: 0, generation: signal.generation, lastHeight: null, lastNode: null, quietSince: null, stableChecks: 0, lastScrollNode: null, lastScrollTop: null, rootStalls: 0, pausedAt: null, domRequired: false, domFingerprint: null, domCardCount: null, domLoading: null, domPageGeneration: null });
    var extra = { generation: signal.generation, scrollSteps: collect.steps };
    if (dom) {
      collect.domRequired = true;
      if (collect.domFingerprint !== dom.fingerprint || collect.domCardCount !== dom.cardCount || collect.domLoading !== dom.loading || collect.domPageGeneration !== dom.pageGeneration) clearQuiet(collect);
      collect.domFingerprint = dom.fingerprint; collect.domCardCount = dom.cardCount; collect.domLoading = dom.loading; collect.domPageGeneration = dom.pageGeneration;
      extra.domFingerprint = dom.fingerprint; extra.cardCount = dom.cardCount; extra.pageGeneration = dom.pageGeneration;
    }
    if (now - collect.began >= MAX_RESULT_MS) return result('unsupported', 'collect_results', 'RESULT_SCROLL_LIMIT', extra);
    if (signal.generation !== collect.generation) { collect.generation = signal.generation; clearQuiet(collect); }
    if (signal.pending > 0 || document.readyState === 'loading') { clearQuiet(collect); return result('working', 'collect_results', '', extra); }
    if (siteChoiceRequired()) { c.collect = null; return result('waiting_login', 'site_consent', 'official_site_choice_required', extra); }
    var m = resultScroller(collect);
    if (!m) { clearQuiet(collect); return result('working', 'collect_results', '', extra); }
    if (m.root && collect.lastScrollNode === m.node && collect.lastScrollTop != null && m.top <= collect.lastScrollTop + 1 && !m.bottom) {
      collect.rootStalls++;
      if (collect.rootStalls >= 2) m = resultScroller(collect) || m;
    } else if (m.root && collect.lastScrollNode === m.node && collect.lastScrollTop != null && m.top > collect.lastScrollTop + 1) collect.rootStalls = 0;
    if (!foreground) { clearQuiet(collect); return result('working', 'paused', 'APP_NOT_FOREGROUND', extra); }
    if (m.node !== collect.lastNode || m.height !== collect.lastHeight) { clearQuiet(collect); collect.lastNode = m.node; collect.lastHeight = m.height; }
    if (!m.bottom) {
      clearQuiet(collect);
      if (collect.steps >= MAX_RESULT_STEPS) return result('unsupported', 'collect_results', 'RESULT_SCROLL_LIMIT', extra);
      if (!foreground || current !== c || stopped[token]) return result(stopped[token] ? 'stopped' : 'working', 'paused', 'APP_NOT_FOREGROUND', extra);
      try {
        var next = Math.min(Math.max(0, m.height - m.viewport), m.top + Math.max(40, Math.floor(m.viewport * 0.7)));
        collect.lastScrollNode = m.node; collect.lastScrollTop = m.top; collect.steps++; m.node.scrollTop = next;
        extra.scrollSteps = collect.steps;
      } catch (_) { return result('unsupported', 'collect_results', 'RESULT_SCROLL_UNAVAILABLE', extra); }
      return result('working', 'collect_results', '', extra);
    }
    collect.lastScrollNode = null; collect.lastScrollTop = null;
    if (dom && dom.loading) { clearQuiet(collect); return result('working', 'collect_results', '', extra); }
    if (collect.quietSince == null) { collect.quietSince = now; collect.stableChecks = 1; }
    else collect.stableChecks++;
    if (now - collect.began >= MIN_RESULT_MS && collect.stableChecks >= 2 && now - collect.quietSince >= RESULT_QUIET_MS) return result('ready', 'collect_results', '', extra);
    return result('working', 'collect_results', '', extra);
  }
  window.__tongAFAuto = Object.freeze({ version: 1, buildNavigation: buildNavigation,
    buildUrl: function (t, base) { return buildNavigation(t, base).url; }, step: step, markNavigated: markNavigated,
    settleResults: settleResults, setForeground: setForeground, inspectAccess: inspectAccess,
    stop: function (token) { try { stopped[safeId(token)] = true; } catch (_) {} return result('stopped', 'stopped', ''); },
    reset: function (token) { if (!token || current && current.token === token) current = null; },
    state: function () { return current && current.last ? Object.assign({}, current.last) : { state: 'working', phase: 'prepare', reason: '', taskKey: '' }; }
  });
})();

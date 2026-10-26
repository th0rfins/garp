/* GARP — Fillmore Handbill SPA. Peta dulu, statistik di Analitik. */
const API = {
  async orders() {
    const r = await fetch('/api/orders');
    if (!r.ok) throw new Error('orders ' + r.status);
    return r.json();
  },
  async stats() {
    const r = await fetch('/api/stats');
    if (!r.ok) throw new Error('stats ' + r.status);
    return r.json();
  },
  async order(id) {
    const r = await fetch('/api/orders/' + id);
    if (!r.ok) throw new Error('order ' + r.status);
    return r.json();
  },
  async addLink(text) {
    const r = await fetch('/api/orders', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ link: text }),
    });
    if (!r.ok) throw new Error('add ' + r.status);
    return r.json();
  },
};

const MOCK = {
  stats: {
    total: 42, active: 6, completed: 33, cancelled: 3, revenue: 33 * 15000,
    daily: [
      { date: 'Sen', count: 5 }, { date: 'Sel', count: 8 }, { date: 'Rab', count: 3 },
      { date: 'Kam', count: 9 }, { date: 'Jum', count: 6 }, { date: 'Sab', count: 7 },
      { date: 'Min', count: 4 },
    ],
    top_merchants: [
      { name: 'Warung Soto Ambyar', count: 9 }, { name: 'Ayam Geprek Cak Yudi', count: 7 },
      { name: 'Mie Ayam Bangka 88', count: 5 },
    ],
    avg_eta_seconds: 420,
    today_count: 2,
  },
  orders: [
    { id: 1, merchant: 'Warung Soto Ambyar - Sidanegara', bookingCode: 'A-9QPPCSCGXX3DAV', status: 'ontheway', eta: 251, progress: 72, driver: 'Bagus', vehicle: 'Honda Vario 125', plate: 'R 5974 IP', lat: -6.2140, lng: 106.8450, link: 'https://app.grab.com/s/6fErEvQn', createdAt: '12:10', message: '' },
    { id: 2, merchant: 'Ayam Geprek Bakar Cak Yudi', bookingCode: 'A-K2LM39XX9PLQ', status: 'prepare', eta: 480, progress: 12, driver: null, lat: null, lng: null, link: 'https://app.grab.com/s/Ab12Cd3f', createdAt: '12:35', message: '' },
    { id: 3, merchant: 'Mie Ayam Bangka 88', bookingCode: 'A-8ZQQ1PPPLM2C', status: 'completed', eta: null, progress: 100, driver: 'Andi', vehicle: 'Honda Beat', plate: 'B 4321 XYZ', lat: -6.2210, lng: 106.8520, link: 'https://app.grab.com/s/Xy9zWq1p', createdAt: '11:00', message: '' },
    { id: 6, merchant: 'Nasi Goreng Gila Sabang', bookingCode: 'A-PLOKIJUHYGT', status: 'ontheway', eta: 96, progress: 41, driver: 'Dedi', vehicle: 'Honda Vario 160', plate: 'B 5567 QR', lat: -6.1880, lng: 106.8390, link: 'https://app.grab.com/s/Zx8yVw2m', createdAt: '12:52', message: '' },
  ],
};

/* Normalize backend order -> internal shape */
function norm(o) {
  return {
    id: o.id,
    merchant: o.merchant || 'Tanpa nama',
    bookingCode: o.booking_code || '',
    status: o.status,
    eta: o.eta_seconds,
    progress: o.status === 'completed' ? 100 : (o.eta_seconds != null ? Math.max(5, 100 - Math.min(100, Math.round(o.eta_seconds / 12))) : 0),
    driver: o.driver_name || null,
    vehicle: o.driver_vehicle || '',
    plate: o.driver_plate || '',
    rating: o.driver_rating || null,
    photo: o.driver_photo || '',
    statusMessage: o.status_message || '',
    route: o.route || null,
    traffic: o.traffic || null,
    completeTime: o.complete_time || '',
    arrivalTime: o.driver_arrival_time || '',
    multiStop: !!o.multi_stop,
    lat: o.driver_lat,
    lng: o.driver_lng,
    destLat: o.dest_lat,
    destLng: o.dest_lng,
    destText: o.dest_text || '',
    link: o.link || '',
    createdAt: (o.created_at || '').slice(11, 16) || '',
    message: o.error || '',
  };
}

let USING_MOCK = false;
async function getData() {
  try {
    const [stats, orders] = await Promise.all([API.stats(), API.orders()]);
    USING_MOCK = false;
    return { stats: {
      total: stats.total, active: stats.active, completed: stats.completed,
      cancelled: stats.cancelled, revenue: stats.value,
      daily: dailyFromStats(stats.daily),
      topMerchants: stats.top_merchants || [],
      avgEta: stats.avg_eta_seconds,
      todayCount: stats.today_count ?? 0,
      today: stats.today,
    }, orders: orders.map(norm) };
  } catch (e) {
    USING_MOCK = true;
    return { stats: MOCK.stats, orders: MOCK.orders.map(norm) };
  }
}

function dailyFromStats(daily) {
  // backend: {'2026-10-24': 1}; build last-30-day series
  const days = ['Min','Sen','Sel','Rab','Kam','Jum','Sab'];
  const out = [];
  const now = new Date();
  for (let i = 29; i >= 0; i--) {
    const d = new Date(now.getTime() - i * 86400000);
    const key = d.toISOString().slice(0, 10);
    out.push({ date: days[d.getDay()], full: key, count: (daily && daily[key]) || 0 });
  }
  return out;
}

/* ---------- Helpers ---------- */
const $ = (s) => document.querySelector(s);
const $$ = (s) => document.querySelectorAll(s);
const rupiah = (n) => 'Rp ' + (n ?? 0).toLocaleString('id-ID');
const STATUS_LABEL = {
  prepare: 'Disiapkan', allocated: 'Driver ke resto', ontheway: 'Melaju',
  completed: 'Tiba', delivered: 'Tiba', cancelled: 'Batal', expired: 'Kedaluwarsa',
};
const fmtEta = (sec) => sec == null ? null : (sec >= 60 ? Math.round(sec / 60) + ' mnt' : sec + ' dtk');
function esc(s) {
  return String(s ?? '').replace(/[&<>\"']/g, (c) => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
}

/* ---------- Navigation ---------- */
let currentPage = 'map';
function setPage(page) {
  currentPage = page;
  $$('.page').forEach((p) => p.classList.toggle('active', p.id === 'page-' + page));
  $$('.rail-btn, .mobilebar button').forEach((n) => n.classList.toggle('active', n.dataset.page === page));
  $$('.rail-logo').forEach((n) => n.classList.toggle('active', page === 'add'));
  if (page === 'map') setTimeout(() => { initBigMap(); if (bigMap) bigMap.resize(); }, 60);
  if (page === 'analytics') setTimeout(initCharts, 80);
  refresh();
}
$$('.rail-btn, .rail-logo').forEach((n) => n.addEventListener('click', () => setPage(n.dataset.page)));
$$('.mobilebar button').forEach((n) => n.addEventListener('click', () => setPage(n.dataset.page)));

/* ================================================================
   BIG MAP
   ================================================================ */
let bigMap, markers = {}, SELECTED_ID = null, ORDERS = [], liveTimer = null, mockT = 0;
let routeSrcAdded = false;

function initBigMap() {
  if (bigMap) return;
  bigMap = new maplibregl.Map({
    container: 'bigmap',
    style: 'https://tiles.openfreemap.org/styles/dark',
    center: [106.845, -6.21],
    zoom: 13.5,
    attributionControl: { compact: true },
  });
  bigMap.addControl(new maplibregl.NavigationControl({ showCompass: false }), 'top-right');
  bigMap.dragRotate.disable();
  bigMap.touchZoomRotate.disableRotation();
}

function mkClass(o) {
  if (['cancelled', 'expired', 'completed'].includes(o.status)) return 'mk done';
  if (o.status === 'prepare') return 'mk alert';
  return 'mk active';
}

function setMarker(id, lngLat, cls, clickable) {
  let m = markers[id];
  if (!m) {
    const el = document.createElement('div');
    el.className = cls;
    el.innerHTML = '🛵';
    m = new maplibregl.Marker({ element: el, anchor: 'center' })
      .setLngLat(lngLat).addTo(bigMap);
    if (clickable) el.addEventListener('click', () => selectOrder(id));
    markers[id] = m;
  } else {
    const el = m.getElement();
    el.className = cls;
    m.setLngLat(lngLat);
  }
}

function ensureRouteLayers() {
  if (routeSrcAdded) return;
  bigMap.addSource('route', { type: 'geojson', data: { type: 'Feature', properties: {}, geometry: { type: 'LineString', coordinates: [] } } });
  bigMap.addLayer({
    id: 'route-glow', type: 'line', source: 'route', layout: { 'line-cap': 'round', 'line-join': 'round' },
    paint: { 'line-color': '#5e6ad2', 'line-width': 9, 'line-opacity': .25 },
  });
  bigMap.addLayer({
    id: 'route-line', type: 'line', source: 'route', layout: { 'line-cap': 'round', 'line-join': 'round' },
    paint: { 'line-color': '#5e6ad2', 'line-width': 3.5, 'line-opacity': .95 },
  });
  routeSrcAdded = true;
}

function setRouteLine(coords) {
  if (!coords || coords.length < 2) return;
  ensureRouteLayers();
  bigMap.getSource('route').setData({ type: 'Feature', properties: {}, geometry: { type: 'LineString', coordinates: coords } });
}

function clearRouteLine() {
  if (routeSrcAdded) bigMap.getSource('route').setData({ type: 'Feature', properties: {}, geometry: { type: 'LineString', coordinates: [] } });
}

function renderMarkers() {
  initBigMap();
  const shown = ORDERS.filter((o) => o.lat != null && ['prepare', 'allocated', 'ontheway'].includes(o.status));
  const keep = new Set(shown.map((o) => o.id));
  Object.entries(markers).forEach(([id, m]) => {
    if (!keep.has(id)) { m.remove(); delete markers[id]; }
  });
  shown.forEach((o) => setMarker(o.id, [o.lng, o.lat], mkClass(o), true));
  const sel = ORDERS.find((x) => x.id === SELECTED_ID);
  const selLive = sel && ['prepare', 'allocated', 'ontheway'].includes(sel.status);
  // real road-snapped route from Grab routing engine (decoded 1e6 polyline)
  if (sel && selLive && sel.route && sel.route.length > 1) {
    setRouteLine(sel.route.map(([a, b]) => [b, a]));  // [lat,lng] -> [lng,lat]
  } else if (sel && selLive && sel.lat != null) {
    const dest = sel.destLat != null ? [sel.destLng, sel.destLat] : [sel.lng + 0.002, sel.lat + 0.0015];
    setRouteLine([[sel.lng, sel.lat], dest]);
  } else {
    clearRouteLine();
  }
}

function selectOrder(id, { fly = true } = {}) {
  SELECTED_ID = id;
  startOrderStream(id);
  renderStrip();
  renderMarkers();
  if (!fly) return;
  const o = ORDERS.find((x) => x.id === id);
  if (o && o.lat != null && bigMap && ['prepare', 'allocated', 'ontheway'].includes(o.status)) {
    const pts = (o.route && o.route.length > 1)
      ? o.route.map(([a, b]) => [b, a])
      : [[o.lng, o.lat], [o.destLng ?? o.lng + 0.002, o.destLat ?? o.lat + 0.0015]];
    bigMap.fitBounds(new maplibregl.LngLatBounds(pts[0], pts[0]).extend(
      pts.reduce((b, c) => b.extend(c), new maplibregl.LngLatBounds(pts[0], pts[0]))
    ), { padding: { top: 80, bottom: 240, left: 60, right: 60 }, maxZoom: 17 });
  }
}

/* fit all active orders (default multi-track overview) */
let overviewDone = false;
function fitAllActive() {
  if (!bigMap) return;
  const pts = [];
  ORDERS.filter((o) => o.lat != null && ['prepare', 'allocated', 'ontheway'].includes(o.status))
    .forEach((o) => {
      pts.push([o.lng, o.lat]);
      if (o.destLat != null) pts.push([o.destLng, o.destLat]);
      (o.route || []).forEach(([a, b]) => pts.push([b, a]));
    });
  if (!pts.length) return;
  const bb = pts.reduce((b, c) => b.extend(c), new maplibregl.LngLatBounds(pts[0], pts[0]));
  bigMap.fitBounds(bb, { padding: { top: 90, bottom: 250, left: 60, right: 60 }, maxZoom: 15, duration: 600 });
}

function renderStrip() {
  const el = $('#order-strip');
  const list = ORDERS.filter((o) => ['prepare', 'allocated', 'ontheway'].includes(o.status));
  el.innerHTML = list.map((o) => `
    <div class="strip-chip ${o.id === SELECTED_ID ? 'sel' : ''}" data-sel="${o.id}">
      <span class="sc-status ${o.status}"></span><span class="sc-name">${esc(o.merchant)}</span>
    </div>`).join('');
  $$('#order-strip .strip-chip').forEach((c) =>
    c.addEventListener('click', () => selectOrder(c.dataset.sel)));
  $('#order-strip').style.display = list.length ? '' : 'none';
  $('#map-empty').classList.toggle('hidden', list.length > 0);
}

/* ---------- Floating card ---------- */
function renderFloatCard(o) {
  const fc = $('#float-card');
  if (!o) { fc.style.display = 'none'; return; }
  const wasHidden = fc.style.display === 'none';
  fc.style.display = '';
  if (wasHidden) { fc.style.opacity = '0'; fc.style.transform = 'translateY(10px)';
    requestAnimationFrame(() => { fc.style.opacity = ''; fc.style.transform = ''; }); }
  const done = ['completed', 'delivered', 'cancelled', 'expired'].includes(o.status);
  $('#fc-id').textContent = o.merchant;
  $('#fc-meta').textContent = [o.vehicle, o.plate].filter(Boolean).join(' · ') || 'menunggu driver';
  const b = $('#fc-badge');
  b.textContent = STATUS_LABEL[o.status] || o.status;
  b.className = 'fc-badge' + (['cancelled', 'expired'].includes(o.status) ? ' err' : o.status === 'prepare' ? '' : '');
  $('#fc-from').textContent = 'Resto';
  $('#fc-to').textContent = 'Lokasimu';
  $('#fc-progress-bar').style.width = (o.progress ?? 0) + '%';
  $('#fc-progress-val').textContent = Math.min(100, Math.round(o.progress ?? 0)) + '%';
  $('#fc-eta').textContent = done ? (o.status === 'completed' ? 'Tiba ✓' : '—') : (fmtEta(o.eta) || '—');
  $('#fc-driver').textContent = [o.driver, o.rating ? '★ ' + o.rating : ''].filter(Boolean).join(' · ') || 'mencari…';
  // driver photo (S3 presigned, valid 6 jam) — replaces bike icon when available
  const img = $('#fc-photo'), av = $('#fc-avatar');
  if (o.photo) {
    if (img.src !== o.photo) img.src = o.photo;
    img.classList.remove('hidden'); av.classList.add('has-photo');
    img.onerror = () => { img.classList.add('hidden'); av.classList.remove('has-photo'); };
  } else { img.classList.add('hidden'); av.classList.remove('has-photo'); }
  $('#fc-vehicle').textContent = o.vehicle || '—';
  $('#fc-plate').textContent = o.plate || '—';
  $('#fc-dest').textContent = o.destText || '—';
  $('#fc-dest').title = o.destText || '';
  $('#fc-created').textContent = o.createdAt || '—';
  $('#fc-complete').textContent = o.completeTime ? String(o.completeTime).slice(11, 16) + ' WIB' : (done ? '—' : 'berjalan');
  // ETA caveat (sec.9): ETAs counts to driver's next ACTIVE stop which may be another order's dropoff
  const etaItem = $('#fc-eta');
  const newEta = done ? (o.status === 'completed' ? 'Tiba ✓' : '—') : (fmtEta(o.eta) || '—');
  if (etaItem.textContent !== newEta && etaItem.textContent !== '—') {
    etaItem.classList.remove('flashing'); void etaItem.offsetWidth; etaItem.classList.add('flashing');
  }
  etaItem.textContent = newEta;
  let caveat = $('#fc-eta-caveat');
  if (!caveat) {
    caveat = document.createElement('div');
    caveat.id = 'fc-eta-caveat'; caveat.className = 'fc-caveat';
    etaItem.closest('.fc-eta-item').appendChild(caveat);
  }
  caveat.textContent = (!done && o.multiStop && o.eta != null) ? 'ETA ke stop berikutnya (double order)' : '';
  const sm = $('#fc-status-msg') || (() => { const d = document.createElement('div'); d.id = 'fc-status-msg'; d.className = 'fc-meta'; $('#fc-body').prepend(d); return d; })();
  sm.textContent = o.statusMessage || '';
}

/* fc collapse (mobile): default collapsed — only head bar visible, tap to expand */
$('#fc-toggle').addEventListener('click', () => $('#float-card').classList.toggle('collapsed'));
$('#float-card').addEventListener('click', (e) => {
  if (e.target.closest('.fc-toggle') || e.target.closest('[data-fc="body"]')) return;
  if (window.innerWidth <= 900) $('#float-card').classList.toggle('collapsed');
});
function initFcCollapse() {
  $('#float-card').classList.toggle('collapsed', window.innerWidth <= 900);
}
window.addEventListener('resize', initFcCollapse);

/* ---------- Live stream per order ---------- */
function stopOrderStream() { if (liveTimer) { clearInterval(liveTimer); liveTimer = null; } }
function startOrderStream(id) {
  stopOrderStream();
  if (USING_MOCK) {
    liveTimer = setInterval(() => {
      const o = ORDERS.find((x) => x.id === id);
      if (!o) return;
      mockT += 2;
      if (o.lat != null) { o.lat += 0.0004 * Math.sin(mockT / 5); o.lng += 0.0006 * mockT / 40; }
      if (o.eta != null) o.eta = Math.max(0, o.eta - 6);
      if (o.progress != null) o.progress = Math.min(99, (o.progress ?? 0) + 0.4);
      renderFloatCard(o);
      renderMarkers();
    }, 3000);
    return;
  }
  if (typeof EventSource !== 'undefined') {
    const es = new EventSource('/api/orders/' + encodeURIComponent(id) + '/stream');
    es.onmessage = (ev) => {
      try {
        const o = norm(JSON.parse(ev.data));
        const i = ORDERS.findIndex((x) => x.id === id);
        if (i >= 0) ORDERS[i] = o;
        renderFloatCard(o);
        renderMarkers();
      } catch (e) {}
    };
    es.onerror = () => { es.close(); fallbackPoll(id); };
    liveTimer = es;
    return;
  }
  fallbackPoll(id);
}
function fallbackPoll(id) {
  loadOrderInto(id);
  liveTimer = setInterval(() => loadOrderInto(id), 5000);
}
async function loadOrderInto(id) {
  try {
    const o = await API.order(id);
    const i = ORDERS.findIndex((x) => x.id === id);
    if (i >= 0) ORDERS[i] = { ...ORDERS[i], ...o };
    renderFloatCard(ORDERS[i]);
    renderMarkers();
  } catch (e) { /* keep last state */ }
}

/* ================================================================
   ANALYTICS
   ================================================================ */
let chartDaily;
let analyticsTimer = null;
function initCharts() {
  const stats = LAST_STATS;
  if (!stats) return;
  if (chartDaily) chartDaily.destroy();
  Chart.defaults.color = '#63636b';
  Chart.defaults.borderColor = '#1c1c20';
  Chart.defaults.font.family = 'Inter';
  Chart.defaults.font.size = 11;
  const DAILY_MAX = 30;
  const show = stats.daily.slice(-DAILY_MAX);
  const labelEvery = show.length > 14 ? 5 : 1;
  chartDaily = new Chart($('#chart-daily'), {
    type: 'line',
    data: {
      labels: show.map((d) => d.date),
      datasets: [{
        data: show.map((d) => d.count),
        borderColor: '#5e6ad2',
        borderWidth: 2.5,
        pointRadius: (ctx) => (ctx.parsed && ctx.parsed.y > 0 ? 3 : 0),
        pointBackgroundColor: '#5e6ad2',
        fill: true,
        backgroundColor: 'rgba(94, 106, 210, .10)',
        tension: .35,
      }],
    },
    options: {
      maintainAspectRatio: false,
      animation: false,
      plugins: {
        legend: { display: false },
        tooltip: {
          displayColors: false,
          backgroundColor: '#232327',
          titleFont: { family: 'Inter', weight: '600' },
          callbacks: { label: (c) => c.parsed.y + ' pesanan' },
        },
      },
      scales: {
        y: { beginAtZero: true, ticks: { precision: 0 } },
        x: {
          grid: { display: false },
          ticks: { maxRotation: 0, autoSkip: true, callback: function(v, i) { return i % labelEvery === 0 ? this.getLabelForValue(v) : ''; } },
        },
      },
    },
  });
  renderFreqList(stats);
  renderMeta(stats);
}
function renderFreqList(stats) {
  const el = $('#freq-list');
  const top = (stats.topMerchants || []).slice(0, 5);
  if (!top.length) { el.innerHTML = '<li class="muted-row">Belum ada data.</li>'; return; }
  const max = top[0].count;
  el.innerHTML = top.map((m, i) => `
    <li class="freq-row">
      <span class="freq-rank">${i + 1}</span>
      <span class="freq-name">${esc(m.name)}</span>
      <span class="freq-bar"><i style="width:${Math.round((m.count / max) * 100)}%"></i></span>
      <span class="freq-n">${m.count}×</span>
    </li>`).join('');
}
function renderMeta(stats) {
  $('#kpi-cancelled').textContent = `${stats.cancelled} batal`;
  $('#kpi-avg-eta').textContent = fmtEta(stats.avgEta) || '—';
  $('#kpi-today').textContent = stats.todayCount;
  $('#kpi-today-date').textContent = stats.today
    ? new Date(stats.today + 'T00:00:00+07:00').toLocaleDateString('id-ID', { weekday: 'long', day: 'numeric', month: 'long', timeZone: 'Asia/Jakarta' })
    : 'hari ini (WIB)';
  const show = stats.daily.slice(-30);
  const sum = show.reduce((a, d) => a + d.count, 0);
  const best = show.reduce((a, d) => d.count > (a?.count ?? -1) ? d : a, null);
  $('#daily-note').textContent = best && best.count > 0
    ? `${sum} pesanan dalam 30 hari · terbanyak ${best.full} (${best.count})`
    : `${sum} pesanan dalam 30 hari`;
}
/* analytics refresh without re-triggering entrance animation:
   animation only plays on page OPEN, data updates are silent */
function startAnalyticsLoop() {
  if (analyticsTimer) clearInterval(analyticsTimer);
  analyticsTimer = setInterval(() => {
    if (currentPage === 'analytics') initCharts();
  }, 8000);
}

/* ---------- Orders table ---------- */
const PAGE_SIZE = 15;
let orderPage = 1;
function renderOrdersTable() {
  const q = $('#search').value.toLowerCase().trim();
  const filtered = ORDERS.filter((o) => {
    if (q && !(o.merchant.toLowerCase().includes(q) || (o.bookingCode || '').toLowerCase().includes(q))) return false;
    return true;
  });
  const pages = Math.max(1, Math.ceil(filtered.length / PAGE_SIZE));
  if (orderPage > pages) orderPage = pages;
  const pageItems = filtered.slice((orderPage - 1) * PAGE_SIZE, orderPage * PAGE_SIZE);
  renderPager(pages, filtered.length);
  $('#tbl-orders tbody').innerHTML = pageItems.map((o) => `
    <tr data-open="${o.id}">
      <td class="td-main"><span class="row-name">${esc(o.merchant)}</span><span class="row-sub">${esc(o.bookingCode || '—')}</span></td>
      <td><span class="st-badge ${o.status}">${STATUS_LABEL[o.status] || o.status}</span></td>
      <td class="t-muted">${esc(o.createdAt)}</td>
      <td class="td-actions">
        <a href="${esc(o.link)}" target="_blank" rel="noopener" class="row-ic" title="Buka link asli" aria-label="Buka link asli ${esc(o.merchant)}" onclick="event.stopPropagation()">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M18 13v6a2 2 0 01-2 2H5a2 2 0 01-2-2V8a2 2 0 012-2h6M15 3h6v6M10 14L21 3"/></svg>
        </a>
        <button class="row-ic watch" title="Pantau di peta" aria-label="Pantau ${esc(o.merchant)} di peta">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/></svg>
        </button>
      </td>
    </tr>`).join('');
  $('#orders-empty').classList.toggle('hidden', ORDERS.length > 0);
}

function renderPager(pages, total) {
  const info = $('#pager-info');
  const btns = $('#pager-btns');
  const filtered = total;
  if (filtered <= PAGE_SIZE) { $('#pager').style.display = 'none'; return; }
  $('#pager').style.display = '';
  const from = (orderPage - 1) * PAGE_SIZE + 1;
  const to = Math.min(orderPage * PAGE_SIZE, filtered);
  info.textContent = `${from}–${to} dari ${filtered}`;
  const nums = [];
  for (let p = 1; p <= pages; p++) {
    if (p === 1 || p === pages || Math.abs(p - orderPage) <= 1) nums.push(p);
    else if (nums[nums.length - 1] !== '…') nums.push('…');
  }
  btns.innerHTML =
    `<button data-pg="${orderPage - 1}" ${orderPage === 1 ? 'disabled' : ''} aria-label="Sebelumnya">‹</button>` +
    nums.map((p) => p === '…'
      ? '<button disabled>…</button>'
      : `<button data-pg="${p}" class="${p === orderPage ? 'cur' : ''}">${p}</button>`).join('') +
    `<button data-pg="${orderPage + 1}" ${orderPage === pages ? 'disabled' : ''} aria-label="Berikutnya">›</button>`;
  $$('#pager-btns button[data-pg]').forEach((b) =>
    b.addEventListener('click', () => {
      const p = parseInt(b.dataset.pg, 10);
      if (!p || p === orderPage) return;
      orderPage = p;
      renderOrdersTable();
      document.querySelector('.table-wrap').scrollTop = 0;
    }));
}

/* ---------- Add ---------- */
$('#btn-add').addEventListener('click', async () => {
  const text = $('#add-input').value.trim();
  if (!text) { $('#add-result').textContent = 'Tempel dulu teks share dari Grab.'; return; }
  $('#add-result').textContent = 'Menyimpan…';
  try {
    const created = await API.addLink(text);
    $('#add-result').textContent = 'Tersimpan.';
    $('#add-input').value = '';
    await refresh();
    if (created && created.id) { selectOrder(created.id); setPage('map'); }
  } catch (e) {
    $('#add-result').textContent = USING_MOCK
      ? 'Backend belum tersambung — link belum bisa disimpan.'
      : 'Gagal menyimpan: ' + e.message + '. Cek koneksi lalu coba lagi.';
  }
});

/* ---------- Refresh ---------- */
let LAST_STATS = null;
async function refresh() {
  const { stats, orders } = await getData();
  LAST_STATS = stats;
  ORDERS = orders;
  renderKPIs(stats);
  renderOrdersTable();
  if (currentPage === 'analytics') initCharts();
  if (currentPage === 'map') {
    renderStrip();
    renderMarkers();
    const active = ORDERS.filter((o) => ['prepare', 'allocated', 'ontheway'].includes(o.status));
    const sel = ORDERS.find((o) => o.id === SELECTED_ID);
    if (sel && active.some((o) => o.id === sel.id)) renderFloatCard(sel);
    else if (active.length) {
      SELECTED_ID = active[0].id;
      renderFloatCard(active[0]);
      renderStrip();
    } else {
      SELECTED_ID = null;
      renderFloatCard(null);
    }
    if (SELECTED_ID) startOrderStream(SELECTED_ID);
    if (!overviewDone && active.length) { overviewDone = true; fitAllActive(); }
    if (!active.length) overviewDone = false;
  }
}
function renderKPIs(stats) {
  const active = ORDERS.filter((o) => ['prepare', 'allocated', 'ontheway'].includes(o.status)).length;
  $('#kpi-active').textContent = `${active}/${stats.total}`;
  $('#kpi-total').textContent = stats.total;
  $('#kpi-revenue').textContent = rupiah(stats.revenue);
}
$('#search').addEventListener('input', () => { orderPage = 1; renderOrdersTable(); });
document.addEventListener('click', (e) => {
  const watch = e.target.closest('.row-ic.watch');
  if (watch) { selectOrder(watch.closest('tr').dataset.open); setPage('map'); return; }
  const row = e.target.closest('tr[data-open]');
  if (row) { selectOrder(row.dataset.open); setPage('map'); }
});
setPage('map');
initFcCollapse();
startAnalyticsLoop();
refresh();
setInterval(() => { if (currentPage !== 'map') refresh(); }, 8000);

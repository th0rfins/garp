/* GARP — map-first SPA. Map bersih; statistik di halaman Analitik (shadcn Card). */
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
    total: 42, active: 6, completed: 33, cancelled: 3, revenue: 42 * 15000,
    daily: [
      { date: 'Sen', count: 5 }, { date: 'Sel', count: 8 }, { date: 'Rab', count: 3 },
      { date: 'Kam', count: 9 }, { date: 'Jum', count: 6 }, { date: 'Sab', count: 7 },
      { date: 'Min', count: 4 },
    ],
    statusDist: { preparing: 2, ontheway: 4, completed: 33, cancelled: 3 },
  },
  orders: [
    { id: 1, merchant: 'Warung Soto Ambyar - Sidanegara', bookingCode: 'A-9QPPCSCGXX3DAV', status: 'ontheway', eta: 251, progress: 72, driver: 'Bagus', vehicle: 'Honda Vario 125', plate: 'R 5974 IP', lat: -6.2140, lng: 106.8450, link: 'https://app.grab.com/s/6fErEvQn', createdAt: '12:10', message: '' },
    { id: 2, merchant: 'Ayam Geprek Bakar Cak Yudi', bookingCode: 'A-K2LM39XX9PLQ', status: 'prepare', eta: 480, progress: 12, driver: null, lat: null, lng: null, link: 'https://app.grab.com/s/Ab12Cd3f', createdAt: '12:35', message: '' },
    { id: 3, merchant: 'Mie Ayam Bangka 88', bookingCode: 'A-8ZQQ1PPPLM2C', status: 'completed', eta: null, progress: 100, driver: 'Andi', vehicle: 'Honda Beat', plate: 'B 4321 XYZ', lat: -6.2210, lng: 106.8520, link: 'https://app.grab.com/s/Xy9zWq1p', createdAt: '11:00', message: '' },
    { id: 4, merchant: 'Bebek Goreng Slamet', bookingCode: 'A-QW12ZXCVBNM3', status: 'cancelled', eta: null, progress: 0, driver: null, lat: null, lng: null, link: 'https://app.grab.com/s/Po1uyTr4', createdAt: '19:20', message: '' },
    { id: 5, merchant: 'Sate Ayam Madura Cak Hari', bookingCode: 'A-LKJHGFDSAQWE', status: 'completed', eta: null, progress: 100, driver: 'Rio', vehicle: 'Yamaha Nmax', plate: 'B 6789 AB', lat: -6.1980, lng: 106.8310, link: 'https://app.grab.com/s/Qwerty01', createdAt: '18:05', message: '' },
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
    return { stats: { total: stats.total, active: stats.active, completed: stats.completed, cancelled: stats.cancelled, revenue: stats.value, daily: dailyFromStats(stats.daily) }, orders: orders.map(norm) };
  } catch (e) {
    USING_MOCK = true;
    return { stats: MOCK.stats, orders: MOCK.orders };
  }
}

function dailyFromStats(daily) {
  // backend: {'2026-10-24': 1}; build last-7-day labels Sen..Min
  const out = [];
  const days = ['Min','Sen','Sel','Rab','Kam','Jum','Sab'];
  const now = new Date();
  for (let i = 6; i >= 0; i--) {
    const d = new Date(now.getTime() - i * 86400000);
    const key = d.toISOString().slice(0, 10);
    out.push({ date: days[d.getDay()], count: (daily && daily[key]) || 0 });
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
  if (page === 'map') setTimeout(() => { initBigMap(); bigMap.invalidateSize(); }, 60);
  if (page === 'analytics') setTimeout(initCharts, 80);
  refresh();
}
$$('.rail-btn, .rail-logo').forEach((n) => n.addEventListener('click', () => setPage(n.dataset.page)));
$$('.mobilebar button').forEach((n) => n.addEventListener('click', () => setPage(n.dataset.page)));

/* ================================================================
   BIG MAP
   ================================================================ */
let bigMap, markers = {}, routeLine, destPin, SELECTED_ID = null, ORDERS = [], liveTimer = null, mockT = 0;

function initBigMap() {
  if (bigMap) return;
  bigMap = L.map('bigmap', { zoomControl: false }).setView([-6.21, 106.845], 14);
  L.control.zoom({ position: 'topright' }).addTo(bigMap);
  L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', { maxZoom: 19 }).addTo(bigMap);
}

function mkClass(o) {
  if (['cancelled', 'expired', 'completed'].includes(o.status)) return 'mk done';
  if (o.status === 'prepare') return 'mk alert';
  return 'mk active';
}
function mkIcon(o) {
  return L.divIcon({ className: '', html: `<div class="${mkClass(o)}">🛵</div>`, iconSize: [26, 26], iconAnchor: [13, 13] });
}

function renderMarkers() {
  initBigMap();
  const shown = ORDERS.filter((o) => o.lat != null);
  Object.values(markers).forEach((m) => bigMap.removeLayer(m));
  markers = {};
  shown.forEach((o) => {
    const m = L.marker([o.lat, o.lng], { icon: mkIcon(o) }).addTo(bigMap);
    m.on('click', () => selectOrder(o.id));
    markers[o.id] = m;
  });
  const sel = ORDERS.find((x) => x.id === SELECTED_ID);
  if (sel && sel.lat != null) {
    const dest = sel.destLat != null ? [sel.destLat, sel.destLng] : [sel.lat + 0.0015, sel.lng + 0.002];
    if (!routeLine) {
      routeLine = L.polyline([], { color: '#f2b33d', weight: 3, opacity: .85, dashArray: '4 8' }).addTo(bigMap);
      destPin = L.marker(dest, { icon: L.divIcon({ className: '', html: '<div class="mk dest">📍</div>', iconSize: [26, 26], iconAnchor: [13, 13] }) }).addTo(bigMap);
    }
    routeLine.setLatLngs([[sel.lat, sel.lng], dest]);
    destPin.setLatLng(dest);
    routeLine.setStyle({ opacity: .85 });
    destPin.getElement().style.opacity = 1;
  } else if (routeLine) {
    routeLine.setStyle({ opacity: 0 });
    if (destPin) destPin.getElement().style.opacity = 0;
  }
}

function selectOrder(id) {
  SELECTED_ID = id;
  startOrderStream(id);
  renderStrip();
  renderMarkers();
  const o = ORDERS.find((x) => x.id === id);
  if (o && o.lat != null && bigMap) {
    const d = o.destLat != null ? [o.destLat, o.destLng] : [o.lat + 0.0015, o.lng + 0.002];
    bigMap.fitBounds(L.latLngBounds([[o.lat, o.lng], d]).pad(.4), { animate: true, maxZoom: 17 });
  }
}

function renderStrip() {
  const el = $('#order-strip');
  const live = ORDERS.filter((o) => ['prepare', 'allocated', 'ontheway'].includes(o.status));
  const list = live.length ? live : ORDERS.slice(0, 6);
  el.innerHTML = list.map((o) => `
    <div class="strip-chip ${o.id === SELECTED_ID ? 'sel' : ''}" data-sel="${o.id}">
      <span class="sc-status ${o.status}"></span><span class="sc-name">${esc(o.merchant)}</span>
    </div>`).join('');
  $$('#order-strip .strip-chip').forEach((c) =>
    c.addEventListener('click', () => selectOrder(c.dataset.sel)));
}

/* ---------- Floating card ---------- */
function renderFloatCard(o) {
  if (!o) { $('#float-card').style.display = 'none'; return; }
  $('#float-card').style.display = '';
  const done = ['completed', 'delivered', 'cancelled', 'expired'].includes(o.status);
  $('#fc-avatar').textContent = o.driver ? '🧑‍✈️' : '🛵';
  $('#fc-id').textContent = o.merchant;
  $('#fc-meta').textContent = [o.vehicle, o.plate].filter(Boolean).join(' · ') || 'menunggu driver';
  const b = $('#fc-badge');
  b.textContent = STATUS_LABEL[o.status] || o.status;
  b.className = 'fc-badge' + (['cancelled', 'expired'].includes(o.status) ? ' err' : o.status === 'prepare' ? ' warn' : '');
  $('#fc-from').textContent = 'Resto';
  $('#fc-to').textContent = 'Lokasimu';
  $('#fc-progress-bar').style.width = (o.progress ?? 0) + '%';
  $('#fc-progress-val').textContent = Math.min(100, Math.round(o.progress ?? 0)) + '%';
  $('#fc-eta').textContent = done ? (o.status === 'completed' ? 'Tiba ✓' : '—') : (fmtEta(o.eta) || '—');
  $('#fc-driver').textContent = o.driver || 'mencari…';
}

/* fc collapse (mobile): default collapsed on small screens */
$('#fc-toggle').addEventListener('click', () => $('#float-card').classList.toggle('collapsed'));
function initFcCollapse() {
  if (window.innerWidth <= 900) $('#float-card').classList.add('collapsed');
}

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
   ANALYTICS (shadcn-style cards + charts)
   ================================================================ */
let chartDaily;
let analyticsTimer = null;
function initCharts() {
  const stats = LAST_STATS;
  if (!stats) return;
  if (chartDaily) chartDaily.destroy();
  Chart.defaults.color = 'rgba(156,163,175,.9)';
  Chart.defaults.borderColor = 'rgba(32,32,32,.8)';
  Chart.defaults.font.family = 'Poppins';
  Chart.defaults.font.size = 11;
  chartDaily = new Chart($('#chart-daily'), {
    type: 'line',
    data: {
      labels: (stats.daily || []).map((d) => d.date),
      datasets: [{
        data: (stats.daily || []).map((d) => d.count),
        borderColor: '#f2b33d',
        borderWidth: 2,
        pointRadius: 3,
        pointBackgroundColor: '#f2b33d',
        fill: true,
        backgroundColor: 'rgba(242,179,61,.08)',
        tension: .35,
      }],
    },
    options: {
      maintainAspectRatio: false,
      animation: false,
      plugins: {
        legend: { display: false },
        tooltip: { displayColors: false, callbacks: { label: (c) => c.parsed.y + ' pesanan' } },
      },
      scales: { y: { beginAtZero: true, ticks: { precision: 0 } }, x: { grid: { display: false } } },
    },
  });
  renderFreqList();
}
function renderFreqList() {
  const counts = {};
  ORDERS.forEach((o) => { counts[o.merchant] = (counts[o.merchant] || 0) + 1; });
  const top = Object.entries(counts).sort((a, b) => b[1] - a[1]).slice(0, 5);
  const el = $('#freq-list');
  if (!top.length) { el.innerHTML = '<li class="muted-row">Belum ada data.</li>'; return; }
  const max = top[0][1];
  el.innerHTML = top.map(([name, n], i) => `
    <li class="freq-row">
      <span class="freq-rank">${i + 1}</span>
      <span class="freq-name">${esc(name)}</span>
      <span class="freq-bar"><i style="width:${Math.round((n / max) * 100)}%"></i></span>
      <span class="freq-n">${n}×</span>
    </li>`).join('');
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
function renderOrdersTable() {
  const q = $('#search').value.toLowerCase().trim();
  const ACTIVE_ST = ['prepare', 'allocated', 'ontheway'];
  const filtered = ORDERS.filter((o) => {
    if (q && !(o.merchant.toLowerCase().includes(q) || (o.bookingCode || '').toLowerCase().includes(q))) return false;
    return true;
  });
  $('#tbl-orders tbody').innerHTML = filtered.map((o) => `
    <tr data-open="${o.id}">
      <td style="font-weight:500">${esc(o.merchant)}</td>
      <td class="t-muted" style="font-variant-numeric:tabular-nums">${esc(o.bookingCode || '—')}</td>
      <td><span class="st-badge ${o.status}">${STATUS_LABEL[o.status] || o.status}</span></td>
      <td class="t-muted">${esc(o.createdAt)}</td>
      <td><a href="${esc(o.link)}" target="_blank" rel="noopener" class="t-muted" onclick="event.stopPropagation()" style="text-decoration:none">buka ↗</a></td>
      <td><span class="t-open">pantau</span></td>
    </tr>`).join('');
  $('#orders-empty').classList.toggle('hidden', ORDERS.length > 0);
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
    const sel = ORDERS.find((o) => o.id === SELECTED_ID);
    if (sel) renderFloatCard(sel);
    else if (ORDERS.length) {
      const firstActive = ORDERS.find((o) => ['ontheway', 'allocated', 'prepare'].includes(o.status)) || ORDERS[0];
      SELECTED_ID = firstActive.id;
      renderFloatCard(firstActive);
      renderStrip();
    } else renderFloatCard(null);
    if (SELECTED_ID) startOrderStream(SELECTED_ID);
  }
}
function renderKPIs(stats) {
  const active = ORDERS.filter((o) => ['prepare', 'allocated', 'ontheway'].includes(o.status)).length;
  $('#kpi-active').textContent = `${active}/${stats.total}`;
  $('#kpi-total').textContent = stats.total;
  $('#kpi-done').textContent = stats.completed;
  $('#kpi-revenue').textContent = rupiah(stats.revenue);
}
$('#search').addEventListener('input', () => renderOrdersTable());
document.addEventListener('click', (e) => {
  const row = e.target.closest('tr[data-open]');
  if (row) { selectOrder(row.dataset.open); setPage('map'); }
});
function syncFcCollapse() {
  const fc = $('#float-card');
  if (window.innerWidth <= 900) fc.classList.add('collapsed');
  else fc.classList.remove('collapsed');
}
window.addEventListener('resize', syncFcCollapse);

setPage('map');
syncFcCollapse();
startAnalyticsLoop();
refresh();
setInterval(() => { if (currentPage !== 'map') refresh(); }, 8000);

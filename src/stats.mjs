// Stats computation — matches the live API contract.
import { db } from './db.mjs';

const AVG_ORDER_VALUE_ESTIMATE = 15000; // fallback when total_price is not set

/** Return stats object matching the live /api/stats shape. */
export function getStats() {
  const rows = db.prepare(`
    SELECT status, eta_seconds, merchant, total_price, created_at FROM orders
  `).all();

  const now = new Date();
  const today = toYMD(now);

  let total = rows.length;
  let active = 0;
  let completed = 0;
  let cancelled = 0;
  let valueSum = 0;
  const etaVals = [];
  const byMerchant = new Map();
  const daily = new Map();

  for (const r of rows) {
    if (ACTIVE_STATUSES.has(r.status)) active++;
    else if (COMPLETED_STATUSES.has(r.status)) completed++;
    else if (CANCELLED_STATUSES.has(r.status)) cancelled++;

    if (r.eta_seconds != null) etaVals.push(r.eta_seconds);
    if (r.total_price != null) valueSum += r.total_price;
    else if (COMPLETED_STATUSES.has(r.status)) valueSum += AVG_ORDER_VALUE_ESTIMATE;

    if (r.merchant) {
      byMerchant.set(r.merchant, (byMerchant.get(r.merchant) || 0) + 1);
    }

    const d = (r.created_at || '').slice(0, 10);
    if (d) {
      daily.set(d, (daily.get(d) || 0) + 1);
    }
  }

  // top_merchants: top 10 by count
  const topMerchants = [...byMerchant.entries()]
    .sort((a, b) => b[1] - a[1])
    .slice(0, 10)
    .map(([name, count]) => ({ name, count }));

  // avg_eta_seconds: average of non-null values
  const avgEta = etaVals.length
    ? Math.round(etaVals.reduce((a, b) => a + b, 0) / etaVals.length)
    : 0;

  const todayCount = daily.get(today) || 0;

  // value: sum of total_price (or estimate for completed orders without price)
  const value = valueSum;

  return {
    total,
    active,
    completed,
    cancelled,
    value,
    daily: Object.fromEntries(daily.entries()),
    top_merchants: topMerchants,
    avg_eta_seconds: avgEta,
    today,
    today_count: todayCount,
  };
}

const ACTIVE_STATUSES = new Set(['prepare', 'allocated', 'ontheway']);
const COMPLETED_STATUSES = new Set(['completed', 'delivered']);
const CANCELLED_STATUSES = new Set(['cancelled', 'expired']);

function toYMD(d) {
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${y}-${m}-${day}`;
}

// Orders CRUD + polling loop for active orders.
// Uses the built-in node:sqlite module.
import { db } from './db.mjs';
import { resolveShareLink, fetchTrackingData, newOrderId } from './grab.mjs';

const ACTIVE_STATUSES = new Set(['prepare', 'allocated', 'ontheway']);
const COMPLETED_STATUSES = new Set(['completed', 'delivered']);
const CANCELLED_STATUSES = new Set(['cancelled', 'expired']);

const stmts = {
  insert: db.prepare(`
    INSERT INTO orders (id, share_id, link, status, created_at, updated_at)
    VALUES (?, ?, ?, 'prepare', ?, ?)
  `),
  get: db.prepare('SELECT * FROM orders WHERE id = ?'),
  list: db.prepare('SELECT * FROM orders ORDER BY created_at DESC'),
  update: db.prepare(`
    UPDATE orders SET
      share_id = ?, booking_code = ?, merchant = ?, status = ?, status_message = ?,
      eta_seconds = ?, driver_name = ?, driver_vehicle = ?, driver_plate = ?,
      driver_rating = ?, driver_photo = ?, driver_lat = ?, driver_lng = ?,
      dest_lat = ?, dest_lng = ?, dest_text = ?, route = ?, traffic = ?,
      multi_stop = ?, error = ?, total_price = ?,
      complete_time = ?, dropoff_time = ?, driver_arrival_time = ?,
      updated_at = ?
    WHERE id = ?
  `),
  byLink: db.prepare('SELECT * FROM orders WHERE link = ?'),
  activeList: db.prepare("SELECT id, share_id, link, status FROM orders WHERE status IN ('prepare','allocated','ontheway')"),
};

/** Convert a DB row to the API response shape (camelCase, JSON-parsed fields). */
function rowToOrder(row) {
  if (!row) return null;
  return {
    id: row.id,
    booking_code: row.booking_code,
    merchant: row.merchant,
    status: row.status,
    status_message: row.status_message,
    eta_seconds: row.eta_seconds,
    driver_name: row.driver_name,
    driver_vehicle: row.driver_vehicle,
    driver_plate: row.driver_plate,
    driver_rating: row.driver_rating,
    driver_photo: row.driver_photo,
    driver_lat: row.driver_lat,
    driver_lng: row.driver_lng,
    dest_lat: row.dest_lat,
    dest_lng: row.dest_lng,
    dest_text: row.dest_text,
    route: row.route ? JSON.parse(row.route) : null,
    traffic: row.traffic ? JSON.parse(row.traffic) : null,
    multi_stop: row.multi_stop,
    link: row.link,
    error: row.error,
    total_price: row.total_price,
    created_at: row.created_at,
    updated_at: row.updated_at,
    complete_time: row.complete_time,
    dropoff_time: row.dropoff_time,
    driver_arrival_time: row.driver_arrival_time,
  };
}

/** Create an order from a Grab share link. Returns the created order. */
export async function createOrderFromLink(link) {
  const now = new Date().toISOString();
  // Check for duplicate link
  const existing = stmts.byLink.get(link);
  if (existing) return rowToOrder(existing);
  // Resolve the share link
  const resolved = await resolveShareLink(link);
  const shareId = resolved?.shareId || null;
  const orderId = resolved?.orderId || newOrderId();
  stmts.insert.run(orderId, shareId, link, now, now);
  const created = stmts.get.get(orderId);
  return rowToOrder(created);
}

export function getOrder(id) {
  const row = stmts.get.get(id);
  return rowToOrder(row);
}

export function listOrders() {
  const rows = stmts.list.all();
  return rows.map(rowToOrder);
}

/** Update an order with fresh tracking data. */
export function updateOrder(id, data) {
  const row = stmts.get.get(id);
  if (!row) return null;
  const now = new Date().toISOString();
  stmts.update.run(
    data.share_id ?? row.share_id,
    data.booking_code ?? row.booking_code,
    data.merchant ?? row.merchant,
    data.status ?? row.status,
    data.status_message ?? row.status_message,
    data.eta_seconds ?? row.eta_seconds,
    data.driver_name ?? row.driver_name,
    data.driver_vehicle ?? row.driver_vehicle,
    data.driver_plate ?? row.driver_plate,
    data.driver_rating ?? row.driver_rating,
    data.driver_photo ?? row.driver_photo,
    data.driver_lat ?? row.driver_lat,
    data.driver_lng ?? row.driver_lng,
    data.dest_lat ?? row.dest_lat,
    data.dest_lng ?? row.dest_lng,
    data.dest_text ?? row.dest_text,
    data.route ? JSON.stringify(data.route) : row.route,
    data.traffic ? JSON.stringify(data.traffic) : row.traffic,
    data.multi_stop ?? row.multi_stop,
    data.error ?? row.error,
    data.total_price ?? row.total_price,
    data.complete_time ?? row.complete_time,
    data.dropoff_time ?? row.dropoff_time,
    data.driver_arrival_time ?? row.driver_arrival_time,
    now
  );
  return rowToOrder(stmts.get.get(id));
}

/** Poll active orders and update them via fetchTrackingData. */
export async function pollActiveOrders() {
  const active = stmts.activeList.all();
  for (const row of active) {
    if (!row.share_id) continue;
    const data = await fetchTrackingData(row.share_id);
    if (data) updateOrder(row.id, data);
  }
}

/** Start the background polling loop (every 10s). */
export function startPoller() {
  const interval = setInterval(() => {
    pollActiveOrders().catch((e) => console.error('[poller]', e.message));
  }, 10000);
  interval.unref();
  return interval;
}

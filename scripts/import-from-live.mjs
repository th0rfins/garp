// Import orders from a running GARP instance into the local SQLite database.
//
// Usage:
//   GARP_LIVE_URL=https://rtu3oi0i4ati9lmob1c55k4a.sin.prisma.build node scripts/import-from-live.mjs
//
// Default URL is the current Prisma Compute deployment.
import { db, DB_PATH } from '../src/db.mjs';

const BASE = (process.env.GARP_LIVE_URL || 'https://rtu3oi0i4ati9lmob1c55k4a.sin.prisma.build').replace(/\/$/, '');

const res = await fetch(`${BASE}/api/orders`, { signal: AbortSignal.timeout(30000) });
if (!res.ok) {
  console.error(`Failed to fetch ${BASE}/api/orders: HTTP ${res.status}`);
  process.exit(1);
}
const orders = await res.json();
if (!Array.isArray(orders)) {
  console.error('Unexpected response: expected array of orders');
  process.exit(1);
}

const ins = db.prepare(`
  INSERT INTO orders (
    id, share_id, booking_code, merchant, status, status_message,
    eta_seconds, driver_name, driver_vehicle, driver_plate, driver_rating,
    driver_photo, driver_lat, driver_lng, dest_lat, dest_lng, dest_text,
    route, traffic, multi_stop, link, error, total_price,
    created_at, updated_at, complete_time, dropoff_time, driver_arrival_time
  ) VALUES (
    @id, @share_id, @booking_code, @merchant, @status, @status_message,
    @eta_seconds, @driver_name, @driver_vehicle, @driver_plate, @driver_rating,
    @driver_photo, @driver_lat, @driver_lng, @dest_lat, @dest_lng, @dest_text,
    @route, @traffic, @multi_stop, @link, @error, @total_price,
    @created_at, @updated_at, @complete_time, @dropoff_time, @driver_arrival_time
  )
  ON CONFLICT(id) DO UPDATE SET
    share_id = excluded.share_id,
    booking_code = excluded.booking_code,
    merchant = excluded.merchant,
    status = excluded.status,
    status_message = excluded.status_message,
    eta_seconds = excluded.eta_seconds,
    driver_name = excluded.driver_name,
    driver_vehicle = excluded.driver_vehicle,
    driver_plate = excluded.driver_plate,
    driver_rating = excluded.driver_rating,
    driver_photo = excluded.driver_photo,
    driver_lat = excluded.driver_lat,
    driver_lng = excluded.driver_lng,
    dest_lat = excluded.dest_lat,
    dest_lng = excluded.dest_lng,
    dest_text = excluded.dest_text,
    route = excluded.route,
    traffic = excluded.traffic,
    multi_stop = excluded.multi_stop,
    link = excluded.link,
    error = excluded.error,
    total_price = excluded.total_price,
    created_at = excluded.created_at,
    updated_at = excluded.updated_at,
    complete_time = excluded.complete_time,
    dropoff_time = excluded.dropoff_time,
    driver_arrival_time = excluded.driver_arrival_time
`);

const num = (v) => (v == null ? null : Number(v));

let n = 0;
for (const o of orders) {
  // derive share_id when possible: production order id == last 10 chars of shareOrderLink
  ins.run({
    id: o.id,
    share_id: null,
    booking_code: o.booking_code ?? null,
    merchant: o.merchant ?? null,
    status: o.status ?? 'prepare',
    status_message: o.status_message ?? null,
    eta_seconds: num(o.eta_seconds),
    driver_name: o.driver_name ?? null,
    driver_vehicle: o.driver_vehicle ?? null,
    driver_plate: o.driver_plate ?? null,
    driver_rating: num(o.driver_rating),
    driver_photo: o.driver_photo ?? null,
    driver_lat: num(o.driver_lat),
    driver_lng: num(o.driver_lng),
    dest_lat: num(o.dest_lat),
    dest_lng: num(o.dest_lng),
    dest_text: o.dest_text ?? null,
    route: o.route ? JSON.stringify(o.route) : null,
    traffic: o.traffic ? JSON.stringify(o.traffic) : null,
    multi_stop: o.multi_stop ? 1 : 0,
    link: o.link || '',
    error: o.error ?? null,
    total_price: num(o.total_price),
    created_at: o.created_at || new Date().toISOString(),
    updated_at: o.updated_at || o.created_at || new Date().toISOString(),
    complete_time: o.complete_time ?? null,
    dropoff_time: o.dropoff_time ?? null,
    driver_arrival_time: o.driver_arrival_time ?? null,
  });
  n++;
}
console.log(`Imported ${n} orders from ${BASE} -> ${DB_PATH}`);

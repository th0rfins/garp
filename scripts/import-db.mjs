// Import order dari file JSON (atau stdin) ke SQLite — upsert by id.
// Cocok untuk migrasi data dari deployment lama ke deployment baru.
// Usage:
//   node scripts/import-db.mjs backup-20260101.json
//   cat backup.json | node scripts/import-db.mjs
import { db } from '../src/db.mjs';
import { readFile } from 'node:fs/promises';

const inFile = process.argv[2];

let input;
if (inFile) {
  input = await readFile(inFile, 'utf8');
} else {
  input = await new Promise((resolve, reject) => {
    const chunks = [];
    process.stdin.on('data', (c) => chunks.push(c));
    process.stdin.on('end', () => resolve(Buffer.concat(chunks).toString('utf8')));
    process.stdin.on('error', reject);
  });
}

const data = JSON.parse(input);
const orders = Array.isArray(data) ? data : data.orders || [];

const upsert = db.prepare(`
  INSERT INTO orders (
    id, share_id, booking_code, merchant, status, status_message,
    eta_seconds, driver_name, driver_vehicle, driver_plate, driver_rating, driver_photo,
    driver_lat, driver_lng, dest_lat, dest_lng, dest_text,
    route, traffic, multi_stop, link, error, total_price,
    created_at, updated_at, complete_time, dropoff_time, driver_arrival_time
  ) VALUES (
    @id, @share_id, @booking_code, @merchant, @status, @status_message,
    @eta_seconds, @driver_name, @driver_vehicle, @driver_plate, @driver_rating, @driver_photo,
    @driver_lat, @driver_lng, @dest_lat, @dest_lng, @dest_text,
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

let imported = 0;
let skipped = 0;
for (const o of orders) {
  try {
    upsert.run(o);
    imported++;
  } catch {
    skipped++;
  }
}
const total = db.prepare('SELECT COUNT(*) c FROM orders').get().c;

process.stderr.write(
  `Imported: ${imported}, skipped: ${skipped}, total in DB now: ${total}\n`
);

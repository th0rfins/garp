// SQLite database layer — uses the built-in node:sqlite module (zero npm deps).
// Data file: $GARP_DATA_DIR/garp.db (default: ./data/garp.db), WAL mode.
import { DatabaseSync } from 'node:sqlite';
import { mkdirSync } from 'node:fs';
import path from 'node:path';

const DATA_DIR = process.env.GARP_DATA_DIR || path.resolve(process.cwd(), 'data');
mkdirSync(DATA_DIR, { recursive: true });
const DB_PATH = path.join(DATA_DIR, 'garp.db');

export const db = new DatabaseSync(DB_PATH);
export { DB_PATH };

db.exec('PRAGMA journal_mode = WAL;');
db.exec('PRAGMA foreign_keys = ON;');

db.exec(`
CREATE TABLE IF NOT EXISTS orders (
  id                    TEXT PRIMARY KEY,
  share_id              TEXT,
  booking_code          TEXT,
  merchant              TEXT,
  status                TEXT NOT NULL DEFAULT 'prepare',
  status_message        TEXT,
  eta_seconds           INTEGER,
  driver_name           TEXT,
  driver_vehicle        TEXT,
  driver_plate          TEXT,
  driver_rating         REAL,
  driver_photo          TEXT,
  driver_lat            REAL,
  driver_lng            REAL,
  dest_lat              REAL,
  dest_lng              REAL,
  dest_text             TEXT,
  route                 TEXT,
  traffic               TEXT,
  multi_stop            INTEGER NOT NULL DEFAULT 0,
  link                  TEXT NOT NULL,
  error                 TEXT,
  total_price           INTEGER,
  created_at            TEXT NOT NULL,
  updated_at            TEXT,
  complete_time         TEXT,
  dropoff_time          TEXT,
  driver_arrival_time   TEXT
);
CREATE INDEX IF NOT EXISTS idx_orders_created_at ON orders(created_at);
CREATE INDEX IF NOT EXISTS idx_orders_status ON orders(status);
`);

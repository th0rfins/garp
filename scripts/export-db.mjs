// Export semua order dari SQLite ke file JSON (migrasi data antar deployment).
// Usage:
//   node scripts/export-db.mjs                    -> cetak ke stdout
//   node scripts/export-db.mjs backup-20260101.json
import { db } from '../src/db.mjs';
import { writeFile } from 'node:fs/promises';

const outFile = process.argv[2];

const orders = db
  .prepare('SELECT * FROM orders ORDER BY created_at DESC')
  .all();

const payload = {
  exportedAt: new Date().toISOString(),
  count: orders.length,
  orders,
};

const json = JSON.stringify(payload, null, 2) + '\n';

if (outFile) {
  writeFile(outFile, json).then(() => {
    process.stderr.write(`Exported ${orders.length} orders -> ${outFile}\n`);
  });
} else {
  process.stdout.write(json);
}

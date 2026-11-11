# GARP — Orders (self-hosted)

Rebuild dari app GARP yang tadinya jalan di Prisma Compute (Node + Prisma Postgres),
sekarang **self-hosted: Node.js + SQLite, tanpa Prisma, tanpa npm dependency sama sekali**.

- Runtime: Node.js ≥ 22.5 (pakai modul bawaan `node:sqlite` dan `node:http`)
- DB: satu file SQLite (`data/garp.db`, mode WAL)
- Frontend: persis dari app production (MapLibre GL + Chart.js via CDN)

## Jalankan (tanpa Docker)

```bash
node server.mjs          # default: http://localhost:3000
PORT=8080 node server.mjs
```

Tidak perlu `npm install` — tidak ada dependency.

## Jalankan (Docker)

```bash
docker compose up -d --build
# http://<host>:3000
```

Data tersimpan di volume `garp-data` (mount `/data` di dalam container).
Env bisa diubah di `docker-compose.yml` section `environment` (lihat `.env.example`).

## Migrasi data antar deployment (export / import DB)

Karena cuma satu file SQLite, migrasi = copy file, atau lewat skrip JSON:

**Export di host lama:**
```bash
node scripts/export-db.mjs garp-backup.json
# atau ke stdout:
node scripts/export-db.mjs > garp-backup.json
```

**Import di host baru:**
```bash
node scripts/import-db.mjs garp-backup.json
# atau lewat stdin:
cat garp-backup.json | node scripts/import-db.mjs
```

Import adalah **upsert by id** — order yang sudah ada di DB tujuan akan diupdate,
yang baru akan ditambahkan. Aman dijalankan berkali-kali.

Contoh pindah dari host A ke host B:
```bash
# host A
node scripts/export-db.mjs garp-backup.json
scp garp-backup.json hostB:/tmp/

# host B
node scripts/import-db.mjs /tmp/garp-backup.json
```

## Pindahkan data dari app lama (Prisma Compute)

```bash
GARP_LIVE_URL=https://rtu3oi0i4ati9lmob1c55k4a.sin.prisma.build node scripts/import-from-live.mjs
```

Mengunduh semua order dari `/api/orders` app lama dan menyimpannya ke SQLite lokal
(83 order di deployment saat rebuild ini dilakukan).

## API (identik dengan production)

| Method | Path | Hasil |
|---|---|---|
| GET | `/api/orders` | daftar order (terbaru dulu) |
| GET | `/api/orders/:id` | satu order, atau `404 {"error":"Order tidak ditemukan"}` |
| POST | `/api/orders` | body `{"link": "https://app.grab.com/s/..."}` → buat order (201) |
| GET | `/api/stats` | total/active/completed/cancelled/value, daily, top_merchants, avg_eta, today |

Status yang dikenali frontend: `prepare`, `allocated`, `ontheway`, `completed`, `delivered`, `cancelled`, `expired`.
Paste teks share lengkap dari Grab (mis. `"Track my order from X. https://app.grab.com/s/..."`) — aplikasi akan ambil URL-nya sendiri.

## Struktur

```
server.mjs                        HTTP server + routing (node:http, zero deps)
src/db.mjs                        node:sqlite + schema + index
src/orders.mjs                    CRUD + poller (update order aktif tiap 10 dtk)
src/stats.mjs                     agregasi statistik
src/grab.mjs                      resolve share link Grab + EXTENSION POINT tracking
public/                           frontend (index.html, app.js, styles.css)
scripts/import-from-live.mjs      import data dari deployment lama (Prisma Compute)
scripts/export-db.mjs             export semua order ke JSON (migrasi)
scripts/import-db.mjs             import JSON ke SQLite, upsert by id (migrasi)
.env.example                      variabel lingkungan
Dockerfile                        node:24-alpine, data di /data (volume)
docker-compose.yml                compose dengan volume garp-data
```

## Integrasi Grab (bagian yang belum)

Yang sudah terbukti bekerja:
- `resolveShareLink()` — `https://app.grab.com/s/<code>` → redirect 302 →
  parameter `shareOrderLink`; **10 karakter terakhirnya = id order**
  (diverifikasi: `lgb0LF7dy7TL9NIyk5l5` → id `TL9NIyk5l5`, cocok dengan DB production).

Yang **belum** di-reverse-engineer:
- Halaman live tracking `https://sharelocation.grab.com/o/<shareId>` adalah JS app
  (hosting S3) yang menarik data driver/ETA/route dari API internal Grab.
  Coba dilakukan probe API `https://api.grab.com/api/v1/safety/<shareId>/locationsharingdetails`
  tapi mengembalikan 500 internal error — **dihentikan, tidak dilanjutkan**.
- Isi ulang fungsi `fetchTrackingData(shareId)` di `src/grab.mjs` — satu-satunya
  fungsi yang perlu diimplementasi. Poller di `src/orders.mjs` sudah memanggilnya
  otomatis tiap 10 detik untuk semua order aktif.

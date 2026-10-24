# GARP — Grab Share Link Tracker & Manager

Webapp + Android app untuk melacak dan mengelola share link pesanan Grab (Share My Ride / Track Order).
Tempel link share dari Grab (contoh: `Lacak pesanan saya dari Warung Soto Ambyar. https://app.grab.com/s/6fErEvQn`),
sistem otomatis me-resolve token, polling posisi driver tiap 5 detik, dan menampilkan live map + analytics.

## Fitur

- **Live Map (map-first)** — peta Leaflet + OpenStreetMap (tanpa API key), marker driver real-time
- **Tracking per order terisolasi** — satu floating card + SSE stream (`/api/orders/{id}/stream`) per pesanan, fallback polling 5s
- **Analytics** — KPI (Aktif, Total, Tiba, Nilai total = total × Rp 15.000), line chart pesanan per hari, "Paling sering dipesan"
- **Manager** — tabel semua pesanan, search, filter status, buka link asli, pantau langsung
- **Android app** (Kotlin/Compose) — menerima share intent dari app Grab (`Share → GARP`), langsung input ke database; UI sama seperti webapp; first-launch meminta Server URL
- **Satu port** — backend FastAPI menyerve frontend static di port yang sama

## Arsitektur

```
[Grab share link]                [Webapp / Android]
  app.grab.com/s/X   ──resolve──▶  POST /api/orders
       │                                │
       ▼                                ▼
sharelocation token (klb5...)      SQLite (garp.db)
       │                                ▲
       ▼   polling 5s + backoff         │ SSE push
GET api.grab.com/.../bookingdetails ────┘
```

- **Backend**: FastAPI + httpx + SQLite (`server.py`), polling 5 detik per order aktif, exponential backoff saat 429, berhenti otomatis di terminal state (COMPLETE/CANCEL/DELIVER/EXPIRED/TERMINAT/completeFlag=1)
- **Frontend**: vanilla JS SPA + Tailwind CDN + Leaflet + Chart.js, font Poppins
- **Android**: Kotlin + Jetpack Compose + Ktor client, share intent handler `text/plain`

## Deploy di machine lain

### Cara 1 — Docker (recommended)

```bash
git clone https://github.com/kzu7ra/garp
cd garp
docker compose up -d --build
```

Buka `http://<ip-server>:8000`. Selesai. Data tersimpan di volume `garp-data` (SQLite).

### Cara 2 — Manual

```bash
git clone https://github.com/kzu7ra/garp
cd garp
python3 -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
uvicorn server:app --host 0.0.0.0 --port 8000
```

Buka `http://localhost:8000`.

### Setup Android app

1. APK di-build otomatis oleh GitHub Actions setiap push ke `main` → download dari tab **Actions → Build Android APK → Artifacts** (`garp-debug-apk`)
2. Install APK di HP, buka app pertama kali → masukkan **Server URL** (mis. `http://192.168.1.10:8000` atau domain produksi). Bisa diganti kapan saja lewat ikon settings
3. Di app Grab: bagikan pesanan → **GARP** → link otomatis tersimpan ke database dan langsung tampil di webapp & app

> Catatan: server harus bisa diakses HP (satu WiFi, atau expose via domain/tunnel).

## API

| Method | Path | Fungsi |
|---|---|---|
| POST | `/api/orders` | Tambah pesanan dari teks/link share (`{"link": "..."}`) |
| GET | `/api/orders` | Daftar semua pesanan |
| GET | `/api/orders/{id}` | Detail satu pesanan |
| DELETE | `/api/orders/{id}` | Hapus pesanan |
| GET | `/api/orders/{id}/stream` | SSE live update per pesanan |
| GET | `/api/stats` | Statistik (total, aktif, nilai, harian) |

## Konfigurasi

| Env | Default | Fungsi |
|---|---|---|
| `GARP_PORT` | `8000` | Port webapp |

## Struktur

```
garp/
├── server.py            # FastAPI backend + poller + SSE
├── frontend/            # SPA (index.html, app.js, styles.css)
├── android/             # Kotlin/Compose app (share intent, server URL settings)
├── .github/workflows/   # Build APK otomatis
├── docker-compose.yml
├── Dockerfile
└── GRABFOOD_TRACKING_API.md  # Dokumentasi API Grab (referensi)
```

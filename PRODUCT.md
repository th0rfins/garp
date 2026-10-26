# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

## Stack

Vanilla JS SPA (frontend/), FastAPI + SQLite backend menyerve frontend di satu port (server.py, uvicorn/Docker port 8000). Tailwind CDN + Leaflet + Chart.js via CDN, font Poppins. Ada Android companion (Kotlin/Compose) yang push share link via share intent.

## Users

Pemilik (personal/family use): pengguna GrabFood/GrabMart yang sering pesan dan ingin memantau pesanan sendiri dan anggota keluarga dari satu tempat. Bukan produk publik; satu instance dipakai satu rumah.

## Product Purpose

GARP melacak pesanan Grab: tempel share link ("Share My Ride") dari app Grab, sistem resolve token, polling posisi driver tiap 5 detik, tampilkan live map + status + analytics. Success = tahu persis di mana pesanan/driver tanpa buka app Grab, plus riwayat dan statistik pesanan.

## Positioning

Self-hosted tracker multi-link: agregasi banyak share link pesanan (termasuk dari HP anggota keluarga via Android share intent) ke satu dashboard live map — sesuatu yang app Grab sendiri tidak berikan lintas-akun.

## Operating Context

Dipakai dari browser (desktop/HP) melalui deploy di VPS (Docker, port 8000, dapat di-expose via tunnel/domain). Input utama: tempel teks share dari app Grab ke webapp, atau Share → GARP dari Android. Data historis di SQLite, termasuk order legacy dari bot sebelumnya.

## Capabilities and Constraints

- Tambah/list/detail/hapus order; SSE live stream per order dengan fallback polling.
- Polling Grab API: 5s per order aktif, backoff saat 429, stop otomatis di terminal state.
- Tanpa auth/login (single-user assumption); jangan tambahkan fitur multi-tenant tanpa diminta.
- Nilai ekonomis di analytics adalah estimasi hardcoded (total × Rp15.000).
- Order legacy mungkin tanpa merchant/koordinat (link expired).

## Evidence on Hand

README.md, GRABFOOD_TRACKING_API.md (dokumentasi API Grab hasil reverse-engineering), data riwayat 34+ order nyata di SQLite. Tidak ada aset brand (logo, brand guideline) — jangan fabrikasi testimoni/press.

## Product Principles

1. Glanceable: status pesanan terbaca dalam 2 detik dari map/card.
2. Zero-friction input: tempel link → langsung masuk, tanpa form panjang.
3. Honest data: tampilkan apa yang diketahui (kosong = belum ada data), jangan mengarang status.
4. Self-hosted simplicity: satu port, tanpa dependensi layanan berbayar.

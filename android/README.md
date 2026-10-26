# GARP Android

Aplikasi Android native (Kotlin + Jetpack Compose, Material 3, tema **Vercel/Linear flat dark**) untuk tracker pesanan GrabFood GARP — selaras penuh dengan frontend web.

## Fitur (v2.0.0, rework 2026-10-26)
- **Tema Vercel/Linear**: near-black netral, accent indigo `#5E6AD2`, mono labels — sama dengan web.
- **Pesanan**: baris 1-line (avatar/nama+kode/badge/jam), search, **pagination 15/halaman**, hapus dengan konfirmasi.
- **Foto driver** dari `driver_photo` (S3 presigned) — di list, peta, dan detail; fallback inisial merchant.
- **Peta multi-track**: semua orderan aktif tampil bersamaan, garis rute dashed driver→tujuan, float card dengan ETA + caveat double order.
- **Detail**: rating driver, status message Grab, kendaraan/plat, tujuan, waktu WIB (`toWibTime`), caveat double-order (multi-stop).
- **Analitik**: Hari ini (WIB), Aktif, Total+Batal, Nilai total, Top merchants, bar chart 30 hari.
- **Polling 5 detik** selaras frontend.
- **Share-link Grab** (`ACTION_SEND`) langsung membuka form tempel link.

## Konfigurasi backend
Default base URL: `http://10.0.2.2:8000` (emulator → host). Ubah lewat ikon ⚙ di kanan atas.

## Build
```bash
cd android
gradle wrapper --gradle-version 8.7   # sekali, jika belum ada gradlew
./gradlew assembleDebug
```
APK: `app/build/outputs/apk/debug/app-debug.apk`

CI: `.github/workflows/android-build.yml` membangun debug + release APK di GitHub Actions (artifact `garp-debug-apk` / `garp-release-apk-unsigned`).

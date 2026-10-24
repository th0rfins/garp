# GARP Android

Aplikasi Android native (Kotlin + Jetpack Compose, Material 3, tema gelap Haulix-style) untuk tracker pesanan GrabFood GARP.

## Struktur
- `app/src/main/java/com/garp/tracker/`
  - `MainActivity.kt` — entry point; menerima share-link Grab (`ACTION_SEND`) dari app Grab/browser.
  - `data/Models.kt` — `Order`, `Stats` (kotlinx.serialization, cocok dengan shape JSON backend FastAPI).
  - `data/GarpApi.kt` — klien HTTP (Ktor): POST/GET/DELETE `/api/orders`, GET `/api/stats`.
  - `ui/` — `GarpApp` (nav bottom bar: Peta / Pesanan / Analitik), `MapScreen` (peta mini canvas + marker driver/tujuan), `OrdersScreen`, `OrderDetailScreen`, `AnalyticsScreen`, `GarpViewModel` (polling tiap 8s, selaras frontend).

## Konfigurasi backend
Default base URL: `http://10.0.2.2:8000` (emulator → host). Ubah di `data/GarpApi.kt` jika backend di LAN/server, mis. `http://192.168.1.10:8000`. Pastikan backend FastAPI GARP berjalan di port 8000.

## Build
```bash
cd android
gradle wrapper --gradle-version 8.7   # sekali, jika belum ada gradlew
./gradlew assembleDebug
```
APK: `app/build/outputs/apk/debug/app-debug.apk`

CI: `.github/workflows/android-build.yml` (root repo) membangun debug + release APK di GitHub Actions dan mengunggahnya sebagai artifact.

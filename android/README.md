# GARP Android (v3 — WebView)

APK ringan (WebView shell) di atas web app GARP — fitur web 100% sama dengan browser (peta OSM, poll bar, pagination, dst), tanpa maintain UI native terpisah.

## Fitur native
- **Share intent**: share link Grab (`app.grab.com/s/...` / `sharelocation.grab.com/o/...`) dari app lain → pilih GARP → link langsung otomatis ditambahkan & peta terbuka.
- **JS bridge** (`AndroidBridge`): web bisa ambil shared link & simpan server URL.
- Link eksternal (bukan domain server) dibuka di browser.
- Back button = history WebView.
- Server URL tersimpan (first-launch default `https://grab.projectpop.xyz`).

## Build
```bash
cd android
./gradlew assembleDebug
```
APK: `app/build/outputs/apk/debug/app-debug.apk`

CI: `.github/workflows/android-build.yml` (artifact `garp-debug-apk`).

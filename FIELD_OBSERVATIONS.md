# Observasi Lapangan — Share My Ride / GrabFood Tracking

> Data hasil capture langsung (live traffic monitoring) terhadap 3 pesanan nyata
> pada 25–26 Okt 2026, polling endpoint `bookingdetails` tiap 15 detik dari
> hampir awal sesi hingga pesanan selesai. Dokumen ini melengkapi
> `GRABFOOD_TRACKING_API.md` dengan temuan lapangan yang tidak terlihat dari
> kode saja.

---

## 1. Ringkasan Pesanan yang Diobservasi

| # | Tanggal | Merchant | Driver / Kendaraan | Durasi capture | Observasi | Error |
|---|---|---|---|---|---|---|
| 1 | 25 Okt 2026 | Dunia Sop Soetomo - Sidakaya | IMAM HASTIAN, Honda Vario 125, R 6403 ATB | snapshot awal + final | 2 | 0 |
| 2 | 26 Okt 2026 | Albiru Nasi Kuning Nasi Uduk - Tegalreja | ZAMZAMI, Honda VARIO 110, R 3385 KP | 8m 13s | 30+ | 0 |
| 3 | 26 Okt 2026 | Albiru Nasi Kuning Nasi Uduk - Tegalreja | WILI JANTO, Honda Beat 110, R 4202 BEB | 15m 11s | ~65 | 0 |

Semua pesanan: area Cilacap, tipe `BIKE`, 1 merchant + 1 dropoff.

---

## 2. Redirect Chain Short Link (hasil capture header asli)

```
GET https://app.grab.com/s/{short_code}
→ HTTP/2 302 (CloudFront Lambda@Edge)
   location: https://app.grab.com/open?screenType=GRABFOOD
       &shareOrderLink={token}
       &og_description=Track+my+order+from+{NAMA_MERCHANT}...   ← nama merchant bocor di header 302
       &deeplink_safe_id={hash}
       &redirection=https://sharelocation.grab.com/o/{token}
       &short_link_code={short_code}
   set-cookie: _gssid=... (30 menit), _gsvid=... (10 tahun)
```

Poin penting:

- **Short code ≠ token API.** `R7I5xw7b` di-pakai langsung ke endpoint
  `bookingdetails` → **HTTP 404**. Harus di-resolve dulu via 302 ke
  `shareOrderLink`. (Terbukti saat testing logger pertama kali.)
- Nama merchant sudah kelihatan di header `Location` 302, sebelum halaman
  web dibuka sama sekali.
- Halaman `sharelocation.grab.com/o/{token}` adalah SPA; og meta-nya generik
  (`"Track my current status"`), detail hanya ada di redirect step-1.

---

## 3. Keabsahan Endpoint — TANPA Auth

`GET https://api.grab.com/api/v1/safety/sharemyride/{token}/bookingdetails?fullData=true`

- Diterjemahkan HTTP 200 **tanpa Authorization header, tanpa cookie, tanpa
  token apapun** — cukup `Accept: application/json`.
- Berhasil via `curl` langsung (bukan dari konteks browser) → tidak ada
  proteksi CORS/auth yang memblokir polling server-side.
- Response berisi: nama lengkap driver, plat nomor, model kendaraan, rating,
  **posisi GPS driver real-time**, koordinat pickup + dropoff (alamat
  customer), foto driver (S3 presigned, `X-Amz-Expires=21600` = 6 jam), dan
  route ter-encode.
- Field `uc` tampak seperti counter (naik seiring waktu/polling: 8 → 35 →
  153 → 327 pada order berbeda).

---

## 4. Lifecycle Lengkap — Urutan Transisi Teramati

State machine aktual (order #3, capture dari awal sampai akhir):

```
bookingState : ORDER_IN_PREPARE ──▶ ORDER_EXECUTING ──▶ COMPLETED
messageStatus: "Kitchen's preparing your order"
               "Out for delivery!"
               "Look forward to delighting you again."   ← driver sampai di pintu
               "Thanks for ordering with us. Enjoy!"     ← final, lokasi di-reset
```

| Field | Saat jalan | Saat driver tiba | Final |
|---|---|---|---|
| `bookingState` | ORDER_EXECUTING | ORDER_EXECUTING | COMPLETED |
| `completeFlag` | 0 | 0 (!) | 0 (!) |
| `messageStatus.title` | "Out for delivery!" | "Look forward to delighting you again." | "Thanks for ordering with us. Enjoy!" |
| `driver.location` | koordinat nyata | titik dropoff | `{"latitude":0,"longitude":0}` |
| `route.ETAs` | turun bertahap | `[2]` → `[1]` | `null` |
| `route.encodedSteps` | polyline penuh | masih ada | `null` |

⚠️ **PENTING — stop condition yang reliable:**

- `completeFlag` **TIDAK reliable sendirian**: di order #3 dia tetap `0`
  bahkan setelah `bookingState == COMPLETED`. (Di order #2 `completeFlag=1`
  muncul duluan sebelum state COMPLETED.)
- `bookingState == "COMPLETED"` juga bisa telat ~15 detik dari pesan akhir.
- Kombinasi teraman (dipakai logger kami):
  ```python
  done = (bookingState == "COMPLETED"
          or completeFlag == 1
          or "Thanks for ordering" in messageStatus.title)
  ```
- Setelah COMPLETED: `sessionStatus` **tetap `ACTIVE`** (teramati bertahan
  minimal ±24 jam). Jangan pakai `sessionStatus != ACTIVE` sebagai sinyal
  "pesanan selesai".

---

## 5. Struktur ETAs — Dua Fase

- Sebelum pickup: `ETAs = [driver→merchant, merchant→customer]`
  (contoh nyata: `[4, 592]`, `[0, 539]`)
- Setelah pickup (ORDER_EXECUTING): jadi **satu elemen**
  `[merchant→customer]` (contoh: `[594]`)
- ETA bisa **naik sesaat** saat re-route / traffic: teramati 404→453 (order
  #2) dan 517→544, 243→257 (order #3). Jangan asumsikan monoton turun.
- `route.ETA` (singular) = unix timestamp estimasi tiba.

---

## 6. Route Encoding — `route.encodedSteps`

Bukan garis lurus antar titik — **route hasil routing engine Grab**
(road-snapped, mengikuti jalan sebenarnya). Hasil decode order #1:

- 81 titik; segmen rata-rata **25.5 m**; total jalur **2 040 m**
- Garis lurus pickup→dropoff hanya **1 013 m** → jalur menyimpang 2×,
  konsisten dengan jalan/gang sungguhan
- Titik pertama = posisi `driver.location` **persis (jarak 0.0 m)**
- Titik terakhir = koordinat dropoff **persis**

### ⚠️ Precision encoding: 1e6, bukan 1e5

`encodedSteps.latitude` / `.longitude` terlihat seperti Google encoded
polyline standar, tapi dekoder standar (precision 1e5) menghasilkan nilai
ngawur (−77.27, 1090.13). Precision yang benar adalah **1e6**:

```python
def decode_grab_polyline(s: str):
    vals, acc, shift, i = [], 0, 0, 0
    while i < len(s):
        b = ord(s[i]) - 63; i += 1
        acc |= (b & 0x1f) << shift; shift += 5
        if b < 0x20:
            acc += ~(acc >> 1) if acc & 1 else acc >> 1  # NB: koreksi akumulator, lihat catatan
            vals.append(acc)
            shift = 0
    return [v / 1e6 for v in vals]
```

> Catatan implementasi: versi yang terverifikasi memakai akumulator penuh
> (delta di-akumulasi lintas karakter lalu dibagi 1e6). Hasil verifikasi:
> titik-0 == `driver.location`, titik-N == koordinat `dropOff` persis.

### `route.traffic`

Array integer, panjangnya = jumlah segmen (order #1: 80 nilai untuk 81
titik). Nilai teramati hanya `0` dan `1` (indikasi lancar/padat per segmen).

---

## 7. Perilaku Polling & Data Driver

- `driver.location` berubah hampir tiap 15 detik saat ORDER_EXECUTING
  (GPS dari HP driver). Saat ORDER_IN_PREPARE driver bergerak sangat kecil
  (standby di merchant).
- Granularitas 15 detik cukup untuk rekonstruksi jalur yang menempel jalan.
- Saat COMPLETED, lokasi di-reset ke `(0,0)` — **validasi boleh: buang
  titik `{0,0}`** sebelum dipakai.
- Posisi driver juga di-refresh saat event `visibilitychange` browser
  (terlihat di bundle: `driverLocationInterval` + listener visibilitychange).

---

## 8. Privacy / Security Notes (hasil lapangan)

1. Short code 8 karakter, **tanpa auth**, membocorkan: nama lengkap driver,
   plat nomor, posisi GPS driver real-time, koordinat alamat customer, ETA,
   dan foto driver (presigned 6 jam).
2. Nama merchant bocor di header 302 (bisa di-enumerate tanpa membuka
   halaman).
3. `sessionStatus` tetap `ACTIVE` setelah pesanan selesai — data final
   (termasuk alamat customer) tetap bisa diambil lewat endpoint yang sama
   lama link short-nya masih resolve.
4. Polling tiap 15 detik selama 3 pesanan (±60 menit total) **tidak pernah
   kena 429/rate limit**.

---

## 9. File Bukti (capture mentah)

| File | Isi |
|---|---|
| `api_bookingdetails.json` / `_2.json` | snapshot order #1 saat diproses |
| `api_bookingdetails_completed.json` | order #1 final (COMPLETED) |
| `grab_share_log_vUSKSjiULtVebCqKcmva.txt/.jsonl` | order #2, 30+ observasi |
| `grab_share_log_vrKBbk1rfkhY8e1S5tYK.txt/.jsonl` | order #3, ~65 observasi full lifecycle |
| `api_final_*.json` | snapshot final tiap order |
| `route_order1_decoded.json` | hasil decode polyline (81 titik, verifikasi jalan nyata) |

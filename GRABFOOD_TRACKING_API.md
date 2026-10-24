# Dokumentasi API GrabFood Tracking (Real & Unmanipulated)

Dokumentasi ini disusun berdasarkan analisa langsung terhadap implementasi dan integrasi API tracking GrabFood (Share My Ride / Live Location Tracking) pada codebase project ini.

---

## 1. Ikhtisar & Arsitektur Flow

Grab menyediakan fitur **Share My Ride / Track Order** yang memungkinkan pengiriman lokasi dan status pesanan secara real-time melalui web view/link publik.

Secara umum, alur kerja tracking terdiri dari 2 tahap utama:
1. **Resolution Short Link / Token Extraction**: Mengubah URL berbagi aplikasi Grab (`https://app.grab.com/s/{code}`) menjadi **Share Location Token** (`klb5...`).
2. **Polling Data Booking**: Mengambil data detail pesanan & koordinat lokasi driver menggunakan token tracking secara berkala via HTTP REST API.

---

## 2. Token & URL Resolution

### Format URL
Terdapat dua jenis format URL yang digunakan oleh Grab:

1. **Short Link (Aplikasi Grab)**:
   - Format: `https://app.grab.com/s/{short_code}`
   - Contoh: `https://app.grab.com/s/B7bQ7vQr`
   - Sifat: Sementara. Mapping di edge server Grab dapat kedaluwarsa setelah pesanan selesai.

2. **Direct Share Location Link (Web/Browser)**:
   - Format: `https://sharelocation.grab.com/o/{token}`
   - Contoh: `https://sharelocation.grab.com/o/klb5oetmOvH6ZHzWOMdE`
   - URL ini berisi token unik (`token`) secara langsung.

### Mekanisme Resolusi Short Link (`app.grab.com/s/...`)
Apabila menerima short link `app.grab.com/s/{short_code}`, sistem melakukan resolusi bertahap:
1. **Pemeriksaan Direct Pattern / Query Parameter**:
   Mengecek apakah URL atau query parameter mengandung `shareOrderLink` atau pola token `/o/{token}`.
2. **Cache Lookup**:
   Mengecek memori lokal `_SHORT_CACHE` untuk mencari pemetaan `short_code` -> `token` yang pernah disimpan.
3. **HTTP Requests (Tanpa Redirect & Dengan Redirect)**:
   - **Metode 1**: `GET` request dengan `follow_redirects=False`. Membaca header HTTP `Location` untuk mengekstrak token `/o/{token}`.
   - **Metode 2**: `GET` request dengan `follow_redirects=True`. Mengambil URL akhir (misalnya `https://sharelocation.grab.com/open?shareOrderLink={token}`) atau memindai body HTML respons (max 6000 karakter).
   - **Metode 3**: `HEAD` request dengan `follow_redirects=False`. Beberapa edge node Grab hanya mengembalikan status `302 Redirect` pada request `HEAD`.

---

## 3. Spesifikasi API Endpoint

### Endpoint Detail Booking & Tracking
- **HTTP Method**: `GET`
- **Base URL**: `https://api.grab.com`
- **Path**: `/api/v1/safety/sharemyride/{token}/bookingdetails`
- **Full URL Example**:
  `https://api.grab.com/api/v1/safety/sharemyride/klb5oetmOvH6ZHzWOMdE/bookingdetails?fullData=true`

### Query Parameters
| Parameter | Tipe | Wajib | Nilai | Keterangan |
|---|---|---|---|---|
| `fullData` | `string` / `boolean` | Ya | `true` | Menginstruksikan API untuk mengembalikan data lengkap termasuk rujukan resto, rincian driver, dan status dapur. |

### HTTP Headers
Header berikut wajib/disarankan agar request tidak ditolak (CORS / bot detection):

```http
Accept: application/json
Origin: https://sharelocation.grab.com
Referer: https://sharelocation.grab.com/o/{token}
User-Agent: Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36
```

---

## 4. Struktur Respons JSON

Respons API mengembalikan objek JSON dengan struktur utama sebagai berikut:

### Objek Tingkat Atas (Root Level)
| Field | Tipe | Deskripsi |
|---|---|---|
| `pass` | `boolean` | Indikator keabsahan sesi/token (`true` jika valid). |
| `token` | `string` | Share token yang digunakan. |
| `sessionStatus` | `string` | Status sesi tracking (`ACTIVE`, `TERMINATED_MANUAL`, dll). |
| `driver` | `object` | Informasi pengemudi & kendaraan. |
| `booking` | `object` | Informasi status booking, tempat pickup, dan dropoff. |
| `route` | `object` | Estimasi waktu tiba (ETA) dan rute perjalanan. |
| `messageStatus` | `object` | Pesan status antarmuka pengguna dari Grab. |
| `additionalOrderDetail` | `object` | Informasi tambahan seperti flag penyelesaian pesanan. |

### Detail Field Utama

#### 1. `driver` (Informasi Driver & Lokasi)
```json
{
  "name": "Nama Driver",
  "vehicleModel": "Honda Vario 125",
  "vehiclePlateNumber": "R 5974 IP",
  "location": {
    "latitude": -7.70288,
    "longitude": 109.020845
  }
}
```
*Catatan*: Koordinat `latitude` dan `longitude` dapat bernilai `null` jika driver belum ditugaskan atau belum mengaktifkan lokasi.

#### 2. `booking` (Status Pesanan & Lokasi)
```json
{
  "bookingCode": "A-9QPPCSCGXX3DAV",
  "bookingState": "ORDER_IN_PREPARE",
  "pickup": {
    "keywords": "Nama Restoran / Merchant"
  },
  "dropOff": {
    "keywords": "Alamat / Lokasi Tujuan Pengiriman"
  }
}
```

#### 3. `route` (Estimasi Waktu Tiba / ETA)
```json
{
  "ETAs": [251, 408]
}
```
*Keterangan*: Array nilai integer yang mewakili estimasi detik perjalanan ke titik penjemputan/pengantaran.

#### 4. `messageStatus` (Pesan Status)
```json
{
  "title": "Kitchen's preparing your order",
  "processMsg": {
    "message": "We'll let you know when it's out for delivery."
  }
}
```

#### 5. `additionalOrderDetail`
```json
{
  "isPendingDelivery": false,
  "completeFlag": 0
}
```

---

## 5. Status Pesanan (Booking States) & Kondisi Terminal

Sistem tracking membedakan antara sesi yang masih aktif dan sesi yang telah berakhir (**Terminal State**).

### Status Aktif (Active States)
- Sesi dalam `sessionStatus` == `"ACTIVE"`.
- Status `bookingState` contohnya:
  - `ORDER_IN_PREPARE`: Makanan sedang disiapkan di dapur/merchant.
  - `DRIVER_ALLOCATED` / `ON_THE_WAY`: Driver dalam perjalanan.

### Kondisi Terminal (Sesi Berakhir)
Tracking akan dihentikan secara otomatis jika salah satu kondisi berikut terpenuhi:
1. `sessionStatus` bernilai selain `"ACTIVE"` (misalnya `TERMINATED_MANUAL`, `EXPIRED`).
2. `bookingState` mengandung salah satu substring berikut:
   - `COMPLETE` / `COMPLETED`
   - `CANCEL` / `CANCELLED`
   - `DELIVER` / `DELIVERED`
   - `EXPIRED`
   - `TERMINAT` / `TERMINATED`
3. `additionalOrderDetail.completeFlag` bernilai `1`.

---

## 6. Penanganan Error & Kode Status HTTP

| HTTP Status / Error | Penyebab | Tindakan Sistem |
|---|---|---|
| `200 OK` + `pass: true` | Sesi aktif dan data berhasil diambil. | Lanjutkan polling. |
| `200 OK` + `pass: false` / Kosong | Token tidak valid atau sesi telah berakhir. | Lempar exception `GrabGone`, hentikan tracking. |
| `404 Not Found` | Token tidak ditemukan atau link sudah expired. | Lempar exception `GrabGone`, hentikan tracking. |
| `429 Too Many Requests` / Rate Limit | Polling terlalu sering. | Terapkan exponential backoff atau hormati header `Retry-After`. |
| `5xx / Network Error` | Masalah jaringan / server Grab temporary down. | Lakukan retry hingga batas maksimum (misal 30x berturut-turut). |

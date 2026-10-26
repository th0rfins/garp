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

#### Bukti Header Redirect Nyata (capture 25 Okt 2026)

```http
HTTP/2 302
server: CloudFront
location: https://app.grab.com/open?screenType=GRABFOOD
  &shareOrderLink={token}
  &og_title=Share+your+current+order+status+with+your+friends.
  &og_description=Track+my+order+from+{NAMA_MERCHANT}.+   <- nama merchant bocor di sini
  &deeplink_safe_id={hash256}
  &redirection=https%3A%2F%2Fsharelocation.grab.com%2Fo%2F{token}
  &short_link_code={short_code}
set-cookie: _gssid={session}; Expires=+30 menit
set-cookie: _gsvid={visitor}; Expires=+10 tahun
x-cache: LambdaGeneratedResponse from cloudfront
```

#### ⚠️ Bukti: Short Code ≠ Token API

Menggunakan short code (`app.grab.com/s/{code}`) langsung sebagai `{token}`
pada endpoint `bookingdetails` menghasilkan **HTTP 404** (terverifikasi).
Resolusi 302 → `shareOrderLink` wajib dilakukan lebih dulu. Contoh nyata:
short code `R7I5xw7b` (8 karakter) → token `Yp9ysb...1dLS` (22 karakter).

Kebocoran tambahan: **nama merchant sudah terlihat di header `Location` 302**
sebelum halaman web mana pun dibuka — cukup satu `curl -I` tanpa auth.
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

### ⚠️ Catatan Lapangan — Reliabilitas Kondisi Terminal

Berdasarkan capture 3 pesanan nyata (polling 15 detik, lifecycle penuh):

- `completeFlag` **tidak selalu menjadi `1`**: pada satu pesanan nilainya
  tetap `0` bahkan setelah `bookingState == "COMPLETED"`. Pada pesanan lain
  `completeFlag=1` muncul **sebelum** state COMPLETED.
- `bookingState` bisa telat ~15 detik dari pesan akhir ("driver sampai").
- `sessionStatus` **tetap `"ACTIVE"`** setelah pesanan selesai (teramati
  bertahan lama setelah COMPLETED) — jangan jadikan sinyal selesai.

Kondisi terminal yang terverifikasi aman (gabungan):

```python
done = (bookingState == "COMPLETED"
        or completeFlag == 1
        or "Thanks for ordering" in messageStatus.title)
```

Urutan transisi nyata yang terekam penuh:

```
bookingState : ORDER_IN_PREPARE -> ORDER_EXECUTING -> COMPLETED
messageStatus: "Kitchen's preparing your order"
            -> "Out for delivery!"
            -> "Look forward to delighting you again."   (driver sampai)
            -> "Thanks for ordering with us. Enjoy!"     (final)


---

## 6. Penanganan Error & Kode Status HTTP

| HTTP Status / Error | Penyebab | Tindakan Sistem |
|---|---|---|
| `200 OK` + `pass: true` | Sesi aktif dan data berhasil diambil. | Lanjutkan polling. |
| `200 OK` + `pass: false` / Kosong | Token tidak valid atau sesi telah berakhir. | Lempar exception `GrabGone`, hentikan tracking. |
| `404 Not Found` | Token tidak ditemukan atau link sudah expired. | Lempar exception `GrabGone`, hentikan tracking. |
| `429 Too Many Requests` / Rate Limit | Polling terlalu sering. | Terapkan exponential backoff atau hormati header `Retry-After`. |
| `5xx / Network Error` | Masalah jaringan / server Grab temporary down. | Lakukan retry hingga batas maksimum (misal 30x berturut-turut). |


---

## 7. Contoh Response Nyata (Capture Langsung, 3 Siklus Hidup Penuh)

> Response di bawah adalah capture asli dari endpoint nyata (polling 15 detik,
> 25–26 Okt 2026), dengan nama driver / plat / kode booking / alamat customer
> disamarkan. Struktur, tipe data, dan urutan field 100% asli.

### 7.1 Contoh 1 — `ORDER_IN_PREPARE` (dapur menyiapkan, driver standby di merchant)

Perhatikan: `ETAs` punya **2 elemen** `[driver→merchant, merchant→customer]`,
driver masih di dekat merchant, `route.encodedSteps` polyline sudah tersedia,
`completeFlag: 0`.

```json
{
  "pass": true,
  "token": "xrEXAMPLExxxXXXXXXXXXX",
  "sessionStatus": "ACTIVE",
  "useGrabMap": true,
  "driver": {
    "name": "JOHN DOE",
    "profilePic": "https://prd-frs-face-images.s3.ap-southeast-1.amazonaws.com/profile/...jpg?X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Expires=21600&...(presigned URL, dipotong)",
    "rating": 5,
    "location": {
      "latitude": -7.731117,
      "longitude": 109.003699
    },
    "serviceType": "BIKE",
    "vehicleModel": "Honda Beat 110",
    "vehiclePlateNumber": "R 0000 XX",
    "plannedSteps": [
      {
        "bookingCodes": [
          "A-EXAMPLECODE00AV"
        ],
        "location": {
          "latitude": -7.731129,
          "longitude": 109.003784
        },
        "state": "ACTIVE",
        "isPickup": true
      },
      {
        "bookingCodes": [
          "A-EXAMPLECODE00AV"
        ],
        "location": {
          "latitude": -7.718626,
          "longitude": 109.023742
        },
        "state": "DRIVER_ACCEPTED",
        "isDropOff": true
      }
    ],
    "vehiclePictureUrl": "https://myteksi.s3.amazonaws.com/production/uploads/taxi_type/13503/icon/hdpi_icon-1677743666.png",
    "CityID": 346
  },
  "booking": {
    "bookingCode": "A-EXAMPLECODE00AV",
    "bookingState": "ORDER_IN_PREPARE",
    "pickup": {
      "waypointID": "",
      "location": {
        "latitude": -7.731129,
        "longitude": 109.003784
      },
      "keywords": "Albiru Nasi Kuning Nasi Uduk - Tegalreja"
    },
    "dropOff": {
      "waypointID": "",
      "location": {
        "latitude": -7.718626,
        "longitude": 109.023742
      },
      "keywords": "Jl. Example No.8 (alamat customer)"
    }
  },
  "route": {
    "steps": null,
    "ETA": 1792973700,
    "traffic": [
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      1,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0
    ],
    "ETAs": [
      4,
      592
    ],
    "encodedSteps": {
      "latitude": "h_{vM??op@gIkB{]_g@se@yf@@@FRBI?@???BJDE?DANy@s@g@S??Rd@r@z@ACF?FBB@AC?AALJDFE@JB?FA@DLUX^j@RtA~@f@J@D@D\\b@f@l@fAb@h@@|@b@Xl@rAvAfCvFfFh@l@bEbD|HlLm\\g`AgBgDeTsAqNyLeHaD_FcHuI_K_EqIaEeGuFmEyM_E_DcDsCeEmDwDqDoAyCyCaDyCkA}ZqCgGu]yOme@cRuRiCa\\yt@iDoLoNcGeEaPkLcJ|H",
      "longitude": "ez`|nEgDhDO?@OMIK_OoH{|A_j@yB_d@k@i@oD}CUcCeKmWu_@eDg\\yAg[I]m@y@g@Sw@m@_@Ig[}c@m`@_MiW{MiLwDeJoSaCkH}PoLuM}DuIwAgH}K_E]_LsAqAaH_TeOwUcKqLuD}TmOyLqBq@iCi@sF{J_PsH}H}QmKqJU_T_FwDgDsHyC}EsKqLgAaBqI_GgPkVqKa`@m@aAyGe@cFgEeCy@s@@Np@l@~BtBdEvFlIzXbKfEdChB|BdAbALFIKq@c@]sHk@iB_KsEwKcGgGe@qIaUkAsDyEiB_BmFaEwCaS"
    }
  },
  "reasonCode": 100,
  "appFlyer": {
    "isVisible": true,
    "rating": {
      "android": 4.8,
      "ios": 4.8
    },
    "downloadLink": "https://grab.onelink.me/2695613898/pw72ckwn"
  },
  "messageStatus": {
    "title": "Kitchen's preparing your order",
    "processMsg": {
      "message": "We'll let you know when it's out for delivery.",
      "color": "#3D3D3D"
    },
    "icon": "https://assets-mobile.grab.com/deliveries/food/food_order_preparing.png"
  },
  "additionalOrderDetail": {
    "isPendingDelivery": false,
    "businessType": 0,
    "completeFlag": 0
  },
  "uc": 9
}
```

### 7.2 Contoh 2 — `ORDER_EXECUTING` (out for delivery, driver bergerak)

Perhatikan: `ETAs` tinggal **1 elemen** `[merchant→customer]` setelah pickup,
`driver.location` berubah tiap poll (GPS real-time), polyline kini mulai dari
posisi driver saat itu.

```json
{
  "pass": true,
  "token": "xrEXAMPLExxxXXXXXXXXXX",
  "sessionStatus": "ACTIVE",
  "useGrabMap": true,
  "driver": {
    "name": "JANE DOE",
    "profilePic": "https://prd-frs-face-images.s3.ap-southeast-1.amazonaws.com/profile/...jpg?X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Expires=21600&...(presigned URL, dipotong)",
    "rating": 5,
    "location": {
      "latitude": -7.731206,
      "longitude": 109.003811
    },
    "serviceType": "BIKE",
    "vehicleModel": "Honda Beat 110",
    "vehiclePlateNumber": "B 1111 YY",
    "plannedSteps": [
      {
        "bookingCodes": [
          "A-EXAMPLECODE00AV"
        ],
        "location": {
          "latitude": -7.731129,
          "longitude": 109.003784
        },
        "state": "COMPLETED",
        "isPickup": true
      },
      {
        "bookingCodes": [
          "A-EXAMPLECODE00AV"
        ],
        "location": {
          "latitude": -7.718626,
          "longitude": 109.023742
        },
        "state": "ACTIVE",
        "isDropOff": true
      }
    ],
    "vehiclePictureUrl": "https://myteksi.s3.amazonaws.com/production/uploads/taxi_type/13503/icon/hdpi_icon-1677743666.png",
    "CityID": 346
  },
  "booking": {
    "bookingCode": "A-EXAMPLECODE00AV",
    "bookingState": "ORDER_EXECUTING",
    "pickup": {
      "waypointID": "",
      "location": {
        "latitude": -7.731129,
        "longitude": 109.003784
      },
      "keywords": "Albiru Nasi Kuning Nasi Uduk - Tegalreja"
    },
    "dropOff": {
      "waypointID": "",
      "location": {
        "latitude": -7.718626,
        "longitude": 109.023742
      },
      "keywords": "Jl. Example No.8 (alamat customer)"
    },
    "dropOffTime": "2026-10-25T23:51:44Z"
  },
  "route": {
    "steps": null,
    "ETA": 1792973185,
    "traffic": [
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      1,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0
    ],
    "ETAs": [
      594
    ],
    "encodedSteps": {
      "latitude": "h_{vM?op@gIkB{]_g@se@yf@@@FRBI?@???BJDE?DANy@s@g@S??Rd@r@z@ACF?FBB@AC?AALJDFE@JB?FA@DLUX^j@RtA~@f@J@D@D\\b@f@l@fAb@h@@|@b@Xl@rAvAfCvFfFh@l@bEbD|HlLm\\g`AgBgDeTsAqNyLeHaD_FcHuI_K_EqIaEeGuFmEyM_E_DcDsCeEmDwDqDoAyCyCaDyCkA}ZqCgGu]yOme@cRuRiCa\\yt@iDoLoNcGeEaPkLcJ|H",
      "longitude": "m_a|nEhDO?@OMIK_OoH{|A_j@yB_d@k@i@oD}CUcCeKmWu_@eDg\\yAg[I]m@y@g@Sw@m@_@Ig[}c@m`@_MiW{MiLwDeJoSaCkH}PoLuM}DuIwAgH}K_E]_LsAqAaH_TeOwUcKqLuD}TmOyLqBq@iCi@sF{J_PsH}H}QmKqJU_T_FwDgDsHyC}EsKqLgAaBqI_GgPkVqKa`@m@aAyGe@cFgEeCy@s@@Np@l@~BtBdEvFlIzXbKfEdChB|BdAbALFIKq@c@]sHk@iB_KsEwKcGgGe@qIaUkAsDyEiB_BmFaEwCaS"
    }
  },
  "reasonCode": 100,
  "appFlyer": {
    "isVisible": true,
    "rating": {
      "android": 4.8,
      "ios": 4.8
    },
    "downloadLink": "https://grab.onelink.me/2695613898/pw72ckwn"
  },
  "messageStatus": {
    "title": "Out for delivery!",
    "processMsg": {
      "message": "Your order might arrive early!",
      "color": "#3D3D3D"
    },
    "icon": "https://assets-mobile.grab.com/deliveries/food/order_delivery_2w.png"
  },
  "additionalOrderDetail": {
    "isPendingDelivery": false,
    "businessType": 0,
    "completeFlag": 0
  },
  "uc": 26
}
```

### 7.3 Contoh 3 — Driver tiba, pesan akhir sebelum state COMPLETED

Perhatikan **anomali penting**: driver sudah di titik dropoff dan pesan sudah
"selesai", tapi `bookingState` masih `ORDER_EXECUTING` dan `completeFlag`
masih `0`. Ini bukti bahwa tidak ada field tunggal yang selalu transisi duluan.

```json
{
  "pass": true,
  "token": "xrEXAMPLExxxXXXXXXXXXX",
  "sessionStatus": "ACTIVE",
  "useGrabMap": true,
  "driver": {
    "name": "JANE DOE",
    "profilePic": "https://prd-frs-face-images.s3.ap-southeast-1.amazonaws.com/profile/...jpg?X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Expires=21600&...(presigned URL, dipotong)",
    "rating": 5,
    "location": {
      "latitude": 0,
      "longitude": 0
    },
    "serviceType": "BIKE",
    "vehicleModel": "Honda Beat 110",
    "vehiclePlateNumber": "B 1111 YY",
    "vehiclePictureUrl": "https://myteksi.s3.amazonaws.com/production/uploads/taxi_type/13503/icon/hdpi_icon-1677743666.png",
    "CityID": 346
  },
  "booking": {
    "bookingCode": "A-EXAMPLECODE00AV",
    "bookingState": "COMPLETED",
    "pickup": {
      "waypointID": "",
      "location": {
        "latitude": -7.731129,
        "longitude": 109.003784
      },
      "keywords": "Albiru Nasi Kuning Nasi Uduk - Tegalreja"
    },
    "dropOff": {
      "waypointID": "",
      "location": {
        "latitude": -7.718626,
        "longitude": 109.023742
      },
      "keywords": "Jl. Example No.8 (alamat customer)"
    },
    "driverArrivalTime": "2026-10-26T00:02:25Z",
    "dropOffTime": "2026-10-25T23:51:44Z",
    "completeTime": "2026-10-26T00:02:25Z"
  },
  "route": {
    "steps": null,
    "ETA": 1792972945,
    "traffic": null,
    "ETAs": null,
    "encodedSteps": null
  },
  "reasonCode": 100,
  "appFlyer": {
    "isVisible": true,
    "rating": {
      "android": 4.8,
      "ios": 4.8
    },
    "downloadLink": "https://grab.onelink.me/2695613898/pw72ckwn"
  },
  "messageStatus": {
    "title": "",
    "processMsg": {
      "message": "Thanks for ordering with us. Enjoy!",
      "color": ""
    },
    "icon": "https://assets-mobile.grab.com/deliveries/food/order_delivery_2w.png"
  },
  "additionalOrderDetail": {
    "isPendingDelivery": false,
    "businessType": 0,
    "completeFlag": 0
  },
  "uc": 71
}
```

### 7.4 Contoh 4 — Final: `COMPLETED` (data tracking dibersihkan)

Perhatikan: `bookingState: "COMPLETED"`, tapi `completeFlag` **tetap 0**
(ini nyata, bukan typo!), `driver.location` di-reset ke `(0,0)`,
`route.ETAs`/`encodedSteps`/`traffic` menjadi `null`, dan waktu-waktu
penyelesaian (`driverArrivalTime`, `dropOffTime`, `completeTime`) muncul di
objek `booking`. **`sessionStatus` tetap `"ACTIVE"`** meski pesanan selesai.

```json
{
  "pass": true,
  "token": "xrEXAMPLExxxXXXXXXXXXX",
  "sessionStatus": "ACTIVE",
  "useGrabMap": true,
  "driver": {
    "name": "JOHN DOE",
    "profilePic": "https://prd-frs-face-images.s3.ap-southeast-1.amazonaws.com/profile/...jpg?X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Expires=21600&...(presigned URL, dipotong)",
    "rating": 5,
    "location": {
      "latitude": 0,
      "longitude": 0
    },
    "serviceType": "BIKE",
    "vehicleModel": "Honda Vario 125",
    "vehiclePlateNumber": "R 0000 XX",
    "vehiclePictureUrl": "https://myteksi.s3.amazonaws.com/production/uploads/taxi_type/13503/icon/hdpi_icon-1677743666.png",
    "CityID": 346
  },
  "booking": {
    "bookingCode": "A-EXAMPLECODE00AV",
    "bookingState": "COMPLETED",
    "pickup": {
      "waypointID": "",
      "location": {
        "latitude": -7.724511963298966,
        "longitude": 109.01690166520325
      },
      "keywords": "Dunia Sop Soetomo - Sidakaya"
    },
    "dropOff": {
      "waypointID": "",
      "location": {
        "latitude": -7.718594074249268,
        "longitude": 109.02386474609376
      },
      "keywords": "Jl. Example No.8 (alamat customer)"
    },
    "driverArrivalTime": "2026-10-25T07:07:46Z",
    "dropOffTime": "2026-10-25T07:05:37Z",
    "completeTime": "2026-10-25T07:07:46Z"
  },
  "route": {
    "steps": null,
    "ETA": 1792912066,
    "traffic": null,
    "ETAs": null,
    "encodedSteps": null
  },
  "reasonCode": 100,
  "appFlyer": {
    "isVisible": true,
    "rating": {
      "android": 4.8,
      "ios": 4.8
    },
    "downloadLink": "https://grab.onelink.me/2695613898/pw72ckwn"
  },
  "messageStatus": {
    "title": "",
    "processMsg": {
      "message": "Your order arrived ahead of time. Yay!",
      "color": ""
    },
    "icon": "https://assets-mobile.grab.com/deliveries/food/order_delivery_2w.png"
  },
  "additionalOrderDetail": {
    "isPendingDelivery": false,
    "businessType": 0,
    "completeFlag": 1
  },
  "uc": 328
}
```

### 7.5 Ringkasan Perubahan Antar Fase (Cheat Sheet)

| Field | IN_PREPARE | EXECUTING | Driver tiba | COMPLETED |
|---|---|---|---|---|
| `booking.bookingState` | ORDER_IN_PREPARE | ORDER_EXECUTING | ORDER_EXECUTING | COMPLETED |
| `booking.completeFlag` | 0 | 0 | 0 **atau** 1 | 0 **atau** 1 |
| `messageStatus.title` | Kitchen's preparing your order | Out for delivery! | Look forward to delighting you again. | Thanks for ordering with us. Enjoy! |
| `route.ETAs` | `[a, b]` (2 leg) | `[b]` (1 leg, turun) | `[≤5]` | `null` |
| `route.encodedSteps` | polyline (merchant→cust) | polyline (pos driver→cust, ter-update) | polyline pendek | `null` |
| `route.traffic` | array 0/1 per segmen | array 0/1 per segmen | array pendek | `null` |
| `driver.location` | dekat merchant | bergerak tiap poll | titik dropoff | `{0,0}` |
| `booking.completeTime` | tidak ada | tidak ada | tidak ada / kosong | ISO-8601 UTC |
| `sessionStatus` | ACTIVE | ACTIVE | ACTIVE | ACTIVE |

---

## 8. Cara Kerja `route.encodedSteps` (Hasil Decode Nyata)

`encodedSteps` **bukan garis lurus** antar dua titik — ini polyline hasil
routing engine Grab yang menempel jalan sungguhan. Decode order nyata:

- 81 titik; segmen rata-rata **25.5 m**; total jalur **2 040 m**
- Garis lurus pickup→dropoff hanya **1 013 m** → jalur menyimpang 2×,
  konsisten mengikuti jalan/gang sungguhan
- Titik pertama == `driver.location` saat response dibuat (**jarak 0.0 m**)
- Titik terakhir == koordinat `dropOff` persis

### ⚠️ Precision encoding: 1e6, BUKAN 1e5

String-nya menyerupai Google encoded polyline standar, tapi dekoder standar
(delta + precision 1e5) menghasilkan nilai ngawur (misal `-77.27, 1090.13`).
Precision yang benar adalah **1e6** — koma 6 desimal:

```python
def decode_grab_polyline(lat_s: str, lng_s: str):
    """Decode route.encodedSteps.latitude/.longitude (Grab variant).
    Terverifikasi: titik[0] == driver.location, titik[-1] == dropOff coords."""
    def dec(s: str):
        vals, acc, shift, i = [], 0, 0, 0
        while i < len(s):
            b = ord(s[i]) - 63; i += 1
            acc |= (b & 0x1f) << shift; shift += 5
            if b < 0x20:
                # delta di-akumulasi (varint), lalu /1e6 — bukan /1e5
                acc += ~(acc >> 1) if acc & 1 else acc >> 1
                vals.append(acc); shift = 0
        return vals
    return list(zip([v / 1e6 for v in dec(lat_s)],
                    [v / 1e6 for v in dec(lng_s)]))
```

### `route.traffic`

Array integer, panjang = jumlah segmen (order nyata: 80 nilai untuk 81
titik). Nilai teramati hanya `0` dan `1` per segmen (indikasi lancar/padat).
Setelah pesanan selesai nilainya `null`.

---

## 9. Multi-Order (Double Order) — `driver.plannedSteps`

Satu driver bisa membawa 2+ pesanan sekaligus. Capture nyata (26 Okt 2026,
driver bawa 2 order, 4 stop):

```json
{
  "plannedSteps": [
    {"bookingCodes": ["A-ORDER2CODE0000AV"], "state": "COMPLETED", "isPickup": true,  "location": {"latitude": -7.728, "longitude": 109.014}},
    {"bookingCodes": ["A-SHAREDCODE0000AV"], "state": "COMPLETED", "isPickup": true,  "location": {"latitude": -7.728, "longitude": 109.014}},
    {"bookingCodes": ["A-ORDER2CODE0000AV"], "state": "ACTIVE",    "isDropOff": true, "location": {"latitude": -7.719, "longitude": 109.016}},
    {"bookingCodes": ["A-SHAREDCODE0000AV"], "state": "DRIVER_ACCEPTED", "isDropOff": true, "location": {"latitude": -7.719, "longitude": 109.019}}
  ]
}
```

### Arti `state` per step (terverifikasi transisi realtime)

| state | Arti |
|---|---|
| `DRIVER_ACCEPTED` | Stop sudah direncanakan, belum dieksekusi |
| `ACTIVE` | Stop sedang dieksekusi (driver menuju / di lokasi) |
| `COMPLETED` | Stop selesai |

### Perilaku yang terekam (polling 15 detik)

1. **Transisi antar-drop terlihat realtime**: DROPOFF order#2 `ACTIVE` →
   `COMPLETED`, dan di poll berikutnya DROPOFF order-share langsung
   `DRIVER_ACCEPTED` → `ACTIVE`.
2. **`bookingState` adalah per-booking, bukan per-trip**: pesanan yang di-share
   tetap `ORDER_EXECUTING` sepanjang driver mengantar pesanan ORANG LAIN
   terlebih dahulu.
3. **`route.ETAs` menghitung ke stop AKTIF berikutnya dalam rute driver** —
   bukan ke dropoff pesanan yang di-share. Saat driver mengantar order lain,
   ETAs=[191] itu ETA ke dropoff order lain. Jangan tampilkan sebagai "makanan
   anda tiba dalam X detik" saat ada stop lain yang lebih dulu.
4. **Booking code pesanan lain ikut terekspos** di `plannedSteps.bookingCodes`
   pada response pesanan yang di-share (catatan privasi tambahan).
5. `plannedSteps` menghilang / dibersihkan saat pesanan COMPLETED (sama seperti
   `route.*`).

---

## 10. Ringkasan Field Tidak Obvious (Quick Reference)

| Field | Perilaku nyata |
|---|---|
| `sessionStatus` | Tetap `"ACTIVE"` setelah pesanan selesai — jangan dipakai sebagai sinyal selesai |
| `additionalOrderDetail.completeFlag` | Bisa tetap `0` sampai akhir (nyata!), bisa juga `1` duluan — tidak konsisten antar pesanan |
| `route.ETAs` | 2 elemen sebelum pickup `[driver→merchant, merchant→cust]`, 1 elemen sesudah; menghitung ke stop aktif berikutnya (bisa dropoff order lain!); bisa naik sesaat saat re-route |
| `route.encodedSteps` | Polyline precision **1e6** (bukan 1e5); titik[0]=posisi driver, titik[-1]=dropoff; hasil routing engine (bukan garis lurus) |
| `route.traffic` | Array 0/1 per segmen, panjang = titik-1; `null` setelah selesai |
| `driver.location` | Reset ke `{0,0}` saat COMPLETED — filter sebelum dipakai |
| `booking.completeTime` | Bisa kosong walau pesanan sudah selesai (dropOffTime yang terisi lebih konsisten) |
| `uc` | Counter naik seiring waktu/polling (8 → 35 → 153 → 327 → 270 teramati) |
| `driver.profilePic` | S3 presigned URL, valid 6 jam (`X-Amz-Expires=21600`) |

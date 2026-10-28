# ios-android-mirror

Tampilkan dan kendalikan layar Android dari Safari di iPhone lama (iOS 12+),
hanya dengan dua perangkat. Tanpa PC dan tanpa server pihak ketiga.

Android menjalankan server kecil di dalam aplikasi. iPhone membuka halaman web
dari Android lewat hotspot atau Wi-Fi yang sama.

## Cara pakai
1. Build dan pasang aplikasi (Android Studio, buka folder ini, tekan Run).
2. Buka aplikasi, lalu ketuk **Aktifkan Aksesibilitas** dan nyalakan "Mirror".
3. Ketuk **Mulai berbagi layar** dan izinkan.
4. Nyalakan hotspot Android, sambungkan iPhone ke hotspot itu.
5. Buka alamat yang tampil di aplikasi (berisi token acak) di Safari iPhone.

## Catatan Android 13
Jika aplikasi dipasang dari file APK, opsi Aksesibilitas bisa abu-abu.
Buka Info Aplikasi, ketuk titik tiga, lalu pilih **Izinkan setelan terbatas**.

## Keamanan
Lalu lintas tidak dienkripsi (HTTP/WS) dan dilindungi token acak per sesi.
Pakai hanya di hotspot pribadi. Jangan dibuka ke internet.

## Rencana
- [x] Streaming layar (JPEG lewat WebSocket)
- [x] Tap, swipe, long press
- [x] Back / Home / Recents, input teks
- [ ] Setelan kualitas dan FPS
- [ ] Rotasi layar
- [ ] Scroll yang lebih halus (gerakan multi-titik)

Lisensi: MIT

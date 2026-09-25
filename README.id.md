<p align="center">
  <img src="app/src/main/res/drawable-nodpi/brand_banner.jpg" alt="The Great Drive" width="640">
</p>

<h1 align="center">The Great Drive</h1>
<p align="center">Simpan yang kamu suka — ke ponsel, Google Drive, atau keduanya.</p>
<p align="center"><b>Android 10+</b> · <b>Unduhan di perangkat</b> · <b>Bahasa Indonesia / English</b></p>
<p align="center"><a href="README.md">English</a> · Bahasa Indonesia</p>

The Great Drive adalah aplikasi Android untuk mengunduh media, menata berkas, dan mengunggahnya ke Google Drive. Bagikan tautan dari aplikasi lain atau tempel langsung, pilih tujuan penyimpanan, lalu pantau antrean. Pilihan terakhirmu tersimpan untuk unduhan berikutnya.

Ekstraksi media, unduhan torrent, penggabungan berkas, dan unggahan berjalan di ponsel. Kamu tidak perlu menjalankan server terpisah.

## Apa yang bisa dilakukan?

| Unduh | Atur | Kendalikan |
| --- | --- | --- |
| Video, foto, audio, dan daftar putar yang didukung | Simpan ke Lokal, Google Drive, atau keduanya | Simpan pilihan kualitas, tujuan, dan filter |
| Story Instagram dengan gambar mini dan pilihan beberapa item | Jelajahi, cari, ubah nama, beri bintang, dan pindahkan berkas Drive | Jeda antrean, batalkan, dan ulangi tugas gagal |
| Impor Instagram JSON/ZIP dengan kategori dan filter tanggal | Gabungkan video dan audio dengan FFmpeg | Baca hasil dan bagikan log diagnostik |
| Tautan magnet dan berkas `.torrent` | Pindahkan folder lama melalui salinan terverifikasi | Cadangkan riwayat dan alamat situs tersimpan |

Baca [daftar fitur lengkap](docs/i18n/id/FEATURES.md) dan [catatan versi](docs/i18n/id/CHANGELOG.md).

## Mulai mengunduh

1. Pasang APK bertanda tangan dari artefak alur kerja **Build The Great Drive APK** di repositorimu. Gunakan kunci penandatanganan yang sama saat memperbarui instalasi lama.
2. Buka **Unduh**, tempel tautan, atau bagikan tautan/berkas ke The Great Drive melalui Android.
3. Pilih **Lokal**, **Drive**, atau **Keduanya**, lalu tentukan kualitas. Kosongkan **Maksimum item** untuk tanpa batas jumlah item.
4. Jika diperlukan, masuk ke situs sumber melalui **Akun**. Hubungkan akun Google untuk tujuan Drive.
5. Mulai unduhan, lalu buka **Aktivitas** untuk memantau progres atau melihat hasil.

Buka **Opsi → Bahasa aplikasi** untuk memilih Bahasa Indonesia atau English. Pilihan tersimpan dan berlaku pada halaman, dialog, status aplikasi, serta informasi luring. Isi situs dan diagnostik mentah dari penyedia tetap memakai bahasa aslinya.

## Menu yang teratur

| Menu | Isi |
| --- | --- |
| **Unduh** | Tautan, tujuan, kualitas, batas item, dan pemilih Story |
| **Opsi** | Bahasa, pengaturan unduhan, metadata, tanggal, dan ID folder Drive |
| **Akun** | Sesi situs, koneksi Google Drive, cadangan, dan administrasi perangkat |
| **Berkas** | Impor Instagram, berkas lokal, torrent, penggabungan media, dan pemindahan folder |
| **Aktivitas** | Kontrol antrean, hasil terbaru, tautan berkas, dan log kesalahan |
| **Info** | Tentang, Fitur, dan Catatan versi yang tersedia luring |

Media lokal tersusun di `The Great Drive/situs/nama-akun/jenis/` pada koleksi Movies, Pictures, atau Music Android. Berkas lain masuk ke Downloads. Drive memakai struktur yang sama di bawah `The Great Drive` pada My Drive atau folder induk pilihanmu.

Memperbarui versi lama? **Berkas → Pindahkan folder Lokal / Google Drive** dapat menyalin berkas dari folder lama `TGDrive`. Pilih simpan sumber atau hapus setelah verifikasi. Sumber Lokal dihapus setelah ukuran dan SHA-256 cocok; sumber Drive masuk ke Sampah setelah ukuran dan MD5 cocok. Folder lama yang kosong tetap ada. Pemindahan Lokal hanya mencakup berkas milik instalasi aplikasi saat ini; dokumen Google dan pintasan dilewati.

## Bangun APK

Repositori menyertakan alur kerja rilis GitHub Actions. Unggah **isi direktori proyek** sehingga `.github/`, `app/`, dan `version.properties` berada di akar repositori.

1. Atur rahasia penandatanganan rilis yang sudah digunakan melalui [panduan penandatanganan](docs/SIGNING.md).
2. Atur Google Drive melalui [panduan Google Drive](docs/GOOGLE_DRIVE_SETUP.md).
3. Jalankan **Actions → Build The Great Drive APK**.
4. Unduh APK dan checksum dari artefak `The-Great-Drive-v<versi>-vc<kode>-run<nomor>-<commit>`.

Alur kerja memakai JDK 17, Gradle 8.13, Android SDK 36, dan Python 3.11. Isi APK mendukung arm64 dan x86_64; integrasi FFmpeg yang disertakan memerlukan arm64. Naikkan `VERSION_CODE` setiap membuat pembaruan.

Paket Android tetap `com.tgdrive.mobile` agar kompatibel dengan pembaruan. Pertahankan sertifikat penandatanganan, klien OAuth, dan nama rahasia `TGDRIVE_*` yang sudah digunakan. Penandatanganan rilis sengaja dihentikan jika kunci yang benar tidak tersedia. Jangan masukkan kunci, kata sandi, atau cookie akun ke repositori.

## Privasi dan batasan

- Sesi situs tetap berada di perangkat. Berkas cookie sementara yang privat diberikan kepada ekstraktor, lalu dihapus saat tugas berakhir. Cadangan tidak memuat cookie login atau token akses Google.
- Situs sumber dan Google Drive menerima permintaan yang diperlukan untuk mengunduh dan mengunggah. Unduhan memerlukan koneksi internet.
- Dukungan situs bergantung pada ekstraktor, situs sumber, dan akses akunmu. Login tidak menjamin semua tautan didukung.
- Unggahan TikTok akan dicoba lagi dengan gallery-dl jika yt-dlp gagal, termasuk tautan pendek. TikTok tetap dapat menolak keduanya; kualitas dan metadata JSON opsional pada percobaan kedua dapat berbeda. Buka Aktivitas → Lihat log error untuk membaca kesalahan keduanya.
- Story dan tautan pratinjau bisa kedaluwarsa. Muat ulang daftar Story jika item pilihan tidak tersedia lagi.
- Ini adalah rilis **alfa**. Saat pertama mencoba pemindahan folder, simpan sumber dan periksa hasilnya di perangkat.

## Kontribusi

Jaga perubahan tetap terarah dan pertahankan alur unduhan, login, serta penyimpanan yang sudah bekerja. Saat melaporkan masalah, sertakan versi aplikasi, versi Android, langkah reproduksi, dan log kesalahan yang sudah disamarkan.

Teks aplikasi dikelola di [`tools/localization.json`](tools/localization.json). Jalankan `python tools/generate_localization.py` setelah mengubah terjemahan, lalu `python tests/check_localization.py`. Dokumen Fitur dan Catatan versi lengkap berada di `docs/i18n/id/` dan `docs/i18n/en/`; keduanya dikemas saat aplikasi dibangun. Lihat [panduan pelokalan](docs/LOCALIZATION.md).

Dibangun dengan [yt-dlp](https://github.com/yt-dlp/yt-dlp), [gallery-dl](https://github.com/mikf/gallery-dl), [Chaquopy](https://chaquo.com/chaquopy/), [FFmpeg](https://ffmpeg.org/), dan [libtorrent4j](https://github.com/aldenml/libtorrent4j). Lisensi serta pemberitahuan masing-masing berlaku untuk dependensi tersebut.

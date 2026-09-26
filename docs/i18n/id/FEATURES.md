# Fitur The Great Drive

## Unduh dan simpan
- Tempel tautan, terima tautan/berkas dari Bagikan Android, atau pilih berkas dari ponsel.
- Simpan ke Lokal, Google Drive, atau keduanya, dengan susunan situs/akun/jenis media.
- Pilih kualitas video atau audio, serta sertakan takarir, gambar mini, dan metadata JSON.
- Kosongkan maksimum item untuk tanpa batas. Pengaturan terakhir tetap tersimpan.

## Instagram dan situs lain
- Unduh video, Reel, foto, dan carousel melalui ekstraktor yang mendukungnya.
- Pilih Story Instagram dengan gambar mini, pratinjau, dan pilihan beberapa atau semua item.
- Masuk ke Instagram, X, atau situs HTTPS lain. Situs tersimpan mendapat tombol untuk dibuka kembali.
- Impor cookies.txt, serta gunakan mode profil/album dan filter tanggal pada jalur yang mendukung.
- Dukungan situs bergantung pada ekstraktor dan akses akun. Sebagian konten memerlukan login.
- Unggahan TikTok memakai ekstraktor kedua jika yt-dlp gagal; TikTok tetap dapat menolak kedua cara. Post foto/carousel dapat digabung dengan audionya menjadi satu MP4 atau disimpan sebagai gambar + audio terpisah. Unduhan video dapat meminta tanpa watermark atau watermark bawaan TikTok jika format tersebut tersedia. Ekstraktor cadangan bisa memakai kualitas berbeda atau tidak menyertakan metadata JSON pilihan.

## Berkas dan impor
- Impor Instagram JSON/ZIP dengan kategori, rentang tanggal, serta hasil folder atau ZIP.
- Unduh tautan magnet, URL torrent, dan berkas .torrent lokal.
- Gabungkan video + audio, atau satu/lebih gambar + audio menjadi MP4 slideshow, dengan FFmpeg pada perangkat arm64. Pemilihan media manual tetap berada di Berkas saat tugas disiapkan dan dimasukkan ke antrean.

## Google Drive
- Unggah ke folder pilihan dengan opsi lewati nama sama dan verifikasi checksum.
- Jelajahi dan cari berkas, buat folder, ubah nama, pindahkan, beri bintang, dan masukkan ke Sampah.
- Buat atau cabut tautan publik melalui penjelajah Drive.

## Antrean dan data perangkat
- Pantau progres, batalkan tugas, jeda antrean, dan ulangi unduhan gagal.
- Buka rincian hasil dan log kesalahan; ekspor serta pulihkan riwayat dan daftar situs.
- Gunakan Administrasi perangkat untuk melihat jumlah tugas, antrean, akun, dan penyimpanan sementara.
- Bersihkan cache tugas lama atau riwayat dengan konfirmasi. Berkas unduhan tetap tersimpan.

## Pindahkan folder lama
- Berkas menyediakan pemindahan folder lama TGDrive ke The Great Drive, untuk Lokal maupun Drive.
- Simpan sumber: salin ke folder baru dan pertahankan berkas sumber.
- Hapus sumber: salin dan verifikasi isi sebelum menghapus sumber Lokal atau memindahkan sumber Drive ke Sampah.
- Pertahankan subfolder, pakai ulang salinan identik, dan simpan isi yang berbeda tanpa menimpanya.
- Pantau progres serta hasil pemindahan; hentikan dan jalankan ulang proses yang terputus.
- Pemindahan Lokal mencakup berkas milik instalasi saat ini. Drive mencakup berkas biasa dalam TGDrive di bawah induk pilihan; dokumen Google dan pintasan dilewati.
- Folder lama yang kosong tetap ada. Berkas yang tidak lagi dimiliki instalasi ini perlu dipindahkan melalui pengelola berkas.

## Bahasa dan informasi aplikasi
- Pilih Bahasa Indonesia atau English di Opsi. Pilihan tersimpan di perangkat.
- Halaman, dialog, notifikasi, dan pesan status buatan aplikasi mengikuti bahasa pilihan.
- Info berisi Tentang, Fitur, dan Catatan versi lengkap dalam dua bahasa, tersedia luring.
- Isi situs, nama berkas, URL, serta diagnostik mentah penyedia mempertahankan teks aslinya.

## Koneksi dan percobaan ulang

- **Opsi → Koneksi & retry:** pemulihan otomatis aktif secara bawaan. Untuk tautan yang ditempel, gunakan **Atur retry per tautan**; tautan dicentang mendapat pemulihan otomatis dan yang tidak dicentang memakai pengulangan manual. Setiap Story juga memiliki kotak pengaturan sendiri pada pemilih.
- **Aktivitas → Pilih media untuk diulang:** pilih sumber yang gagal, dibatalkan, atau terhenti, lalu atur pemulihan otomatis masing-masing. Buka detail tugas → **Retry media terpilih** untuk memilih berkas belum selesai setelah ekstraksi menghasilkan daftar keluaran. Sebelum ekstraksi selesai, pengulangan berlaku pada tautan/Story sumber; item carousel atau daftar putar yang belum ditemukan belum bisa dipilih satu per satu.
- Transfer Story, berkas langsung, dan pemutar TikTok mencoba ulang gangguan koneksi sementara hingga tiga kali, memakai koneksi baru dan jeda 1/2/4 detik. Unduhan yang dikelola ekstraktor memakai pengaturan percobaan per berkas/bagian. Metode ekstraktor cadangan tetap tersedia. Masalah izin yang menetap, Story kedaluwarsa, dan kegagalan sertifikat perlu ditangani alih-alih diulang terus.
- Tujuan keluaran yang sudah selesai dicatat. Pengulangan unggahan Drive memakai media dalam cache dan tidak membuat salinan lokal yang sudah berhasil lagi. Membersihkan cache menghapus data pemulihan ini; unduh kembali tautan sumber bila diperlukan.
- **Status Drive:** indikator di bawah banner berwarna hijau saat akun terverifikasi dan jaringan online, kuning saat menghubungkan, merah saat terputus/offline. Ketuk untuk membuka Akun. Indikator menunjukkan status akun/jaringan, bukan pemeriksaan server Drive terus-menerus.
- **Akun → Putuskan koneksi Drive:** menghapus koneksi tersimpan dari aplikasi tanpa menghapus berkas atau mencabut izin akun Google. Bagian unggahan yang sudah diterima Drive dapat selesai; bagian berikutnya dihentikan. Hubungkan kembali sebelum mengulang unggahan belum selesai.


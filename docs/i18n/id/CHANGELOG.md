# Catatan versi

## 1.3.11-alpha (19)
- Memperbaiki kegagalan TikTok `Unexpected response from webpage request`: yt-dlp diperbarui ke 2026.08.19 dan jalur API aplikasi TikTok dicoba sebelum halaman web.
- Fallback TikTok tidak lagi dianggap gagal bila gallery-dl sudah berhasil menyimpan media tetapi status side-item non-zero.
- Link pendek `vt.tiktok.com` / `vm.tiktok.com` memakai resolusi redirect yang lebih kompatibel dan cookie sesi parent-domain TikTok ikut diekspor.
- Workflow GitHub Actions tidak diubah. Fitur combine/separate, watermark, dan FFmpeg picker dari 1.3.10 tetap dipertahankan.


## 1.3.10-alpha (18)

- Menambahkan pilihan hasil post foto/carousel TikTok: gabungkan seluruh gambar hasil unduhan dengan audio menjadi satu MP4, atau simpan gambar dan audio asli secara terpisah.
- Menambahkan pilihan watermark video TikTok. Jalur tanpa watermark menghindari format TikTok ber-watermark; jalur dengan watermark meminta format watermark bawaan TikTok jika format tersebut tersedia dari ekstraktor.
- Memperluas alat FFmpeg di perangkat agar menerima satu atau lebih gambar + satu audio selain video + audio. Media terpilih langsung disiapkan dan dimasukkan ke antrean dari Berkas tanpa berpindah kembali ke Unduh.
- Menambahkan ikon vektor khusus untuk gabung media, media terpisah, watermark aktif/nonaktif, beserta teks antarmuka/status dwibahasa dan pengujian regresi untuk mode TikTok.

## 1.3.9-alpha (17)

- Mengganti simbol navigasi kecil dengan enam ikon vektor. Memperbesar ikon, teks, dan area sentuh tanpa mengubah menu yang ada.
- Jika yt-dlp gagal pada TikTok, mencoba ulang unggahan publik dengan ekstraktor media gallery-dl; tautan pendek TikTok dialihkan ke unggahan asal dan cookie situs tersimpan dipakai jika tersedia.
- Menampilkan kesalahan kedua ekstraktor jika TikTok tetap menolak permintaan. Alur unduhan situs lain tetap sama.

## 1.3.8-alpha (16)

- Menambahkan pilihan Bahasa Indonesia dan English di Opsi. Pilihan tersimpan dan mengganti teks antarmuka, dialog, notifikasi, serta pesan status aplikasi.
- Menyediakan Fitur dan Catatan versi dalam dua bahasa lengkap yang dapat dibaca luring. Teks teknis mentah dari situs atau pustaka tetap dipertahankan untuk diagnosis.
- Merapikan Administrasi perangkat menjadi kartu ringkasan unduhan, antrean, akun, dan penyimpanan; memperbaiki jarak terhadap bilah sistem serta tombol tindakan. Menghapus penjelasan administrasi bot yang tidak relevan.
- Memperbarui tampilan pilihan Tentang, Fitur, dan Catatan versi. Pilihan bahasa mempertahankan tautan, berkas terpilih, serta pengaturan unduhan yang sedang digunakan.
- Menulis ulang README dalam Bahasa Indonesia dan English: pengenalan aplikasi, panduan mulai, menu, penyimpanan, pembangunan APK, dan batasan.

## 1.3.7-alpha (15)

- Memperbaiki latar ikon adaptif yang menyebabkan ikon Android bawaan muncul, serta menambahkan ikon bitmap cadangan.
- Memindahkan informasi aplikasi ke menu Info dengan pilihan Tentang, Fitur, dan Catatan versi luring.
- Menambahkan pemindahan folder lama TGDrive ke The Great Drive untuk Lokal dan Drive, dengan pemeriksaan jumlah berkas sebelum mulai.
- Menyediakan pilihan simpan sumber atau hapus setelah salinan diverifikasi. Lokal memeriksa ukuran dan SHA-256; Drive memeriksa ukuran dan MD5 lalu memindahkan sumber ke Sampah.
- Memakai ulang salinan identik, mempertahankan isi yang berbeda, dan memperbarui tautan riwayat Drive sebelum menghapus sumber. Folder sumber kosong tetap ada.
- Menampilkan progres, pembatalan, ringkasan, dan kesalahan pemindahan. Proses yang terhenti dapat dijalankan ulang.
- Pemindahan Lokal hanya mencakup berkas milik instalasi ini. Drive mencari folder lama di My Drive atau folder induk pilihan; dokumen Google dan pintasan dilewati.

## 1.3.6-alpha (14)

- Mengganti identitas aplikasi menjadi The Great Drive: nama Android, ikon peluncur, banner Unduh, dan informasi Tentang dengan nomor versi.
- Memperbarui nama proyek, alur kerja GitHub Actions, APK, artefak, teks antarmuka, notifikasi, serta panduan penyiapan.
- Menyimpan unduhan baru di folder The Great Drive pada Lokal dan Google Drive. Berkas lama tetap berada di folder TGDrive.
- Mempertahankan paket com.tgdrive.mobile, sertifikat rilis, OAuth, rahasia penandatanganan, sesi, pengaturan, dan format cadangan.

## 1.3.5-alpha (13)

- Memperbaiki Story video yang tidak mendapatkan URL media ketika ekstraktor mengembalikan URL DASH sintetis ytdl:. Pemilih Story memakai mode video gabungan untuk mengambil MP4 langsung.
- Jika masih menerima DASH, gunakan video_url dari metadata Story dengan ID yang sama. Tolak media dari ID lain; jalur foto dan unduhan biasa tetap dipertahankan.
- Menambahkan pengujian regresi URL ytdl: dan video_url langsung. Resolusi MP4 gabungan dapat berbeda dari hasil DASH yang digabung FFmpeg.

## 1.3.4-alpha (12)

- Memperbaiki pilihan Story yang menyimpan item terakhir meskipun ID lain dipilih. Media kini dicari dan disimpan berdasarkan ID Story pilihan.
- Menandai tugas gagal jika ID tidak tersedia atau kedaluwarsa, dengan petunjuk memuat ulang daftar. Tidak menggantinya dengan Story terakhir atau menampilkan selesai palsu.
- Menamai foto, video, dan metadata JSON opsional dengan ID Story yang sesuai, melalui antrean serta tujuan Lokal/Drive yang sama.
- Menambahkan pengujian tiga Story berbeda dan ID yang tidak ditemukan.

## 1.3.3-alpha (11)

- Menambahkan gambar mini foto/video pada pemilih Story. Ketuk untuk melihat gambar lebih besar atau memutar video jika URL tersedia.
- Memasukkan pilihan Story sebagai satu kelompok antrean, dengan hasil terpisah untuk setiap item.
- Memastikan Story yang dipilih langsung masuk antrean meski pilihan lewati tautan selesai aktif. Pilihan tersebut tetap berlaku pada unduhan URL biasa.
- Unduhan tetap dapat dimulai saat pratinjau tidak tersedia atau URL pratinjau kedaluwarsa.

## 1.3.2-alpha (10)

- Menginisialisasi ekstraktor gallery-dl sebelum membaca daftar Story agar sesi dan API Instagram tersedia.
- Menyimpan rincian kegagalan Story serta unduhan di perangkat, dengan cookie dan token disamarkan. Log dapat dibaca dan dibagikan dari Aktivitas.
- Menampilkan dialog kegagalan Story dengan tombol Buka log agar pesan tidak terpotong seperti pada pemberitahuan singkat.

## 1.3.1-alpha (9)

- Memisahkan antarmuka menjadi Unduh, Opsi, Akun, Berkas, dan Aktivitas sambil mempertahankan pilihan, sesi, serta alur antrean.
- Mengganti label Galeri menjadi Lokal karena hasil juga mencakup JSON/ZIP. Nilai internal tetap kompatibel dengan riwayat dan pengaturan sebelumnya.
- Merangkum riwayat dalam kartu tugas yang dapat diketuk untuk melihat lokasi berkas dan tautan Drive. Riwayat lama juga ditampilkan dengan label Lokal.
- Memindahkan kontrol antrean ke Aktivitas, cadangan dan administrasi ke Akun, serta menambahkan penanda berkas pilihan.
- Membagi pilihan kualitas dan kategori menjadi beberapa baris serta menambahkan jarak terhadap bilah sistem.

## 1.3.0-alpha (8)

- Menyimpan tujuan, kualitas, batas item, folder Drive, mode profil, tanggal, serta pilihan unduhan untuk dipakai kembali saat menerima tautan Bagikan Android.
- Mengizinkan batas item kosong untuk tanpa batas; angka positif tetap membatasi daftar putar, profil, dan impor.
- Menambahkan foto Instagram melalui gallery-dl ketika tidak ada format video, serta mencoba foto pada postingan campuran tanpa mengganti jalur video/Reel yang sudah berjalan.
- Menambahkan pemilih Story aktif dengan pilihan beberapa atau semua item melalui sesi Instagram.
- Mengunduh magnet, URL HTTPS .torrent, serta berkas .torrent di perangkat melalui libtorrent4j, dengan struktur berkas tetap dipertahankan.
- Menggabungkan audio/video impor Instagram melalui FFmpeg serta menyediakan penggabungan satu MP4 dan satu berkas audio lokal. FFmpeg yang disertakan memerlukan arm64.
- Menambahkan administrasi perangkat untuk ringkasan riwayat, akun, antrean, jeda, pembatalan, pembersihan cache, serta penghapusan riwayat dengan konfirmasi.

## 1.2.0-alpha

- Menambahkan kualitas 2160p, 1440p, 480p, dan video saja, serta mode gallery-dl pilihan untuk album/profil Instagram, profil Facebook, dan linimasa X. Menambahkan filter foto/video Facebook dan rentang tanggal.
- Menambahkan rentang tanggal impor Instagram JSON/ZIP serta pilihan keluaran satu ZIP tanpa kompresi; folder tetap menjadi pilihan awal.
- Menambahkan bintang Drive, pencabutan akses publik, pemindahan berkas, pelewatan nama sama, verifikasi MD5 opsional, dan pemuatan daftar/pencarian bertahap.
- Menambahkan impor cookies.txt untuk situs HTTPS, unduhan tanpa akun, serta pelewatan tautan yang pernah selesai.
- Menambahkan jeda antar-tugas, pengenalan tugas terhenti dan pengulangan, serta cadangan riwayat/daftar situs tanpa cookie atau token Google.

## 1.1.4-alpha

- Membuka beranda X tanpa selalu memulai alur login. Tombol selesai memeriksa cookie sesi X sebelum menutup halaman.
- Mengingat hingga 24 situs login HTTPS dan membuat tombol untuk membukanya kembali. Tekan lama untuk menghapus tombol; parameter dan fragmen URL tidak disimpan.

## 1.1.3-alpha

- Mengingat akun Google Drive terakhir dan meminta token baru saat aplikasi dibuka, dengan petunjuk sambung ulang jika izin berakhir atau dicabut. Token tidak disimpan.
- Mempertahankan cookie WebView antar-pembukaan aplikasi dan cadangan terenkripsi selama tujuh hari untuk cookie sesi melalui Android Keystore. Domain X dan Twitter diteruskan ke pengunduh.
- Menambahkan login situs HTTPS lain untuk digunakan oleh ekstraktor yang mendukung situs tersebut.

## 1.1.2-alpha

- Menggunakan nama akun dari URL Story Instagram untuk folder, termasuk saat ekstraktor mengembalikan ID pengguna numerik.
- Membaca hasil otorisasi Google sebelum menganggap pemilihan akun dibatalkan; menampilkan kode kesalahan yang tersedia.
- Mendokumentasikan SHA-1 sertifikat rilis yang digunakan dan konfigurasi OAuth Drive.

## 1.1.1-alpha

- Mengonfirmasi akun Drive yang dipilih, menampilkan alamat email tersambung, dan memungkinkan pergantian akun.
- Mengelompokkan hasil Lokal dan Drive berdasarkan situs, nama akun, dan jenis media di folder TGDrive yang digunakan saat itu.
- Menyimpan status sebelum memperbarui layar; menampilkan 100% hanya setelah tujuan penyimpanan selesai.
- Memperbaiki deklarasi variabel lokal ganda pada History.record yang menghambat kompilasi Java.

## 1.1.0-alpha

- Menjalankan ekstraksi media dan penyimpanan di perangkat tanpa memerlukan server aplikasi terpisah.
- Menambahkan sesi situs lokal, antrean, penyimpanan media Lokal, unggahan, dan penjelajah Drive.
- Menambahkan impor Instagram JSON/ZIP dengan pilihan kategori.
- Mempertahankan identitas penandatanganan rilis dan memberi nama artefak pembangunan yang berbeda untuk setiap hasil.
- Memperbaiki penyiapan Android SDK ke versi tindakan v4 dan memasang paket SDK yang diperlukan secara eksplisit.

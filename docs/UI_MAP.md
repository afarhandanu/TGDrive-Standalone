# Peta tampilan 1.3.1-alpha

| Menu | Isi | Alur yang dipakai |
|---|---|---|
| Unduh | URL/share, tujuan Lokal/Drive/Keduanya, kualitas, maksimum item, Story picker, mulai | MainActivity.start → enqueue → DownloadService lama |
| Opsi | Subtitle, thumbnail, metadata, lewati selesai, anonim, Drive, mode profil, jenis Facebook, folder Drive, tanggal | Satu formulir pengaturan yang tetap tersimpan; berpengaruh juga pada Berkas |
| Akun | Login Instagram, X, situs lain, cookies.txt, akun/file Drive, ekspor/pulihkan cadangan, administrasi perangkat | Kelas LoginActivity, DriveBrowserActivity, AppBackup, dan AdminActivity lama |
| Berkas | Import Instagram JSON/ZIP, file dari ponsel, .torrent, gabung audio-video | Pemilih file dan jalur enqueue lama; berkas biasa kembali ke Unduh untuk memilih tujuan |
| Aktivitas | Kontrol antrean, kartu ringkas, detail tiap job | Riwayat tetap ditulis oleh History/DownloadService seperti sebelumnya |

Nilai internal tujuan `gallery` tetap sama. Aplikasi memakai MediaStore: gambar di Pictures, video di Movies, audio di Music, dan JSON/ZIP/berkas lain di Downloads. Label antarmukanya **Lokal** agar berlaku bagi semua jenis berkas.

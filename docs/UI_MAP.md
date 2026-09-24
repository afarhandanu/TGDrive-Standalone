# Peta tampilan 1.3.7-alpha

| Menu | Isi | Alur yang dipakai |
|---|---|---|
| Unduh | URL/share, tujuan Lokal/Drive/Keduanya, kualitas, maksimum item, Story picker dengan pratinjau, mulai | URL biasa tetap MainActivity.start → enqueue → DownloadService; Story diproses per item dalam satu batch dan media diverifikasi berdasarkan ID |
| Opsi | Subtitle, thumbnail, metadata, lewati selesai, anonim, Drive, mode profil, jenis Facebook, folder Drive, tanggal | Satu formulir pengaturan yang tetap tersimpan; berpengaruh juga pada Berkas |
| Akun | Login Instagram, X, situs lain, cookies.txt, akun/file Drive, ekspor/pulihkan cadangan, administrasi perangkat | Kelas LoginActivity, DriveBrowserActivity, AppBackup, dan AdminActivity lama |
| Berkas | Import Instagram JSON/ZIP, file dari ponsel, .torrent, gabung audio-video, pindahkan folder lama Lokal/Drive | Jalur unduh lama; migrasi membuka MigrationActivity dengan pratinjau jumlah file dan pilihan salin atau hapus sumber setelah verifikasi |
| Aktivitas | Kontrol antrean, log error, kartu ringkas, detail tiap job | Riwayat tetap ditulis oleh History/DownloadService; error Story dan unduhan bisa dibaca dan dibagikan dari perangkat |
| Info | Tentang, Fitur, Changelog | Tiga pilihan dalam tab khusus; dokumen offline dikemas saat build dari docs/FEATURES.md dan CHANGELOG.md |

Nilai internal tujuan `gallery` tetap sama. Aplikasi memakai MediaStore: gambar di Pictures, video di Movies, audio di Music, dan JSON/ZIP/berkas lain di Downloads. Label antarmukanya **Lokal** agar berlaku bagi semua jenis berkas.

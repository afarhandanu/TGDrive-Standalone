# Peta tampilan 1.3.12-alpha

Enam menu memakai ikon vektor 26 dp dan tombol setinggi 72 dp. Label tetap mengikuti bahasa aplikasi; urutan dan fungsi menu tidak berubah.

| Menu | Isi | Alur yang dipakai |
|---|---|---|
| Unduh | URL/share, tujuan Lokal/Drive/Keduanya, kualitas, maksimum item, Story picker dengan pratinjau, mulai | URL biasa tetap MainActivity.start → enqueue → DownloadService; Story diproses per item dalam satu batch dan media diverifikasi berdasarkan ID. TikTok yang gagal pada yt-dlp dicoba sekali lagi melalui gallery-dl khusus TikTok. |
| Opsi | Bahasa Indonesia/English, takarir, gambar mini, metadata, lewati selesai, anonim, Drive, mode profil, jenis Facebook, folder Drive, tanggal, mode foto/carousel TikTok, watermark TikTok | Satu formulir pengaturan yang tetap tersimpan; mode TikTok dikirim ke job unduhan dan berpengaruh pada hasil carousel/video |
| Akun | Login Instagram, X, situs lain, cookies.txt, akun/file Drive, ekspor/pulihkan cadangan, administrasi perangkat | Sesi dan alur akun yang sama; AdminActivity memakai kartu ringkasan serta jarak aman dari bilah sistem |
| Berkas | Import Instagram JSON/ZIP, file dari ponsel, .torrent, gabung media + audio, pindahkan folder lama Lokal/Drive | FFmpeg menerima 1 video + 1 audio atau satu/lebih gambar + 1 audio. Setelah media dipilih, job langsung disiapkan dari tab Berkas tanpa dilempar kembali ke Unduh; migrasi tetap memakai alur lama. |
| Aktivitas | Kontrol antrean, log error, kartu ringkas, detail tiap job | Riwayat tetap ditulis oleh History/DownloadService; error Story dan unduhan bisa dibaca dan dibagikan dari perangkat |
| Info | Tentang, Fitur, Catatan versi | Tiga pilihan dalam tab khusus; dokumen luring lengkap dua bahasa dikemas dari docs/i18n/id dan docs/i18n/en sesuai bahasa pilihan |

Nilai internal tujuan `gallery` tetap sama. Aplikasi memakai MediaStore: gambar di Pictures, video di Movies, audio di Music, dan JSON/ZIP/berkas lain di Downloads. Label antarmukanya **Lokal** agar berlaku bagi semua jenis berkas.

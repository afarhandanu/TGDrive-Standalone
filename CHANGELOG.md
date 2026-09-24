# Changelog

## 1.3.5-alpha (versionCode 13)

- Memperbaiki Story video yang gagal dengan "URL media ... tidak tersedia" ketika gallery-dl mengembalikan URL DASH sintetis berawalan `ytdl:`. Khusus pemilih Story, extractor menggunakan mode video `merged` agar mengembalikan tautan MP4 langsung.
- Jika extractor masih mengembalikan URL DASH, gunakan `video_url` langsung dari metadata milik Story dengan ID yang sama. Tetap tolak URL Story lain dan tetap gagal bila URL media ID yang dipilih tidak tersedia.
- Tambah regresi untuk Story video dengan URL `ytdl:` dan `video_url` langsung; foto dan jalur download biasa tidak diubah.
- Mode MP4 gabungan dapat memakai resolusi berbeda dari mode DASH yang digabung dengan FFmpeg. Pengujian hasil pada akun Instagram sungguhan masih diperlukan.

## 1.3.4-alpha (versionCode 12)

- Memperbaiki pemilih Story yang menyimpan Story terakhir walaupun foto atau ID lain dipilih. Job Story kini mencari media melalui gallery-dl berdasarkan ID Story yang dipilih, lalu menyimpan hanya URL media dengan ID sama.
- Jika ID sudah kedaluwarsa, job berstatus gagal dengan pesan agar memuat ulang daftar Story. Tidak ada fallback ke Story terakhir atau status selesai palsu.
- Gambar, video, serta metadata JSON opsional dari Story terpilih memakai nama berisi ID Story. Tujuan Lokal/Drive dan antrean yang ada tetap dipakai.
- Menambahkan tes regresi tiga Story dan ID yang tidak ditemukan. Download Story perlu diverifikasi pada APK di perangkat dengan sesi Instagram aktif.

## 1.3.3-alpha (versionCode 11)

- Menambahkan thumbnail foto/video di pemilih Story; thumbnail dapat diketuk untuk melihat foto lebih besar atau memutar video ketika URL media tersedia.
- Memproses pilihan Story sebagai satu batch di service dan mencatat hasil tiap Story secara terpisah. Ini mencegah rangkaian permintaan service terpisah berhenti sebelum seluruh pilihan selesai diproses.
- Pilihan Story yang ditekan langsung selalu masuk antrean, termasuk ketika opsi "lewati link yang sudah berhasil diunduh" aktif; opsi tersebut tetap berlaku untuk download URL biasa.
- Pratinjau dari CDN bisa kedaluwarsa; tombol download tetap bisa dipakai jika pratinjau tidak muncul. Perlu pengujian langsung di perangkat untuk daftar Story sebenarnya dan hasil Drive/Lokal.

## 1.3.2-alpha (versionCode 10)

- Inisialisasi extractor gallery-dl ketika membuka pemilih Story, sehingga sesi dan API Instagram tersedia sebelum daftar dibaca.
- Simpan traceback error pemilih Story dan kegagalan unduhan pada perangkat. Detailnya bisa dibaca dan dibagikan melalui Aktivitas → Lihat log error; nilai cookie dan token disamarkan.
- Jika pemilih Story gagal, tampilkan dialog dengan tombol Buka log agar pesan yang semula terpotong pada toast dapat dibaca.
- Perubahan ini belum diuji di perangkat; uji pemilih Story memakai akun Instagram yang masih login setelah memasang APK baru.

## 1.3.1-alpha (versionCode 9)

- Memisahkan layar utama menjadi lima menu: Unduh, Opsi, Akun, Berkas, dan Aktivitas. Field, pilihan yang tersimpan, sesi login, serta alur enqueue lama tetap dipakai oleh semua menu.
- Menamai tujuan di UI dan hasil baru sebagai **Lokal**, karena hasil JSON/ZIP masuk ke penyimpanan lokal selain foto/video yang terlihat di galeri. Nilai tujuan internal `gallery` tetap digunakan untuk kompatibilitas riwayat dan pengaturan yang tersimpan.
- Merangkum riwayat dalam kartu pendek per job; detail path dan tautan Drive tetap tersedia saat kartu diketuk. Riwayat lama bertuliskan Galeri juga ditampilkan sebagai Lokal.
- Memindahkan kontrol antrean ke bagian atas Aktivitas, cadangan dan administrasi perangkat ke Akun, serta menyediakan penanda saat file dari ponsel dipilih.
- Membagi pilihan kualitas dan kategori panjang menjadi dua baris dan memberi ruang dari status bar serta navigasi sistem.
- APK untuk versi ini harus dibangun dan diuji di perangkat; arsip source sudah disiapkan.

## 1.3.0-alpha (versionCode 8)

- Menyimpan pilihan tujuan, kualitas, batas item, folder Drive, mode profil, rentang tanggal, dan opsi unduhan di perangkat. Tautan yang dibagikan ke aplikasi menggunakan pilihan terakhir.
- Kolom maksimum item boleh kosong untuk mengambil semua item; angka positif tetap membatasi playlist, profil, dan import.
- Post Instagram yang tidak mempunyai format video memakai gallery-dl untuk foto; jalur yt-dlp yang sudah berhasil untuk Reel/video tetap digunakan. Post campuran juga mencoba foto dengan gallery-dl.
- Pemilih Story Instagram menampilkan Story aktif dari sesi yang sudah masuk, bisa memilih beberapa atau semua, lalu memasukkannya ke antrean dan tujuan Galeri/Drive yang sama.
- Magnet, URL HTTPS .torrent, dan berkas .torrent dari ponsel diunduh di perangkat dengan libtorrent4j; struktur file torrent dipertahankan saat disimpan.
- FFmpeg menggabungkan video dengan audio terpisah pada import Instagram, serta memberi pilihan gabung satu MP4 dan satu berkas audio dari ponsel.
- Halaman administrasi perangkat menampilkan status antrean, akun dan ringkasan riwayat serta menyediakan jeda, pembatalan, pembersihan cache job dan riwayat dengan konfirmasi.
- Native FFmpeg dari paket yang dipilih tersedia untuk perangkat arm64; fungsi penggabungan pada emulator x86_64 perlu build pustaka yang sesuai. Torrent, Galeri, Drive, dan ekstraktor yang tidak memerlukan FFmpeg tetap mengikuti jalur sebelumnya.
- APK belum dibangun atau diuji pada perangkat untuk versi ini; jalankan workflow build dan uji foto/Story, torrent serta mux pada arm64.

## 1.2.0-alpha — Bot feature expansion (source)

- Add 2160p/1440p/480p/video-only formats and a separate, opt-in gallery-dl path for Instagram albums/profiles, Facebook profiles and X media timelines. Offer Facebook photo/video selection and date limits on profile batches. The normal yt-dlp path remains the default.
- Add Instagram JSON/ZIP import date range and optional uncompressed ZIP output; folder output remains the default.
- Add Drive star/unstar, revoke public access, browse folders to move items, optional skip for files with the same name and optional MD5 check after uploading. Paginate Drive listing and search.
- Add Netscape cookies.txt import for a selected HTTPS site, optional anonymous download and optional skip for completed links.
- Add between-job queue pause, interrupted-job recognition with retry, and portable history/saved-site backup. Login cookies and Google tokens are excluded from exported files.
- Bot-only Telegram administration, the interactive Story picker, torrent engine and media mux still require separate Android work and on-device tests; see `docs/FEATURE_PARITY.md`.

## 1.1.4-alpha — X session check and saved site buttons

- Open the X home page instead of always starting its login flow. Only close the login screen through the Done button when X's session cookie is visible; otherwise explain what is missing.
- Remember up to 24 HTTPS login sites locally and show a button for each on the main screen. Re-entering a site updates its button; holding a button removes it. URL query and fragment are not stored.


## 1.1.3-alpha — Persistent account sessions and other websites

- Remember the last authorized Google Drive account and silently request a fresh access token when opening the app. Show a reconnect prompt if consent has expired or was revoked. Tokens are never stored.
- Keep WebView site cookies across app restarts and save an encrypted, seven-day fallback in Android Keystore storage for session cookies; X and Twitter domains are both passed to the downloader.
- Let users open an HTTPS login page for another website. Its private WebView cookies are made available when downloading media from the same website, subject to extractor support.

## 1.1.2-alpha — Story owner and Drive authorization

- Use the account name in an Instagram Story URL for its folder, even when the extractor returns a numeric user ID.
- Parse the Google authorization result before treating an account selection as cancelled; show the returned error code when available.
- Document the existing release certificate SHA-1 and required Google Drive OAuth configuration.

## 1.1.1-alpha — Drive account and media folders

- Confirm Drive authorization with the selected account, display the connected email, and allow changing the account.
- Save files into `TGDrive/site/username/type/filename` in Gallery and Google Drive.
- Persist job status before refreshing the screen; show 100% only after destinations finish.
- Fix the duplicate local variable in `History.record` that prevented Java compilation.
- Fix the duplicate local variable in `History.record` that prevented Java compilation.

## 1.1.0-alpha — build workflow fix

- Updated Android SDK setup to v4 and installed the required SDK packages explicitly. This avoids the discontinued `tools` package requested during setup.

## 1.1.0-alpha — Android standalone foundation

- Replaced the former server-dependent Android client with on-device media extraction and output destinations.
- Added local login sessions, queue, Gallery publishing, Drive upload and Drive file browsing.
- Added Instagram JSON/ZIP import with category selection.
- Retained the TGDrive release signing identity and distinct workflow artifact names.

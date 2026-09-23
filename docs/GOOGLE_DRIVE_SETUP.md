# Hubungkan Google Drive

Konfigurasi Google Cloud harus cocok dengan APK yang ditandatangani oleh kunci rilis TGDrive yang sudah ada. Pemilih akun yang tertutup setelah akun diketuk belum membuktikan bahwa pengguna membatalkan: Google juga dapat menolak otorisasi yang belum dikonfigurasi.

1. Pilih atau buat project di [Google Cloud Console](https://console.cloud.google.com/).
2. Aktifkan **Google Drive API** untuk project tersebut.
3. Atur **OAuth consent screen**. Jika statusnya masih **Testing**, masukkan alamat Google yang akan dipakai di APK ke **Test users**.
4. Pada **APIs & Services → Credentials**, buat **OAuth client ID** dengan tipe **Android**:

   - Package name: `com.tgdrive.mobile`
   - SHA-1 sertifikat rilis: `1E:A0:93:41:B1:75:57:E3:24:46:E8:BC:F3:C1:F5:F7:0F:77:3A:B2`

5. Pastikan APK yang diuji ditandatangani oleh keystore rilis TGDrive yang sama. SHA-1 di atas adalah sidik jari sertifikat yang tersimpan dalam `TGDrive-signing-key.jks`, bukan SHA-256 berkas keystore. Jangan buat kunci baru.
6. Di aplikasi, ketuk **Hubungkan / ganti akun Google Drive**, pilih akun penguji, dan lanjutkan persetujuan akses. Aplikasi akan menampilkan email sesudah berhasil membaca informasi akun lewat Drive API.

Jika masih gagal, catat kode kegagalan yang tampil di aplikasi, cek package name, SHA-1, project OAuth, status Drive API, dan daftar Test users. Jangan kirim token akses, isi secret, atau berkas keystore dalam laporan error.

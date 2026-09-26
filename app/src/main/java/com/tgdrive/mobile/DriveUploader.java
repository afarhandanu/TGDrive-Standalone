package com.tgdrive.mobile;

import android.webkit.MimeTypeMap;
import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** Resumable Drive v3 upload. The app obtains an OAuth token on-device. */
public final class DriveUploader {
    public interface Progress { void update(int percent); }

    public static String upload(File file, String token, String folderId, Progress progress) throws Exception {
        return upload(file, token, folderId, progress, false);
    }
    public static String upload(File file, String token, String folderId, Progress progress,
                                boolean verifyMd5) throws Exception {
        return upload(file, token, folderId, progress, verifyMd5, true);
    }
    public static String upload(File file, String token, String folderId, Progress progress,
                                boolean verifyMd5, boolean automatic) throws Exception {
        int attempts = automatic ? 4 : 1;
        if (token == null || token.isEmpty()) throw new IllegalStateException("Hubungkan Google Drive dahulu");
        String name = file.getName();
        String ext = name.contains(".") ? name.substring(name.lastIndexOf('.') + 1).toLowerCase() : "";
        String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
        if (mime == null) mime = "application/octet-stream";
        JSONObject metadata = new JSONObject().put("name", name).put("mimeType", mime);
        if (folderId != null && !folderId.trim().isEmpty()) metadata.put("parents", new org.json.JSONArray().put(folderId));
        String session = null;
        for (int attempt = 0; attempt < attempts; attempt++) {
            progress.update(0);
            HttpURLConnection start = null;
            try {
                start = connection("https://www.googleapis.com/upload/drive/v3/files?uploadType=resumable&fields=id%2CwebViewLink", token, "POST");
                start.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                start.setRequestProperty("X-Upload-Content-Type", mime);
                start.setRequestProperty("X-Upload-Content-Length", String.valueOf(file.length()));
                byte[] body = metadata.toString().getBytes(StandardCharsets.UTF_8);
                start.setDoOutput(true); start.setFixedLengthStreamingMode(body.length);
                try (var out = start.getOutputStream()) { out.write(body); }
                int status = start.getResponseCode();
                if (status != 200 && status != 201) throw httpFailure(status);
                session = start.getHeaderField("Location");
                break;
            } catch (IOException error) {
                if (attempt == attempts - 1 || !transientFailure(error)) throw error;
                retryDelay(attempt, progress, 0);
            } finally { if (start != null) start.disconnect(); }
        }
        if (session == null || !session.startsWith("https://www.googleapis.com/"))
            throw new IOException("Sesi upload Drive tidak valid");
        long size = file.length(), offset = 0;
        int failures = 0;
        boolean queryStatus = false;
        byte[] buffer = new byte[1024 * 1024];
        try (java.io.RandomAccessFile input = new java.io.RandomAccessFile(file, "r")) {
            while (true) {
                int percent = (int) Math.min(99, 100 * offset / Math.max(size, 1));
                progress.update(percent);
                int count = queryStatus ? 0 : (int) Math.min(buffer.length, size - offset);
                if (!queryStatus) { input.seek(offset); input.readFully(buffer, 0, count); }
                HttpURLConnection chunk = null;
                try {
                    chunk = connection(session, token, "PUT");
                    chunk.setRequestProperty("Content-Type", mime);
                    chunk.setRequestProperty("Content-Range", queryStatus || size == 0 ? "bytes */" + size :
                        "bytes " + offset + "-" + (offset + count - 1) + "/" + size);
                    chunk.setDoOutput(true); chunk.setFixedLengthStreamingMode(count);
                    try (var out = chunk.getOutputStream()) { if (count > 0) out.write(buffer, 0, count); }
                    int status = chunk.getResponseCode();
                    if (status == 200 || status == 201) {
                        JSONObject result = new JSONObject(new String(readAll(chunk.getInputStream()), StandardCharsets.UTF_8));
                        String link = confirmed(file, token, result, verifyMd5);
                        // The server has committed this file; report completion before any next-media cancellation.
                        progress.update(100);
                        return link;
                    }
                    if (status != 308) throw httpFailure(status);
                    long received = UploadRange.offset(chunk.getHeaderField("Range"), size);
                    if (!queryStatus && received <= offset) throw new IOException("Upload Drive belum maju");
                    if (received < offset) throw new IllegalStateException("Offset upload Drive berubah; hentikan agar data tidak rusak");
                    if (received == size) throw new IOException("Drive belum mengonfirmasi hasil upload");
                    if (received > offset) failures = 0;
                    offset = received;
                    queryStatus = false;
                } catch (IOException error) {
                    if (failures >= attempts - 1 || !transientFailure(error)) throw error;
                    retryDelay(failures++, progress, percent);
                    // A lost response does not tell us whether Drive received the bytes.
                    // Query the SAME resumable session before sending any bytes again.
                    queryStatus = true;
                } finally { if (chunk != null) chunk.disconnect(); }
            }
        }
    }
    private static String confirmed(File file, String token, JSONObject result, boolean verifyMd5) throws Exception {
        if (verifyMd5) {
            String remote = DriveFiles.md5Checksum(token, result.getString("id"));
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("MD5");
            try (FileInputStream check = new FileInputStream(file)) {
                byte[] block = new byte[262144]; int read;
                while ((read = check.read(block)) != -1) digest.update(block, 0, read);
            }
            StringBuilder local = new StringBuilder();
            for (byte b : digest.digest()) local.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
            if (remote.isEmpty() || !remote.equalsIgnoreCase(local.toString()))
                throw new IllegalStateException("Checksum MD5 Drive berbeda atau belum tersedia untuk " + file.getName());
        }
        return result.optString("webViewLink", "https://drive.google.com/file/d/" + result.getString("id") + "/view");
    }
    private static IOException httpFailure(int status) {
        if (status == 401 || status == 403)
            throw new IllegalStateException("Token Drive kedaluwarsa. Hubungkan Drive ulang lalu coba lagi.");
        if (status != 408 && status != 429 && status < 500)
            throw new IllegalStateException("Drive HTTP " + status);
        return new IOException("Drive HTTP " + status);
    }
    private static boolean transientFailure(IOException error) {
        if (error instanceof javax.net.ssl.SSLHandshakeException || error instanceof javax.net.ssl.SSLPeerUnverifiedException)
            return false;
        for (Throwable cause = error; cause != null; cause = cause.getCause())
            if (cause instanceof java.security.cert.CertificateException) return false;
        return true;
    }
    private static void retryDelay(int attempt, Progress progress, int percent) throws InterruptedException {
        for (int i = 0; i < 4 * (1 << attempt); i++) {
            progress.update(percent); Thread.sleep(250);
        }
    }

    private static HttpURLConnection connection(String url, String token, String method) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setRequestMethod(method);
        c.setInstanceFollowRedirects(false);
        c.setRequestProperty("Connection", "close");
        c.setRequestProperty("Authorization", "Bearer " + token);
        c.setConnectTimeout(30000);
        c.setReadTimeout(60000);
        return c;
    }
    private static byte[] readAll(InputStream input) throws IOException {
        try (InputStream in = input; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192]; int n;
            while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
            return out.toByteArray();
        }
    }
}

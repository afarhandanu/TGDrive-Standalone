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
        if (token == null || token.isEmpty()) throw new IllegalStateException("Hubungkan Google Drive dahulu");
        String name = file.getName();
        String ext = name.contains(".") ? name.substring(name.lastIndexOf('.') + 1).toLowerCase() : "";
        String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
        if (mime == null) mime = "application/octet-stream";
        JSONObject metadata = new JSONObject().put("name", name).put("mimeType", mime);
        if (folderId != null && !folderId.trim().isEmpty()) metadata.put("parents", new org.json.JSONArray().put(folderId));
        HttpURLConnection start = connection("https://www.googleapis.com/upload/drive/v3/files?uploadType=resumable&fields=id%2CwebViewLink", token, "POST");
        start.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        start.setRequestProperty("X-Upload-Content-Type", mime);
        start.setRequestProperty("X-Upload-Content-Length", String.valueOf(file.length()));
        byte[] body = metadata.toString().getBytes(StandardCharsets.UTF_8);
        start.setDoOutput(true);
        start.setFixedLengthStreamingMode(body.length);
        try (var out = start.getOutputStream()) { out.write(body); }
        int status = start.getResponseCode();
        if (status != 200 && status != 201) throw new IOException("Drive gagal memulai upload: HTTP " + status);
        String session = start.getHeaderField("Location");
        start.disconnect();
        if (session == null || !session.startsWith("https://www.googleapis.com/"))
            throw new IOException("Sesi upload Drive tidak valid");
        long size = file.length();
        long offset = 0;
        byte[] buffer = new byte[1024 * 1024];
        try (FileInputStream input = new FileInputStream(file)) {
            while (offset < size || (size == 0 && offset == 0)) {
                int count = input.read(buffer, 0, (int) Math.min(buffer.length, size - offset));
                if (size == 0) count = 0;
                if (count < 0) throw new IOException("File berubah selama upload");
                boolean delivered = false;
                for (int attempt = 0; attempt < 4 && !delivered; attempt++) {
                    try {
                        HttpURLConnection chunk = connection(session, token, "PUT");
                        chunk.setRequestProperty("Content-Type", mime);
                        chunk.setRequestProperty("Content-Range", size == 0 ? "bytes */0" :
                            "bytes " + offset + "-" + (offset + count - 1) + "/" + size);
                        chunk.setDoOutput(true);
                        chunk.setFixedLengthStreamingMode(count);
                        try (var out = chunk.getOutputStream()) { out.write(buffer, 0, count); }
                        status = chunk.getResponseCode();
                        if (status == 200 || status == 201) {
                            JSONObject result = new JSONObject(new String(readAll(chunk.getInputStream()), StandardCharsets.UTF_8));
                            if (verifyMd5) {
                                String remote = DriveFiles.md5Checksum(token, result.getString("id"));
                                java.security.MessageDigest digest = java.security.MessageDigest.getInstance("MD5");
                                try (FileInputStream check = new FileInputStream(file)) {
                                    byte[] block = new byte[262144]; int read;
                                    while ((read = check.read(block)) != -1) digest.update(block, 0, read);
                                }
                                byte[] actual = digest.digest();
                                StringBuilder local = new StringBuilder();
                                for (byte b : actual) local.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
                                if (remote.isEmpty() || !remote.equalsIgnoreCase(local.toString()))
                                    throw new IllegalStateException("Checksum MD5 Drive berbeda atau belum tersedia untuk " + file.getName());
                            }
                            progress.update(100);
                            return result.optString("webViewLink", "https://drive.google.com/file/d/" + result.getString("id") + "/view");
                        }
                        if (status == 308) {
                            String range = chunk.getHeaderField("Range");
                            long received = range == null ? 0 : Long.parseLong(range.substring(range.lastIndexOf('-') + 1)) + 1;
                            if (received != offset + count) throw new IOException("Offset upload Drive berubah; hentikan agar data tidak rusak");
                            offset = received;
                            progress.update((int) (100 * offset / Math.max(size, 1)));
                            delivered = true;
                        } else if (status == 401 || status == 403) {
                            throw new IllegalStateException("Token Drive kedaluwarsa. Hubungkan Drive ulang lalu coba lagi.");
                        } else if (status >= 500 || status == 429) {
                            Thread.sleep(1000L * (attempt + 1));
                        } else throw new IOException("Drive HTTP " + status);
                        chunk.disconnect();
                    } catch (IOException ex) {
                        if (attempt == 3) throw ex;
                        Thread.sleep(1000L * (attempt + 1));
                    }
                }
                if (!delivered) throw new IOException("Upload Drive gagal sesudah retry");
                if (size == 0) break;
            }
        }
        throw new IOException("Drive tidak mengonfirmasi hasil upload");
    }

    private static HttpURLConnection connection(String url, String token, String method) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setRequestMethod(method);
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

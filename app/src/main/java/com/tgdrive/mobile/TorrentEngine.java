package com.tgdrive.mobile;

import org.json.JSONArray;
import org.json.JSONObject;
import org.libtorrent4j.SessionManager;
import org.libtorrent4j.TorrentInfo;
import org.libtorrent4j.swig.torrent_handle;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.function.BooleanSupplier;

/** Downloads magnet links and .torrent files into a private staging directory. */
public final class TorrentEngine {
    private TorrentEngine() { }
    public static JSONArray download(String link, String localPath, File jobDir,
                                     DownloadService.Callback callback, BooleanSupplier canceled) throws Exception {
        File staging = new File(jobDir, "torrent/files");
        if (!staging.mkdirs() && !staging.isDirectory()) throw new IllegalStateException("Folder torrent tidak tersedia");
        File torrent = localPath == null ? new File(jobDir, "source.torrent") : new File(localPath);
        SessionManager session = new SessionManager();
        try {
            session.start();
            if (localPath == null && link != null && link.startsWith("magnet:?xt=urn:btih:")) {
                callback.onProgress(-1, "Mencari metadata magnet dari peer…");
                byte[] meta = session.fetchMagnet(link, 120, staging);
                if (meta == null || meta.length == 0) throw new IllegalStateException("Metadata magnet tidak tersedia dari peer");
                Files.write(torrent.toPath(), meta);
            } else if (localPath == null && link != null && link.startsWith("https://")) {
                HttpURLConnection connection = (HttpURLConnection) new URL(link).openConnection();
                connection.setConnectTimeout(15000); connection.setReadTimeout(30000);
                try {
                    connection.connect();
                    if (!"https".equalsIgnoreCase(connection.getURL().getProtocol()) || connection.getResponseCode() != 200)
                        throw new IllegalArgumentException("Link .torrent harus HTTPS dan dapat diakses");
                    try (InputStream in = connection.getInputStream(); FileOutputStream out = new FileOutputStream(torrent)) {
                        byte[] buffer = new byte[32768]; int bytes, total = 0;
                        while ((bytes = in.read(buffer)) != -1) {
                            total += bytes;
                            if (total > 8 * 1024 * 1024) throw new IllegalArgumentException("File .torrent lebih dari 8 MB");
                            out.write(buffer, 0, bytes);
                        }
                    }
                } finally { connection.disconnect(); }
            }
            TorrentInfo info = new TorrentInfo(torrent);
            if (!info.isValid() || info.numFiles() < 1) throw new IllegalArgumentException("Metadata torrent tidak valid");
            session.download(info, staging);
            torrent_handle handle = null;
            long deadline = System.currentTimeMillis() + 24L * 60 * 60 * 1000;
            while (System.currentTimeMillis() < deadline) {
                if (canceled.getAsBoolean()) throw new InterruptedException("Torrent dibatalkan");
                if (handle == null || !handle.is_valid()) handle = session.swig().find_torrent(info.swig().info_hash());
                if (handle != null && handle.is_valid()) {
                    var status = handle.status();
                    if (status.getIs_finished() || status.getIs_seeding()) break;
                    callback.onProgress((int) Math.min(99, status.getProgress() * 100),
                        "Torrent · " + status.getNum_peers() + " peer");
                }
                Thread.sleep(1500);
            }
            if (System.currentTimeMillis() >= deadline) throw new IllegalStateException("Torrent belum selesai dalam 24 jam");
        } catch (UnsatisfiedLinkError error) {
            throw new IllegalStateException("Pustaka torrent tidak cocok dengan arsitektur ponsel", error);
        } finally { session.stop(); }
        JSONArray result = new JSONArray();
        ArrayList<File> files = new ArrayList<>();
        collect(staging, files);
        for (File file : files) if (file.length() > 0 && !file.getName().endsWith(".part"))
            result.put(new JSONObject().put("path", file.getAbsolutePath()).put("name", file.getName()));
        if (result.length() == 0) throw new IllegalStateException("Torrent selesai tanpa file hasil");
        return result;
    }
    private static void collect(File root, ArrayList<File> result) {
        File[] list = root.listFiles();
        if (list == null) return;
        for (File file : list) {
            if (Files.isSymbolicLink(file.toPath())) continue;
            if (file.isDirectory()) collect(file, result);
            else if (file.isFile()) result.add(file);
        }
    }
}

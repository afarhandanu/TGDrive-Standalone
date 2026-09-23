package com.tgdrive.mobile;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.webkit.CookieManager;
import com.chaquo.python.Python;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

public class DownloadService extends Service {
    public static final String EVENTS = "com.tgdrive.mobile.JOB_EVENT";
    private static final AtomicLong NEXT_ID = new AtomicLong();
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private volatile boolean canceled;
    private NotificationManager notifications;

    @Override public void onCreate() {
        super.onCreate();
        notifications = getSystemService(NotificationManager.class);
        notifications.createNotificationChannel(new NotificationChannel("downloads", "TGDrive downloads", NotificationManager.IMPORTANCE_LOW));
    }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;
        if ("CANCEL".equals(intent.getAction())) { canceled = true; return START_NOT_STICKY; }
        String url = intent.getStringExtra("url");
        String importPath = intent.getStringExtra("import_path");
        String localPath = intent.getStringExtra("local_path");
        if ((url == null || url.trim().isEmpty()) && importPath == null && localPath == null) return START_NOT_STICKY;
        String target = intent.getStringExtra("target");
        String token = intent.getStringExtra("drive_token");
        String folder = intent.getStringExtra("folder_id");
        String quality = intent.getStringExtra("quality");
        int count = intent.getIntExtra("count", 1);
        long id = NEXT_ID.updateAndGet(last -> Math.max(System.currentTimeMillis(), last + 1));
        boolean subtitles = intent.getBooleanExtra("subtitles", false);
        boolean thumbnail = intent.getBooleanExtra("thumbnail", false);
        boolean metadata = intent.getBooleanExtra("metadata", false);
        String category = intent.getStringExtra("category");
        History.enqueue(this, id, importPath != null ? "Instagram JSON/ZIP import" : localPath != null ? "Local file" : url,
            target, quality, count, folder, subtitles, thumbnail, metadata);
        startForeground(3001, notification("TGDrive", "Menyiapkan antrean…", 0));
        worker.submit(() -> { runJob(id, url, importPath, localPath, category, target, token, folder, quality, count, subtitles, thumbnail, metadata); stopSelf(startId); });
        return START_NOT_STICKY;
    }
    private void runJob(long id, String url, String importPath, String localPath, String category, String target, String token, String folder, String quality, int count,
                        boolean subtitles, boolean thumbnail, boolean metadata) {
        canceled = false;
        File jobDir = new File(getCacheDir(), "job-" + id);
        jobDir.mkdirs();
        File cookieFile = new File(jobDir, "cookies.txt");
        boolean gallery = "gallery".equals(target) || "both".equals(target);
        boolean drive = "drive".equals(target) || "both".equals(target);
        try {
            event(id, "RUNNING", "Mengambil media…", 0);
            Callback callback = new Callback(id);
            String raw;
            if (localPath != null) {
                File local = new File(localPath);
                File localDir = new File(jobDir, "local/files");
                if (!localDir.mkdirs() && !localDir.isDirectory()) throw new IllegalStateException("Gagal membuat folder file lokal");
                File targetFile = new File(localDir, local.getName().replaceFirst("^selected_[0-9]+_", ""));
                Files.copy(local.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                raw = new JSONArray().put(new JSONObject().put("path", targetFile.getAbsolutePath()).put("name", targetFile.getName())).toString();
            } else if (importPath != null) raw = Python.getInstance().getModule("instagram_import")
                .callAttr("import_file", importPath, jobDir.getAbsolutePath(), category == null ? "all" : category, count, callback).toString();
            else {
                writeCookies(url, cookieFile);
                raw = Python.getInstance().getModule("downloader")
                    .callAttr("download", url, jobDir.getAbsolutePath(), quality, count, subtitles, thumbnail, metadata,
                        cookieFile.exists() ? cookieFile.getAbsolutePath() : "", callback).toString();
            }
            JSONArray files = new JSONArray(raw);
            StringBuilder result = new StringBuilder();
            for (int i = 0; i < files.length(); i++) {
                if (canceled) throw new InterruptedException("Dibatalkan");
                File media = new File(files.getJSONObject(i).getString("path"));
                if (!media.getCanonicalPath().startsWith(jobDir.getCanonicalPath() + File.separator))
                    throw new SecurityException("Hasil unduhan keluar dari direktori kerja");
                String relative = jobDir.toPath().relativize(media.toPath()).toString();
                if (gallery) {
                    event(id, "SAVING", "Menyimpan ke Galeri: " + media.getName(), 99);
                    MediaDestination.publish(this, media, relative);
                    result.append("Galeri: ").append(relative).append("\n");
                }
                if (drive) {
                    String parent = DriveFiles.ensurePath(token, folder, relative);
                    event(id, "UPLOADING", "Mengunggah " + media.getName(), 0);
                    String link = DriveUploader.upload(media, token, parent,
                        percent -> event(id, "UPLOADING", "Drive " + percent + "%", Math.min(percent, 99)));
                    result.append("Drive: ").append(link).append("\n");
                }
            }
            event(id, "COMPLETED", result.toString(), 100);
            deleteTree(jobDir);
        } catch (Exception ex) {
            // Keep downloaded files in private cache after a Drive failure so a retry can be added later.
            event(id, canceled ? "CANCELLED" : "FAILED", ex.getMessage() == null ? ex.toString() : ex.getMessage(), 0);
        } finally {
            cookieFile.delete();
            if (localPath != null) new File(localPath).delete();
            if (importPath != null) new File(importPath).delete();
        }
    }
    private void writeCookies(String url, File path) throws Exception {
        String host = android.net.Uri.parse(url).getHost();
        if (host == null) return;
        String base = host.endsWith("instagram.com") ? "instagram.com" :
                      host.endsWith("x.com") || host.endsWith("twitter.com") ? "x.com" : host;
        String cookies = CookieManager.getInstance().getCookie("https://" + base);
        if (cookies == null || cookies.trim().isEmpty()) return;
        StringBuilder text = new StringBuilder("# Netscape HTTP Cookie File\n");
        for (String part : cookies.split(";")) {
            String[] pair = part.trim().split("=", 2);
            if (pair.length == 2) text.append(".").append(base).append("\tTRUE\t/\tTRUE\t2147483647\t")
                .append(pair[0]).append("\t").append(pair[1]).append("\n");
        }
        Files.write(path.toPath(), text.toString().getBytes(StandardCharsets.UTF_8));
    }
    private void event(long id, String state, String message, int progress) {
        History.record(this, id, state, message, progress);
        Intent event = new Intent(EVENTS).setPackage(getPackageName());
        event.putExtra("id", id).putExtra("state", state).putExtra("message", message).putExtra("progress", progress);
        sendBroadcast(event);
        notifications.notify(3001, notification(state, message, progress));
    }
    private Notification notification(String title, String body, int progress) {
        return new Notification.Builder(this, "downloads")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title).setContentText(body).setProgress(100, progress, progress <= 0)
            .setOngoing(!title.equals("COMPLETED") && !title.equals("FAILED") && !title.equals("CANCELLED"))
            .build();
    }
    private void deleteTree(File file) {
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteTree(child);
        file.delete();
    }
    @Override public void onDestroy() { worker.shutdownNow(); super.onDestroy(); }
    @Override public IBinder onBind(Intent intent) { return null; }
    public class Callback {
        private final long id;
        public Callback(long id) { this.id = id; }
        public void onProgress(int percent, String speed) {
            if (canceled) throw new IllegalStateException("Dibatalkan");
            event(id, "RUNNING", percent >= 100 ? "Unduhan selesai; menyiapkan penyimpanan" :
                percent < 0 ? speed : percent + "% · " + speed, Math.min(percent, 99));
        }
    }
}

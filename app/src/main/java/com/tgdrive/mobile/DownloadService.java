package com.tgdrive.mobile;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import com.chaquo.python.Python;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicInteger;

public class DownloadService extends Service {
    public static final String EVENTS = "com.tgdrive.mobile.JOB_EVENT";
    private static final AtomicLong NEXT_ID = new AtomicLong();
    private static final AtomicInteger PENDING = new AtomicInteger();
    private static final Object PAUSE_LOCK = new Object();
    private static volatile boolean paused;
    public static boolean hasPendingJobs() { return PENDING.get() > 0; }
    public static boolean isPaused() { return paused; }
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
        if ("PAUSE".equals(intent.getAction())) { paused = true; return START_NOT_STICKY; }
        if ("RESUME".equals(intent.getAction())) {
            synchronized (PAUSE_LOCK) { paused = false; PAUSE_LOCK.notifyAll(); }
            return START_NOT_STICKY;
        }
        String url = intent.getStringExtra("url");
        String importPath = intent.getStringExtra("import_path");
        String localPath = intent.getStringExtra("local_path");
        String muxAudioPath = intent.getStringExtra("mux_audio_path");
        String torrentPath = intent.getStringExtra("torrent_path");
        if ((url == null || url.trim().isEmpty()) && importPath == null && localPath == null && torrentPath == null) return START_NOT_STICKY;
        String target = intent.getStringExtra("target");
        String token = intent.getStringExtra("drive_token");
        String folder = intent.getStringExtra("folder_id");
        String quality = intent.getStringExtra("quality");
        int count = intent.getIntExtra("count", 1);
        long id = NEXT_ID.updateAndGet(last -> Math.max(System.currentTimeMillis(), last + 1));
        boolean subtitles = intent.getBooleanExtra("subtitles", false);
        boolean thumbnail = intent.getBooleanExtra("thumbnail", false);
        boolean metadata = intent.getBooleanExtra("metadata", false);
        boolean anonymous = intent.getBooleanExtra("anonymous", false);
        boolean skipDrive = intent.getBooleanExtra("skip_drive", false);
        boolean verifyDrive = intent.getBooleanExtra("verify_drive", false);
        boolean albumMode = intent.getBooleanExtra("album_mode", false);
        String profileContent = intent.getStringExtra("profile_content");
        String category = intent.getStringExtra("category");
        String dateFrom = intent.getStringExtra("date_from");
        String dateTo = intent.getStringExtra("date_to");
        String importOutput = intent.getStringExtra("import_output");
        History.enqueue(this, id, importPath != null ? "Instagram JSON/ZIP import" : localPath != null ? "Local file" : torrentPath != null ? "Torrent file" : url,
            target, quality, count, folder, subtitles, thumbnail, metadata, anonymous, skipDrive,
            albumMode, profileContent == null ? "all" : profileContent, dateFrom, dateTo, verifyDrive);
        startForeground(3001, notification("TGDrive", "Menyiapkan antrean…", 0));
        PENDING.incrementAndGet();
        worker.submit(() -> {
            try {
                synchronized (PAUSE_LOCK) { while (paused) PAUSE_LOCK.wait(); }
                runJob(id, url, importPath, localPath, muxAudioPath, torrentPath, category, target, token, folder, quality, count, subtitles, thumbnail, metadata,
                    dateFrom == null ? "" : dateFrom, dateTo == null ? "" : dateTo, importOutput == null ? "folder" : importOutput,
                    anonymous, skipDrive, albumMode, profileContent == null ? "all" : profileContent, verifyDrive);
            } catch (InterruptedException e) { event(id, "INTERRUPTED", "Antrean terhenti; tugas dapat diulang", 0); }
            finally { PENDING.decrementAndGet(); stopSelf(startId); }
        });
        return START_NOT_STICKY;
    }
    private void runJob(long id, String url, String importPath, String localPath, String muxAudioPath, String torrentPath, String category, String target, String token, String folder, String quality, int count,
                        boolean subtitles, boolean thumbnail, boolean metadata, String dateFrom, String dateTo,
                        String importOutput, boolean anonymous, boolean skipDrive, boolean albumMode,
                        String profileContent, boolean verifyDrive) {
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
            if (torrentPath != null || (url != null && (url.startsWith("magnet:?") || url.toLowerCase(java.util.Locale.ROOT).matches("^https://[^?#]+\\.torrent(?:\\?.*)?$")))) {
                raw = TorrentEngine.download(url, torrentPath, jobDir, callback, () -> canceled).toString();
            } else if (localPath != null) {
                File local = new File(localPath);
                File localDir = new File(jobDir, "local/files");
                if (!localDir.mkdirs() && !localDir.isDirectory()) throw new IllegalStateException("Gagal membuat folder file lokal");
                File targetFile = new File(localDir, local.getName().replaceFirst("^selected_[0-9]+_", ""));
                Files.copy(local.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                if (muxAudioPath != null) {
                    File sourceAudio = new File(muxAudioPath);
                    File audio = new File(jobDir, "mux_audio_" + id + ".m4a");
                    Files.copy(sourceAudio.toPath(), audio.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    event(id, "RUNNING", "Menggabungkan video + audio dengan FFmpeg", 98);
                    MediaMux.merge(targetFile, audio);
                }
                raw = new JSONArray().put(new JSONObject().put("path", targetFile.getAbsolutePath()).put("name", targetFile.getName())).toString();
            } else if (importPath != null) raw = Python.getInstance().getModule("instagram_import")
                .callAttr("import_file", importPath, jobDir.getAbsolutePath(), category == null ? "all" : category, count, callback,
                    dateFrom, dateTo, importOutput).toString();
            else {
                if (!anonymous) writeCookies(url, cookieFile);
                if (albumMode) raw = Python.getInstance().getModule("gallery_download")
                    .callAttr("download", url, jobDir.getAbsolutePath(), count,
                        cookieFile.exists() ? cookieFile.getAbsolutePath() : "", callback,
                        dateFrom, dateTo, profileContent, quality, subtitles, thumbnail, metadata).toString();
                else raw = Python.getInstance().getModule("downloader")
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
                String audioPath = files.getJSONObject(i).optString("audio_path", "");
                if (!audioPath.isEmpty()) {
                    File audio = new File(audioPath);
                    if (!audio.getCanonicalPath().startsWith(jobDir.getCanonicalPath() + File.separator))
                        throw new SecurityException("Audio keluar dari direktori kerja");
                    event(id, "RUNNING", "Menggabungkan audio + video dengan FFmpeg", 98);
                    MediaMux.merge(media, audio);
                }
                String relative = jobDir.toPath().relativize(media.toPath()).toString();
                if (gallery) {
                    event(id, "SAVING", "Menyimpan ke lokal: " + media.getName(), 99);
                    MediaDestination.publish(this, media, relative);
                    result.append("Lokal: ").append(relative).append("\n");
                }
                if (drive) {
                    String parent = DriveFiles.ensurePath(token, folder, relative);
                    if (skipDrive && DriveFiles.hasFile(token, parent, media.getName())) {
                        result.append("Drive sudah ada: ").append(relative).append("\n");
                        continue;
                    }
                    event(id, "UPLOADING", "Mengunggah " + media.getName(), 0);
                    String link = DriveUploader.upload(media, token, parent,
                        percent -> event(id, "UPLOADING", "Drive " + percent + "%", Math.min(percent, 99)), verifyDrive);
                    result.append("Drive: ").append(link).append("\n");
                }
            }
            event(id, "COMPLETED", result.toString(), 100);
            deleteTree(jobDir);
        } catch (Exception ex) {
            // Keep downloaded files in private cache after a Drive failure so a retry can be added later.
            if (!canceled) ErrorLog.record(this, "Unduhan #" + id, ex);
            event(id, canceled ? "CANCELLED" : "FAILED", ex.getMessage() == null ? ex.toString() : ex.getMessage(), 0);
        } finally {
            cookieFile.delete();
            if (localPath != null) new File(localPath).delete();
            if (muxAudioPath != null) new File(muxAudioPath).delete();
            if (importPath != null) new File(importPath).delete();
            if (torrentPath != null) new File(torrentPath).delete();
        }
    }
    private void writeCookies(String url, File path) throws Exception {
        String host = WebSessions.host(url);
        if (host == null) return;
        LinkedHashSet<String> domains = new LinkedHashSet<>();
        domains.add(host);
        if (host.startsWith("www.")) domains.add(host.substring(4));
        else domains.add("www." + host);
        if (host.equals("x.com") || host.endsWith(".x.com") ||
            host.equals("twitter.com") || host.endsWith(".twitter.com")) {
            domains.add("x.com"); domains.add("www.x.com");
            domains.add("twitter.com"); domains.add("www.twitter.com");
        } else if (host.equals("instagram.com") || host.endsWith(".instagram.com")) {
            domains.add("instagram.com"); domains.add("www.instagram.com");
        }
        StringBuilder text = new StringBuilder("# Netscape HTTP Cookie File\n");
        for (String domain : domains) {
            String cookies = WebSessions.cookies(this, domain);
            if (cookies == null) continue;
            for (String part : cookies.split(";")) {
                String[] pair = part.trim().split("=", 2);
                if (pair.length == 2 && pair[0].matches("[!#$%&'*+.^_`|~0-9a-zA-Z-]+") &&
                    pair[1].indexOf('\n') < 0 && pair[1].indexOf('\r') < 0 && pair[1].indexOf('\t') < 0)
                    text.append(".").append(domain).append("\tTRUE\t/\tTRUE\t")
                        .append((System.currentTimeMillis() / 1000) + 86400).append("\t")
                        .append(pair[0]).append("\t").append(pair[1]).append("\n");
            }
        }
        if (text.indexOf("\t") >= 0)
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
        public void onMux(String videoPath, String audioPath) throws Exception {
            File root = new File(getCacheDir(), "job-" + id).getCanonicalFile();
            File video = new File(videoPath).getCanonicalFile(), audio = new File(audioPath).getCanonicalFile();
            if (!video.toPath().startsWith(root.toPath()) || !audio.toPath().startsWith(root.toPath()))
                throw new SecurityException("Media mux keluar dari direktori kerja");
            if (canceled) throw new InterruptedException("Dibatalkan");
            event(id, "RUNNING", "Menggabungkan audio + video dengan FFmpeg", 98);
            MediaMux.merge(video, audio);
        }
        public void onProgress(int percent, String speed) {
            if (canceled) throw new IllegalStateException("Dibatalkan");
            event(id, "RUNNING", percent >= 100 ? "Unduhan selesai; menyiapkan penyimpanan" :
                percent < 0 ? speed : percent + "% · " + speed, Math.min(percent, 99));
        }
    }
}

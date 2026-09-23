package com.tgdrive.mobile;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;

public final class History {
    private History() {}
    public static synchronized void enqueue(Context context, long id, String url, String target,
            String quality, int count, String folder, boolean subtitles, boolean thumbnail, boolean metadata,
            boolean anonymous, boolean skipDrive, boolean albumMode, String profileContent,
            String dateFrom, String dateTo, boolean verifyDrive) {
        record(context, id, "QUEUED", url, 0);
        try {
            var prefs = context.getSharedPreferences("jobs", Context.MODE_PRIVATE);
            JSONArray history = new JSONArray(prefs.getString("history", "[]"));
            JSONObject job = history.getJSONObject(0);
            job.put("url", url).put("target", target).put("quality", quality).put("count", count)
                .put("folder", folder).put("subtitles", subtitles).put("thumbnail", thumbnail)
                .put("metadata", metadata).put("anonymous", anonymous).put("skip_drive", skipDrive)
                .put("album_mode", albumMode).put("profile_content", profileContent)
                .put("date_from", dateFrom).put("date_to", dateTo).put("verify_drive", verifyDrive);
            prefs.edit().putString("history", history.toString()).apply();
        } catch (Exception ignored) { }
    }
    public static synchronized void record(Context context, long id, String state, String message, int progress) {
        try {
            var prefs = context.getSharedPreferences("jobs", Context.MODE_PRIVATE);
            JSONArray prior = new JSONArray(prefs.getString("history", "[]"));
            JSONArray next = new JSONArray();
            JSONObject item = new JSONObject().put("id", id).put("state", state).put("message", message).put("progress", progress);
            for (int i = 0; i < prior.length(); i++) {
                JSONObject old = prior.optJSONObject(i);
                if (old != null && old.optLong("id") == id) {
                    for (String field : new String[]{"url", "target", "quality", "count", "folder", "subtitles", "thumbnail", "metadata", "anonymous", "skip_drive", "album_mode", "profile_content", "date_from", "date_to", "verify_drive"})
                        if (old.has(field)) item.put(field, old.get(field));
                    break;
                }
            }
            next.put(item);
            for (int i = 0; i < Math.min(prior.length(), 99); i++) {
                JSONObject previousItem = prior.getJSONObject(i);
                if (previousItem.optLong("id") != id) next.put(previousItem);
            }
            prefs.edit().putString("history", next.toString()).apply();
        } catch (Exception ignored) { }
    }
    public static JSONArray read(Context context) {
        try { return new JSONArray(context.getSharedPreferences("jobs", Context.MODE_PRIVATE).getString("history", "[]")); }
        catch (Exception e) { return new JSONArray(); }
    }
    public static JSONObject lastRetryable(Context context) {
        JSONArray jobs = read(context);
        for (int i = 0; i < jobs.length(); i++) {
            JSONObject job = jobs.optJSONObject(i);
            if (job != null && ("FAILED".equals(job.optString("state")) || "CANCELLED".equals(job.optString("state"))
                    || "INTERRUPTED".equals(job.optString("state")))
                    && job.optString("url").startsWith("http")) return job;
        }
        return null;
    }
    public static synchronized void markInterrupted(Context context) {
        JSONArray jobs = read(context);
        boolean changed = false;
        for (int i = 0; i < jobs.length(); i++) {
            JSONObject job = jobs.optJSONObject(i);
            if (job == null) continue;
            String state = job.optString("state");
            if ("QUEUED".equals(state) || "RUNNING".equals(state) || "SAVING".equals(state) || "UPLOADING".equals(state)) {
                try { job.put("state", "INTERRUPTED").put("message", "Aplikasi berhenti saat tugas berjalan. Pilih Ulangi untuk mencoba lagi.");
                    changed = true; } catch (Exception ignored) { }
            }
        }
        if (changed) context.getSharedPreferences("jobs", Context.MODE_PRIVATE).edit().putString("history", jobs.toString()).apply();
    }
    public static boolean alreadyCompleted(Context context, String url) {
        String key = canonical(url);
        JSONArray jobs = read(context);
        for (int i = 0; i < jobs.length(); i++) {
            JSONObject job = jobs.optJSONObject(i);
            if (job != null && "COMPLETED".equals(job.optString("state"))
                    && key.equals(canonical(job.optString("url")))) return true;
        }
        return false;
    }
    private static String canonical(String raw) {
        try {
            android.net.Uri uri = android.net.Uri.parse(raw);
            String host = uri.getHost();
            if (host == null) return raw;
            host = host.toLowerCase(java.util.Locale.ROOT).replaceFirst("^www\\.", "");
            if ("twitter.com".equals(host)) host = "x.com";
            String path = uri.getPath() == null ? "/" : uri.getPath().replaceAll("/+$", "");
            if (!(host.equals("instagram.com") || host.equals("x.com"))) return raw;
            return host + path;
        } catch (Exception e) { return raw; }
    }
}

package com.tgdrive.mobile;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;

public final class History {
    private History() {}
    public static synchronized void enqueue(Context context, long id, String url, String target,
            String quality, int count, String folder, boolean subtitles, boolean thumbnail, boolean metadata) {
        record(context, id, "QUEUED", url, 0);
        try {
            var prefs = context.getSharedPreferences("jobs", Context.MODE_PRIVATE);
            JSONArray history = new JSONArray(prefs.getString("history", "[]"));
            JSONObject job = history.getJSONObject(0);
            job.put("url", url).put("target", target).put("quality", quality).put("count", count)
                .put("folder", folder).put("subtitles", subtitles).put("thumbnail", thumbnail)
                .put("metadata", metadata);
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
                    for (String field : new String[]{"url", "target", "quality", "count", "folder", "subtitles", "thumbnail", "metadata"})
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
            if (job != null && ("FAILED".equals(job.optString("state")) || "CANCELLED".equals(job.optString("state")))
                    && job.optString("url").startsWith("http")) return job;
        }
        return null;
    }
}

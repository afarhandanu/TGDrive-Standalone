package com.tgdrive.mobile;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/** Portable history and saved-site backup; account credentials never leave Android storage. */
final class AppBackup {
    private AppBackup() {}

    static byte[] exportData(Context context) throws Exception {
        return new JSONObject().put("format", "tgdrive-standalone-backup")
            .put("version", 1).put("history", History.read(context))
            .put("sites", SavedSites.list(context)).toString(2).getBytes(StandardCharsets.UTF_8);
    }

    static void restoreData(Context context, InputStream input) throws Exception {
        ByteArrayOutputStream content = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192]; int count;
        while ((count = input.read(buffer)) != -1) {
            content.write(buffer, 0, count);
            if (content.size() > 2 * 1024 * 1024) throw new IllegalArgumentException("Backup lebih dari 2 MB");
        }
        byte[] bytes = content.toByteArray();
        if (bytes.length > 2 * 1024 * 1024) throw new IllegalArgumentException("Backup lebih dari 2 MB");
        JSONObject backup = new JSONObject(new String(bytes, StandardCharsets.UTF_8));
        if (!"tgdrive-standalone-backup".equals(backup.optString("format")) || backup.optInt("version") != 1)
            throw new IllegalArgumentException("Format backup tidak dikenali");
        JSONArray incoming = backup.getJSONArray("history");
        JSONArray entries = new JSONArray();
        for (int i = 0; i < Math.min(incoming.length(), 100); i++) {
            JSONObject job = incoming.optJSONObject(i);
            if (job != null && job.optString("url").length() < 4096) {
                JSONObject safe = new JSONObject(job.toString());
                if ("RUNNING".equals(safe.optString("state")) || "QUEUED".equals(safe.optString("state")))
                    safe.put("state", "INTERRUPTED");
                entries.put(safe);
            }
        }
        JSONArray sites = backup.getJSONArray("sites");
        for (int i = sites.length() - 1; i >= 0; i--) {
            JSONObject site = sites.optJSONObject(i);
            if (site != null) {
                String address = site.optString("url");
                if (WebSessions.host(address) != null) SavedSites.remember(context, address);
            }
        }
        context.getSharedPreferences("jobs", Context.MODE_PRIVATE).edit()
            .putString("history", entries.toString()).apply();
    }
}

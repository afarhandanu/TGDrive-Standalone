package com.tgdrive.mobile;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.time.Instant;
import android.util.Log;

/** Small device-local diagnostic history, separate from downloadable job history. */
public final class ErrorLog {
    private static final String PREFS = "diagnostics";
    private static final int MAX_ENTRIES = 30;
    private ErrorLog() {}

    public static void record(Context context, String area, Throwable error) {
        record(context, area, Log.getStackTraceString(error));
    }

    public static synchronized void record(Context context, String area, String message) {
        try {
            var prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            JSONArray old = new JSONArray(prefs.getString("errors", "[]"));
            JSONArray next = new JSONArray();
            next.put(new JSONObject().put("time", Instant.now().toString())
                .put("area", area).put("message", clean(message)));
            for (int i = 0; i < Math.min(old.length(), MAX_ENTRIES - 1); i++) next.put(old.get(i));
            prefs.edit().putString("errors", next.toString()).apply();
        } catch (Exception ignored) { }
    }

    public static synchronized String read(Context context) {
        try {
            JSONArray entries = new JSONArray(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString("errors", "[]"));
            if (entries.length() == 0) return "Belum ada error yang dicatat pada perangkat ini.";
            StringBuilder result = new StringBuilder();
            for (int i = 0; i < entries.length(); i++) {
                JSONObject item = entries.getJSONObject(i);
                if (i > 0) result.append("\n\n");
                result.append(item.optString("time")).append(" · ").append(item.optString("area"))
                    .append("\n").append(item.optString("message"));
            }
            return result.toString();
        } catch (Exception error) { return "Log error tidak dapat dibaca: " + error.getClass().getSimpleName(); }
    }

    private static String clean(String raw) {
        String value = raw == null ? "Error tanpa rincian" : raw;
        value = value.replaceAll("(?i)((?:sessionid|csrftoken|authorization|access_token|refresh_token|cookie|token)\\s*[:=]\\s*)[^\\s&;,]+", "$1[disembunyikan]");
        value = value.replaceAll("(?i)(Bearer\\s+)[^\\s,;]+", "$1[disembunyikan]");
        value = value.replaceAll("(https://[^\\s?#]+)\\?[^\\s)]+", "$1?[parameter disembunyikan]");
        return value.length() > 6000 ? value.substring(0, 6000) + "…" : value;
    }
}

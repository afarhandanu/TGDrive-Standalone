package com.tgdrive.mobile;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.net.URI;

/** Remembers login page addresses only. Account cookies stay in WebSessions. */
final class SavedSites {
    private static final String PREFS = "saved_login_sites";
    private static final String KEY = "sites";
    private static final int MAX_SITES = 24;

    private SavedSites() {}

    static JSONArray list(Context context) {
        try { return new JSONArray(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]")); }
        catch (Exception e) { return new JSONArray(); }
    }

    static void remember(Context context, String address) {
        String host = WebSessions.host(address);
        if (host == null) throw new IllegalArgumentException("Gunakan URL HTTPS situs yang valid");
        String safeUrl;
        try {
            URI url = new URI(address);
            safeUrl = new URI("https", null, host, url.getPort(),
                url.getPath().isEmpty() ? "/" : url.getPath(), null, null).toASCIIString();
        } catch (Exception e) { throw new IllegalArgumentException("URL situs tidak valid"); }
        JSONArray old = list(context), next = new JSONArray();
        try { next.put(new JSONObject().put("host", host).put("url", safeUrl)); }
        catch (Exception e) { throw new IllegalArgumentException("Gagal menyimpan situs"); }
        for (int i = 0; i < old.length() && next.length() < MAX_SITES; i++) {
            JSONObject site = old.optJSONObject(i);
            if (site != null && !host.equals(site.optString("host"))) next.put(site);
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, next.toString()).apply();
    }

    static void forget(Context context, String host) {
        JSONArray old = list(context), next = new JSONArray();
        for (int i = 0; i < old.length(); i++) {
            JSONObject site = old.optJSONObject(i);
            if (site != null && !host.equals(site.optString("host"))) next.put(site);
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, next.toString()).apply();
    }
}

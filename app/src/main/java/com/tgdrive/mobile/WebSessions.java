package com.tgdrive.mobile;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import android.webkit.CookieManager;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.Map;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Private, device-bound fallback for WebView session cookies. No cookie leaves app storage. */
final class WebSessions {
    private static final String PREFS = "web_sessions";
    private static final String KEY = "tgdrive_web_sessions";
    private static final long MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000;

    private WebSessions() {}

    static String host(String url) {
        try {
            URI uri = new URI(url);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getUserInfo() != null) return null;
            String host = uri.getHost();
            if (host == null || host.isEmpty()) return null;
            host = host.toLowerCase(java.util.Locale.ROOT);
            return host.endsWith(".") ? host.substring(0, host.length() - 1) : host;
        } catch (Exception e) { return null; }
    }

    static void save(Context context, String host) {
        if (host == null) return;
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        try {
            String cookies = CookieManager.getInstance().getCookie("https://" + host + "/");
            if (cookies == null || cookies.trim().isEmpty()) {
                prefs.edit().remove(host).apply();
                return;
            }
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key());
            byte[] encrypted = cipher.doFinal(cookies.getBytes(StandardCharsets.UTF_8));
            prefs.edit().putString(host, System.currentTimeMillis() + ":" +
                Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP) + ":" +
                Base64.encodeToString(encrypted, Base64.NO_WRAP)).apply();
        } catch (Exception ignored) { /* WebView's own cookie store remains available. */ }
    }

    static void restore(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        for (Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
            String host = entry.getKey();
            if (!host.equals(host("https://" + host + "/")) || !(entry.getValue() instanceof String)) continue;
            long remainingSeconds;
            try {
                String stamp = (String) entry.getValue();
                remainingSeconds = Math.max(1L,
                    (Long.parseLong(stamp.substring(0, stamp.indexOf(':'))) + MAX_AGE_MS - System.currentTimeMillis()) / 1000);
            } catch (Exception e) { continue; }
            String current = CookieManager.getInstance().getCookie("https://" + host + "/");
            String saved = decrypt(prefs, host);
            if (saved == null) continue;
            // Restore only missing cookies: fresh cookies from the website always win.
            for (String pair : saved.split(";")) {
                pair = pair.trim();
                int eq = pair.indexOf('=');
                if (eq < 1 || pair.indexOf('\n') >= 0 || pair.indexOf('\r') >= 0) continue;
                String name = pair.substring(0, eq);
                if (containsCookie(current, name)) continue;
                CookieManager.getInstance().setCookie("https://" + host + "/",
                    pair + "; Path=/; Secure; Max-Age=" + remainingSeconds +
                    (("auth_token".equalsIgnoreCase(name) || "sessionid".equalsIgnoreCase(name)) ? "; HttpOnly" : ""));
            }
        }
        CookieManager.getInstance().flush();
    }

    static String cookies(Context context, String host) {
        if (host == null) return null;
        String current = CookieManager.getInstance().getCookie("https://" + host + "/");
        String saved = decrypt(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE), host);
        if (saved == null) return current;
        if (current == null || current.isEmpty()) return saved;
        StringBuilder merged = new StringBuilder(current);
        for (String pair : saved.split(";")) {
            pair = pair.trim();
            int eq = pair.indexOf('=');
            if (eq > 0 && !containsCookie(current, pair.substring(0, eq))) merged.append("; ").append(pair);
        }
        return merged.toString();
    }

    static boolean hasXSession(Context context) {
        for (String host : new String[]{"x.com", "www.x.com", "twitter.com", "www.twitter.com"})
            if (containsCookie(cookies(context, host), "auth_token")) return true;
        return false;
    }

    private static boolean containsCookie(String header, String name) {
        if (header == null) return false;
        for (String part : header.split(";"))
            if (part.trim().startsWith(name + "=")) return true;
        return false;
    }

    private static String decrypt(SharedPreferences prefs, String host) {
        String value = prefs.getString(host, null);
        if (value == null) return null;
        try {
            String[] fields = value.split(":", 3);
            if (fields.length != 3 || System.currentTimeMillis() - Long.parseLong(fields[0]) > MAX_AGE_MS) {
                prefs.edit().remove(host).apply(); return null;
            }
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, Base64.decode(fields[1], Base64.NO_WRAP)));
            return new String(cipher.doFinal(Base64.decode(fields[2], Base64.NO_WRAP)), StandardCharsets.UTF_8);
        } catch (Exception e) { prefs.edit().remove(host).apply(); return null; }
    }

    private static SecretKey key() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        if (store.containsAlias(KEY)) return (SecretKey) store.getKey(KEY, null);
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(KEY,
            KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
        return generator.generateKey();
    }
}

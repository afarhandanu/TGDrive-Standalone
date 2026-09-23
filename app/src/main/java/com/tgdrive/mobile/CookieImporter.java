package com.tgdrive.mobile;

import android.content.Context;
import android.webkit.CookieManager;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

/** Parse a user-selected Netscape cookies.txt for one HTTPS host. */
final class CookieImporter {
    private CookieImporter() {}

    static ArrayList<String> parse(InputStream input, String host) throws Exception {
        ArrayList<String> cookies = new ArrayList<>();
        if (!host.matches("[a-z0-9.-]{1,253}")) throw new IllegalArgumentException("Host tidak valid");
        BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
        String line; int total = 0;
        while ((line = reader.readLine()) != null) {
            total += line.length();
            if (total > 2 * 1024 * 1024 || cookies.size() > 300)
                throw new IllegalArgumentException("File cookies terlalu besar");
            boolean httpOnly = line.startsWith("#HttpOnly_");
            if (httpOnly) line = line.substring("#HttpOnly_".length());
            else if (line.startsWith("#") || line.trim().isEmpty()) continue;
            String[] fields = line.split("\\t", 7);
            if (fields.length != 7) continue;
            String domain = fields[0].toLowerCase(java.util.Locale.ROOT).replaceFirst("^\\.", "");
            if (!domain.matches("[a-z0-9.-]{1,253}") || domain.startsWith(".") || domain.endsWith(".")) continue;
            if (!host.equals(domain) && !host.endsWith("." + domain)) continue;
            String name = fields[5], value = fields[6], path = fields[2];
            if (!name.matches("[!#$%&'*+.^_`|~0-9a-zA-Z-]{1,128}") ||
                value.contains("\r") || value.contains("\n") || value.contains(";") ||
                !path.startsWith("/") || path.contains(";") || path.contains("\r") ||
                !"TRUE".equalsIgnoreCase(fields[3])) continue;
            long expiry;
            try { expiry = Long.parseLong(fields[4]); } catch (NumberFormatException e) { continue; }
            if (expiry > 0 && expiry < System.currentTimeMillis() / 1000L) continue;
            String cookie = name + "=" + value + "; Domain=" + fields[0] + "; Path=" + path + "; Secure";
            if (httpOnly) cookie += "; HttpOnly";
            if (expiry > 0) cookie += "; Max-Age=" + Math.min(expiry - System.currentTimeMillis() / 1000L, 7L * 86400);
            cookies.add(cookie);
        }
        if (cookies.isEmpty()) throw new IllegalArgumentException("Tidak ada cookies HTTPS yang cocok untuk " + host);
        return cookies;
    }

    static void install(Context context, String host, ArrayList<String> cookies, Runnable completed) {
        CookieManager manager = CookieManager.getInstance();
        final int[] remaining = {cookies.size()};
        for (String cookie : cookies) manager.setCookie("https://" + host + "/", cookie, accepted -> {
            if (--remaining[0] == 0) {
                manager.flush();
                WebSessions.save(context, host);
                completed.run();
            }
        });
    }
}

package com.tgdrive.mobile;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** A small Drive v3 file manager; requests stay on the phone. */
public final class DriveFiles {
    private static final String ROOT = "https://www.googleapis.com/drive/v3/files";
    private DriveFiles() {}
    public static JSONArray list(String token, String folder) throws Exception {
        String q = "'" + folder.replace("'", "\\'") + "' in parents and trashed = false";
        String uri = ROOT + "?fields=nextPageToken,files(id,name,mimeType,size,webViewLink,parents)"
            + "&pageSize=100&q=" + enc(q);
        JSONArray output = new JSONArray();
        String page = null;
        do {
            String url = uri + (page == null ? "" : "&pageToken=" + enc(page));
            JSONObject result = request(url, token, "GET", null);
            JSONArray part = result.optJSONArray("files");
            if (part != null) for (int i = 0; i < part.length(); i++) output.put(part.get(i));
            page = result.optString("nextPageToken", "");
        } while (!page.isEmpty() && output.length() < 1000);
        return output;
    }
    public static JSONArray search(String token, String name) throws Exception {
        String q = "name contains '" + name.replace("'", "\\'") + "' and trashed = false";
        return request(ROOT + "?fields=files(id,name,mimeType,size,webViewLink,parents)&pageSize=100&q=" + enc(q), token, "GET", null).getJSONArray("files");
    }
    public static void rename(String token, String id, String name) throws Exception {
        request(ROOT + "/" + enc(id) + "?fields=id", token, "PATCH", new JSONObject().put("name", name));
    }
    public static void trash(String token, String id) throws Exception {
        request(ROOT + "/" + enc(id) + "?fields=id", token, "PATCH", new JSONObject().put("trashed", true));
    }
    public static void createFolder(String token, String parent, String name) throws Exception {
        request(ROOT + "?fields=id", token, "POST", new JSONObject().put("name", name)
            .put("mimeType", "application/vnd.google-apps.folder").put("parents", new JSONArray().put(parent)));
    }
    public static String publicLink(String token, String id) throws Exception {
        request(ROOT + "/" + enc(id) + "/permissions?fields=id", token, "POST",
            new JSONObject().put("type", "anyone").put("role", "reader"));
        return "https://drive.google.com/file/d/" + id + "/view";
    }
    private static JSONObject request(String uri, String token, String method, JSONObject data) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(uri).openConnection();
        c.setRequestMethod(method); c.setConnectTimeout(30000); c.setReadTimeout(60000);
        c.setRequestProperty("Authorization", "Bearer " + token);
        if (data != null) {
            c.setRequestProperty("Content-Type", "application/json; charset=UTF-8"); c.setDoOutput(true);
            byte[] bytes = data.toString().getBytes(StandardCharsets.UTF_8);
            c.setFixedLengthStreamingMode(bytes.length);
            try (var out = c.getOutputStream()) { out.write(bytes); }
        }
        int code = c.getResponseCode();
        if (code < 200 || code > 299) throw new IOException("Google Drive HTTP " + code);
        try (var in = c.getInputStream(); var out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192]; int n;
            while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
            return new JSONObject(new String(out.toByteArray(), StandardCharsets.UTF_8));
        }
        finally { c.disconnect(); }
    }
    private static String enc(String text) {
        try { return URLEncoder.encode(text, "UTF-8"); }
        catch (java.io.UnsupportedEncodingException e) { throw new AssertionError(e); }
    }
}

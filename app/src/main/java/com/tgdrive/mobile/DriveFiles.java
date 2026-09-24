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
    public static String accountLabel(String token) throws Exception {
        JSONObject user = request("https://www.googleapis.com/drive/v3/about?fields=user(emailAddress,displayName)", token, "GET", null)
            .getJSONObject("user");
        String email = user.optString("emailAddress", "");
        if (email.isEmpty()) throw new IOException("Drive tidak memberikan alamat akun. Pilih akun lain lalu coba lagi.");
        return email;
    }
    /** The optional folder ID is the parent of The Great Drive/site/username/category. */
    public static String ensurePath(String token, String baseId, String relativeFile) throws Exception {
        String parent = baseId == null || baseId.trim().isEmpty() ? "root" : baseId.trim();
        parent = ensureFolder(token, parent, "The Great Drive");
        String[] pieces = relativeFile.replace('\\', '/').split("/");
        for (int i = 0; i < pieces.length - 1; i++) {
            if (!pieces[i].isEmpty() && !pieces[i].equals(".") && !pieces[i].equals(".."))
                parent = ensureFolder(token, parent, pieces[i]);
        }
        return parent;
    }
    private static String ensureFolder(String token, String parent, String name) throws Exception {
        String q = "'" + parent.replace("'", "\\'") + "' in parents and name = '" +
            name.replace("\\", "\\\\").replace("'", "\\'") +
            "' and mimeType = 'application/vnd.google-apps.folder' and trashed = false";
        JSONArray existing = request(ROOT + "?fields=files(id)&pageSize=1&q=" + enc(q), token, "GET", null)
            .getJSONArray("files");
        if (existing.length() > 0) return existing.getJSONObject(0).getString("id");
        return request(ROOT + "?fields=id", token, "POST", new JSONObject()
            .put("name", name).put("mimeType", "application/vnd.google-apps.folder")
            .put("parents", new JSONArray().put(parent))).getString("id");
    }
    public static JSONArray list(String token, String folder) throws Exception {
        String q = "'" + folder.replace("'", "\\'") + "' in parents and trashed = false";
        String uri = ROOT + "?fields=nextPageToken,files(id,name,mimeType,size,webViewLink,parents,starred)"
            + "&pageSize=100&q=" + enc(q);
        JSONArray output = new JSONArray();
        String page = null;
        do {
            String url = uri + (page == null ? "" : "&pageToken=" + enc(page));
            JSONObject result = request(url, token, "GET", null);
            JSONArray part = result.optJSONArray("files");
            if (part != null) for (int i = 0; i < part.length(); i++) output.put(part.get(i));
            page = result.optString("nextPageToken", "");
        } while (!page.isEmpty() && output.length() < 10000);
        return output;
    }
    public static JSONArray search(String token, String name) throws Exception {
        String q = "name contains '" + name.replace("'", "\\'") + "' and trashed = false";
        String uri = ROOT + "?fields=nextPageToken,files(id,name,mimeType,size,webViewLink,parents,starred)"
            + "&pageSize=100&q=" + enc(q);
        JSONArray output = new JSONArray();
        String page = "";
        do {
            JSONObject result = request(uri + (page.isEmpty() ? "" : "&pageToken=" + enc(page)), token, "GET", null);
            JSONArray files = result.optJSONArray("files");
            if (files != null) for (int i = 0; i < files.length(); i++) output.put(files.get(i));
            page = result.optString("nextPageToken", "");
        } while (!page.isEmpty() && output.length() < 10000);
        return output;
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
    public static boolean hasFile(String token, String parent, String name) throws Exception {
        String q = "'" + parent.replace("'", "\\'") + "' in parents and name = '" +
            name.replace("\\", "\\\\").replace("'", "\\'") + "' and trashed = false";
        return request(ROOT + "?fields=files(id)&pageSize=1&q=" + enc(q), token, "GET", null)
            .getJSONArray("files").length() > 0;
    }
    public static String md5Checksum(String token, String id) throws Exception {
        return request(ROOT + "/" + enc(id) + "?fields=md5Checksum", token, "GET", null)
            .optString("md5Checksum", "");
    }
    static boolean isFolder(JSONObject item) {
        return "application/vnd.google-apps.folder".equals(item.optString("mimeType"));
    }
    static JSONArray migrationChildren(String token, String parent) throws Exception {
        String q="'"+parent.replace("\\", "\\\\").replace("'", "\\'")+"' in parents and trashed = false";
        String uri=ROOT+"?supportsAllDrives=true&includeItemsFromAllDrives=true&pageSize=1000&q="+enc(q)+
            "&fields=nextPageToken,files(id,name,mimeType,size,md5Checksum,parents,trashed)";
        JSONArray files=new JSONArray(); String page="";
        do {
            JSONObject result=request(uri+(page.isEmpty()?"":"&pageToken="+enc(page)),token,"GET",null);
            JSONArray part=result.optJSONArray("files");
            if(part!=null) for(int i=0;i<part.length();i++) files.put(part.get(i));
            page=result.optString("nextPageToken","");
        } while(!page.isEmpty());
        return files;
    }
    static JSONObject migrationInfo(String token,String id) throws Exception {
        return request(ROOT+"/"+enc(id)+"?supportsAllDrives=true&fields=id,name,size,md5Checksum,parents,trashed",token,"GET",null);
    }
    static JSONObject migrationCopy(String token,String id,String name,String parent) throws Exception {
        return request(ROOT+"/"+enc(id)+"/copy?supportsAllDrives=true&fields=id",token,"POST",
            new JSONObject().put("name",name).put("parents",new JSONArray().put(parent)));
    }
    public static String publicLink(String token, String id) throws Exception {
        request(ROOT + "/" + enc(id) + "/permissions?fields=id", token, "POST",
            new JSONObject().put("type", "anyone").put("role", "reader"));
        return "https://drive.google.com/file/d/" + id + "/view";
    }
    public static void star(String token, String id, boolean enabled) throws Exception {
        request(ROOT + "/" + enc(id) + "?fields=id,starred", token, "PATCH",
            new JSONObject().put("starred", enabled));
    }
    public static void revokePublicLink(String token, String id) throws Exception {
        JSONArray permissions = request(ROOT + "/" + enc(id) + "/permissions?fields=permissions(id,type)",
            token, "GET", null).optJSONArray("permissions");
        if (permissions == null) return;
        for (int i = 0; i < permissions.length(); i++) {
            JSONObject permission = permissions.optJSONObject(i);
            if (permission != null && "anyone".equals(permission.optString("type")))
                request(ROOT + "/" + enc(id) + "/permissions/" + enc(permission.getString("id")), token, "DELETE", null);
        }
    }
    public static void move(String token, String id, String destination) throws Exception {
        if (destination == null || destination.isEmpty() || destination.equals(id))
            throw new IllegalArgumentException("Folder tujuan tidak valid");
        JSONObject file = request(ROOT + "/" + enc(id) + "?fields=parents", token, "GET", null);
        JSONArray parents = file.optJSONArray("parents");
        StringBuilder old = new StringBuilder();
        if (parents != null) for (int i = 0; i < parents.length(); i++) {
            if (i > 0) old.append(',');
            old.append(parents.getString(i));
        }
        if (parents != null && parents.length() == 1 && destination.equals(parents.optString(0))) return;
        request(ROOT + "/" + enc(id) + "?fields=id&addParents=" + enc(destination)
            + (old.length() == 0 ? "" : "&removeParents=" + enc(old.toString())), token, "PATCH", null);
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
        if (code < 200 || code > 299) {
            String detail = "";
            try (var error = c.getErrorStream()) {
                if (error != null) {
                    byte[] bytes = new byte[4096];
                    int count = error.read(bytes);
                    if (count > 0) detail = new JSONObject(new String(bytes, 0, count, StandardCharsets.UTF_8))
                        .getJSONObject("error").optString("message", "");
                }
            } catch (Exception ignored) { }
            c.disconnect();
            throw new IOException("Google Drive HTTP " + code + (detail.isEmpty() ? "" : ": " + detail));
        }
        try (var in = c.getInputStream(); var out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192]; int n;
            while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
            return out.size() == 0 ? new JSONObject() : new JSONObject(new String(out.toByteArray(), StandardCharsets.UTF_8));
        }
        finally { c.disconnect(); }
    }
    private static String enc(String text) {
        try { return URLEncoder.encode(text, "UTF-8"); }
        catch (java.io.UnsupportedEncodingException e) { throw new AssertionError(e); }
    }
}

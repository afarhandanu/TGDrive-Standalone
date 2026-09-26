package com.tgdrive.mobile;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/** Private, atomic output checkpoint. Contains paths/status only, never OAuth tokens or cookies. */
final class RetryFiles {
    static File manifest(Context context, long id) {
        return new File(context.getCacheDir(), "job-" + id + "/outputs.json");
    }
    static JSONArray read(Context context, long id) {
        try { return new JSONArray(new String(Files.readAllBytes(manifest(context, id).toPath()), StandardCharsets.UTF_8)); }
        catch (Exception missing) { return null; }
    }
    static void save(Context context, long id, JSONArray files) throws Exception {
        File target = manifest(context, id), partial = new File(target.getParentFile(), "outputs.json.part");
        Files.write(partial.toPath(), files.toString().getBytes(StandardCharsets.UTF_8));
        Files.move(partial.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }
    static boolean complete(JSONObject file, String target) {
        return (!("gallery".equals(target) || "both".equals(target)) || file.optBoolean("local_done")) &&
            (!("drive".equals(target) || "both".equals(target)) || file.optBoolean("drive_done"));
    }
}

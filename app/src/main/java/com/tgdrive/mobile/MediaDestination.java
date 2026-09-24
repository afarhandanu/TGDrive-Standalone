package com.tgdrive.mobile;

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.webkit.MimeTypeMap;
import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;

public final class MediaDestination {
    private MediaDestination() {}

    public static Uri publish(Context context, File source, String relativeFile) throws Exception {
        String name = source.getName();
        StringBuilder subfolders = new StringBuilder();
        String[] parts = relativeFile.replace('\\', '/').split("/");
        for (int i = 0; i < parts.length - 1; i++) {
            String safe = parts[i].replaceAll("[^a-zA-Z0-9._-]", "_");
            if (!safe.isEmpty() && !safe.equals(".") && !safe.equals(".."))
                subfolders.append('/').append(safe);
        }
        String ext = name.contains(".") ? name.substring(name.lastIndexOf('.') + 1).toLowerCase() : "";
        String type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
        if (type == null) type = "application/octet-stream";
        Uri collection;
        String folder;
        if (type.startsWith("video/")) {
            collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
            folder = Environment.DIRECTORY_MOVIES + "/The Great Drive";
        } else if (type.startsWith("image/")) {
            collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
            folder = Environment.DIRECTORY_PICTURES + "/The Great Drive";
        } else if (type.startsWith("audio/")) {
            collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
            folder = Environment.DIRECTORY_MUSIC + "/The Great Drive";
        } else {
            collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI;
            folder = Environment.DIRECTORY_DOWNLOADS + "/The Great Drive";
        }
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
        values.put(MediaStore.MediaColumns.MIME_TYPE, type);
        values.put(MediaStore.MediaColumns.RELATIVE_PATH, folder + subfolders + "/");
        values.put(MediaStore.MediaColumns.IS_PENDING, 1);
        Uri result = context.getContentResolver().insert(collection, values);
        if (result == null) throw new IllegalStateException("Gagal membuat file galeri");
        try (FileInputStream input = new FileInputStream(source);
             OutputStream output = context.getContentResolver().openOutputStream(result, "w")) {
            if (output == null) throw new IllegalStateException("Gagal membuka file galeri");
            byte[] buffer = new byte[262144]; int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
        } catch (Exception e) {
            context.getContentResolver().delete(result, null, null);
            throw e;
        }
        values.clear();
        values.put(MediaStore.MediaColumns.IS_PENDING, 0);
        context.getContentResolver().update(result, values, null, null);
        return result;
    }
}

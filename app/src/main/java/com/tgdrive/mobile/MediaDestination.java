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

    public static Uri publish(Context context, File source) throws Exception {
        String name = source.getName();
        String ext = name.contains(".") ? name.substring(name.lastIndexOf('.') + 1).toLowerCase() : "";
        String type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
        if (type == null) type = "application/octet-stream";
        Uri collection;
        String folder;
        if (type.startsWith("video/")) {
            collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
            folder = Environment.DIRECTORY_MOVIES + "/TGDrive";
        } else if (type.startsWith("image/")) {
            collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
            folder = Environment.DIRECTORY_PICTURES + "/TGDrive";
        } else if (type.startsWith("audio/")) {
            collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
            folder = Environment.DIRECTORY_MUSIC + "/TGDrive";
        } else {
            collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI;
            folder = Environment.DIRECTORY_DOWNLOADS + "/TGDrive";
        }
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
        values.put(MediaStore.MediaColumns.MIME_TYPE, type);
        values.put(MediaStore.MediaColumns.RELATIVE_PATH, folder);
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

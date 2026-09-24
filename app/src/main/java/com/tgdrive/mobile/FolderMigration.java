package com.tgdrive.mobile;

import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;

/** Only migrates the old app's TGDrive folders; source deletion is checksum gated. */
final class FolderMigration {
    static final String OLD = "TGDrive", NEW = "The Great Drive";
    static final class Entry {
        final String id, name, mime, path, volume;
        final long size;
        Entry(String id, String name, String mime, String path, String volume, long size) {
            this.id=id; this.name=name; this.mime=mime; this.path=path; this.volume=volume; this.size=size;
        }
        String label() { return path + name; }
    }
    static List<Entry> scanLocal(Context context) throws Exception {
        List<Entry> result = new ArrayList<>();
        String[] columns = {"_id", "_display_name", "mime_type", "relative_path", "volume_name", "_size"};
        String where = "owner_package_name = ? AND is_pending = 0 AND " +
            "(relative_path LIKE ? OR relative_path LIKE ? OR relative_path LIKE ? OR relative_path LIKE ?)";
        String[] args = {context.getPackageName(), "Movies/TGDrive/%", "Pictures/TGDrive/%", "Music/TGDrive/%", "Download/TGDrive/%"};
        if (android.os.Build.VERSION.SDK_INT >= 30) where += " AND is_trashed = 0";
        try (Cursor c = context.getContentResolver().query(MediaStore.Files.getContentUri("external"), columns, where, args, "_id ASC")) {
            if (c == null) throw new IOException("Daftar file Lokal tidak dapat dibaca");
            while (c.moveToNext()) {
                String path=c.getString(3), volume=c.getString(4);
                // Provider LIKE matching can be case-insensitive; scope exact legacy roots here too.
                if (path == null || !path.matches("^(Movies|Pictures|Music|Download)/TGDrive/.*")) continue;
                Uri uri=ContentUris.withAppendedId(MediaStore.Files.getContentUri(volume), c.getLong(0));
                result.add(new Entry(uri.toString(),c.getString(1),c.getString(2),path,volume,c.getLong(5)));
            }
        }
        return result;
    }
    static List<Entry> scanDrive(String token, String parent, BooleanSupplier canceled) throws Exception {
        List<Entry> result=new ArrayList<>();
        JSONArray children=DriveFiles.migrationChildren(token,parent);
        Set<String> visited=new HashSet<>();
        for (int i=0;i<children.length();i++) {
            JSONObject f=children.getJSONObject(i);
            if (OLD.equals(f.optString("name")) && DriveFiles.isFolder(f))
                scanDriveFolder(token,f.getString("id"),"",result,visited,canceled);
        }
        return result;
    }
    private static void scanDriveFolder(String token, String folder, String path, List<Entry> result,
                                        Set<String> visited, BooleanSupplier canceled) throws Exception {
        VerifiedMigration.check(canceled);
        if (!visited.add(folder)) return;
        JSONArray children=DriveFiles.migrationChildren(token,folder);
        for (int i=0;i<children.length();i++) {
            VerifiedMigration.check(canceled);
            JSONObject f=children.getJSONObject(i);
            if (DriveFiles.isFolder(f)) scanDriveFolder(token,f.getString("id"),path+f.getString("name")+"/",result,visited,canceled);
            else result.add(new Entry(f.getString("id"),f.getString("name"),f.optString("mimeType"),path,"",f.optLong("size")));
        }
    }
    static void local(Context context, Entry entry, boolean remove, BooleanSupplier canceled) throws Exception {
        var resolver=context.getContentResolver();
        SharedPreferences journal=context.getSharedPreferences("folder_migration_copies",Context.MODE_PRIVATE);
        String newPath=entry.path.replaceFirst("/TGDrive/", "/The Great Drive/");
        Uri source=Uri.parse(entry.id);
        VerifiedMigration.run(new VerifiedMigration.Item() {
            Uri target;
            boolean created;
            public void copyOrReuse() throws Exception {
                String expected=fingerprint(resolver.openInputStream(source),canceled);
                String saved=journal.getString(entry.id,null);
                if (saved!=null && targetMatches(Uri.parse(saved),expected,false)) { target=Uri.parse(saved); return; }
                try (Cursor c=resolver.query(MediaStore.Files.getContentUri(entry.volume),new String[]{"_id"},
                        "relative_path = ? AND _display_name = ? AND owner_package_name = ? AND is_pending = 0" +
                            (android.os.Build.VERSION.SDK_INT>=30 ? " AND is_trashed = 0" : ""),
                        new String[]{newPath,entry.name,context.getPackageName()},null)) {
                    if (c!=null) while(c.moveToNext()) {
                        Uri candidate=ContentUris.withAppendedId(MediaStore.Files.getContentUri(entry.volume),c.getLong(0));
                        if(targetMatches(candidate,expected,false)) { target=candidate; return; }
                    }
                }
                VerifiedMigration.check(canceled);
                ContentValues values=new ContentValues();
                values.put(MediaStore.MediaColumns.DISPLAY_NAME,entry.name);
                values.put(MediaStore.MediaColumns.MIME_TYPE,entry.mime==null ? "application/octet-stream" : entry.mime);
                values.put(MediaStore.MediaColumns.RELATIVE_PATH,newPath);
                values.put(MediaStore.MediaColumns.IS_PENDING,1);
                Uri collection=entry.path.startsWith("Movies/") ? MediaStore.Video.Media.getContentUri(entry.volume) :
                    entry.path.startsWith("Pictures/") ? MediaStore.Images.Media.getContentUri(entry.volume) :
                    entry.path.startsWith("Music/") ? MediaStore.Audio.Media.getContentUri(entry.volume) : MediaStore.Downloads.getContentUri(entry.volume);
                target=resolver.insert(collection,values);
                if(target==null) throw new IOException("Tidak bisa membuat salinan Lokal");
                created=true;
                try (InputStream input=resolver.openInputStream(source); OutputStream output=resolver.openOutputStream(target,"w")) {
                    if(input==null || output==null) throw new IOException("Tidak bisa membuka file untuk disalin");
                    byte[] buffer=new byte[262144]; int count;
                    while((count=input.read(buffer))!=-1) { VerifiedMigration.check(canceled); output.write(buffer,0,count); }
                } catch(Exception error) { cleanup(); throw error; }
            }
            private boolean targetMatches(Uri candidate,String expected,boolean allowPending) throws Exception {
                VerifiedMigration.check(canceled);
                String[] columns=android.os.Build.VERSION.SDK_INT>=30 ?
                    new String[]{"relative_path","owner_package_name","is_pending","is_trashed"} :
                    new String[]{"relative_path","owner_package_name","is_pending"};
                try (Cursor c=resolver.query(candidate,columns,null,null,null)) {
                    if(c==null || !c.moveToFirst() || !newPath.equals(c.getString(0)) || !context.getPackageName().equals(c.getString(1))) return false;
                    if(!allowPending && c.getInt(2)!=0) return false;
                    if(android.os.Build.VERSION.SDK_INT>=30 && c.getInt(3)!=0) return false;
                    return expected.equals(fingerprint(resolver.openInputStream(candidate),canceled));
                } catch(java.io.InterruptedIOException canceledError) { throw canceledError; }
                catch(Exception missing) { return false; }
            }
            public boolean verify() throws Exception {
                try {
                    String current=fingerprint(resolver.openInputStream(source),canceled);
                    if(!targetMatches(target,current,created)) { cleanup(); return false; }
                    if(created) {
                        ContentValues ready=new ContentValues(); ready.put(MediaStore.MediaColumns.IS_PENDING,0);
                        if(resolver.update(target,ready,null,null)!=1) throw new IOException("Salinan belum dapat diterbitkan");
                    }
                    if(!journal.edit().putString(entry.id,target.toString()).commit()) throw new IOException("Catatan salinan tidak dapat disimpan; sumber dipertahankan");
                    created=false; return true;
                } catch(Exception error) { cleanup(); throw error; }
            }
            public void removeSource() throws Exception {
                if(resolver.delete(source,null,null)!=1) throw new IOException("Salinan tersimpan; file lama belum dapat dihapus");
            }
            private void cleanup() { if(created && target!=null) { try { resolver.delete(target,null,null); } catch(Exception ignored) {} } }
        },remove,canceled);
    }
    static void drive(Context context, String token, String parent, Entry entry, boolean remove, BooleanSupplier canceled) throws Exception {
        if(entry.mime.startsWith("application/vnd.google-apps."))
            throw new IOException("Dokumen Google/pintasan dipertahankan: verifikasi checksum hanya mendukung file biasa");
        SharedPreferences journal=context.getSharedPreferences("folder_migration_copies",Context.MODE_PRIVATE);
        VerifiedMigration.run(new VerifiedMigration.Item() {
            String destination, target;
            JSONObject source;
            public void copyOrReuse() throws Exception {
                source=DriveFiles.migrationInfo(token,entry.id);
                if(source.optString("md5Checksum").isEmpty()) throw new IOException("Checksum sumber Drive tidak tersedia; sumber dipertahankan");
                destination=DriveFiles.ensurePath(token,parent,entry.path+entry.name);
                String key="drive:"+entry.id+":"+destination;
                String saved=journal.getString(key,null);
                if(saved!=null) {
                    try { if(matches(DriveFiles.migrationInfo(token,saved))) { target=saved; return; } }
                    catch(IOException ignored) { }
                }
                JSONArray children=DriveFiles.migrationChildren(token,destination);
                for(int i=0;i<children.length();i++) {
                    JSONObject candidate=children.getJSONObject(i);
                    if(entry.name.equals(candidate.optString("name")) && matches(candidate)) { target=candidate.getString("id"); return; }
                }
                VerifiedMigration.check(canceled);
                target=DriveFiles.migrationCopy(token,entry.id,entry.name,destination).getString("id");
                // Persist the new copy ID so interruption/retry can reuse it instead of creating duplicates.
                if(!journal.edit().putString(key,target).commit()) throw new IOException("Catatan salinan Drive gagal disimpan; sumber dipertahankan");
            }
            private boolean matches(JSONObject copy) {
                return !copy.optBoolean("trashed") && source.has("size") && copy.has("size") && !source.optString("md5Checksum").isEmpty() &&
                    source.optString("md5Checksum").equals(copy.optString("md5Checksum")) &&
                    source.optString("size").equals(copy.optString("size")) &&
                    copy.optJSONArray("parents")!=null && copy.optJSONArray("parents").toString().contains("\""+destination+"\"");
            }
            public boolean verify() throws Exception {
                source=DriveFiles.migrationInfo(token,entry.id);
                return !source.optBoolean("trashed") && matches(DriveFiles.migrationInfo(token,target));
            }
            public void removeSource() throws Exception {
                History.remapDriveFile(context,entry.id,target);
                DriveFiles.trash(token,entry.id);
            }
        },remove,canceled);
    }
    private static String fingerprint(InputStream stream, BooleanSupplier canceled) throws Exception {
        if(stream==null) throw new IOException("File tidak dapat dibaca untuk verifikasi");
        try(InputStream input=stream) {
            MessageDigest digest=MessageDigest.getInstance("SHA-256"); long length=0;
            byte[] buffer=new byte[262144]; int count;
            while((count=input.read(buffer))!=-1) { VerifiedMigration.check(canceled); digest.update(buffer,0,count); length+=count; }
            StringBuilder value=new StringBuilder().append(length).append(':');
            for(byte b:digest.digest()) value.append(String.format(java.util.Locale.ROOT,"%02x",b & 255));
            return value.toString();
        }
    }
}

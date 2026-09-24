package com.tgdrive.mobile;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DriveBrowserActivity extends Activity {
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private String token, folder = "root";
    private LinearLayout list;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        token = getIntent().getStringExtra("token");
        LinearLayout outer = new LinearLayout(this); outer.setOrientation(1); outer.setPadding(24, 24, 24, 24);
        TextView heading = new TextView(this); heading.setText("Google Drive · The Great Drive"); heading.setTextSize(24); outer.addView(heading);
        Button back = new Button(this); back.setText("Kembali ke My Drive"); back.setOnClickListener(v -> { folder = "root"; load(); }); outer.addView(back);
        Button create = new Button(this); create.setText("Buat folder"); create.setOnClickListener(v -> input("Nama folder", "", value -> action(() -> DriveFiles.createFolder(token, folder, value)))); outer.addView(create);
        Button search = new Button(this); search.setText("Cari file"); search.setOnClickListener(v -> input("Nama file", "", value -> io.execute(() -> {
            try { JSONArray found = DriveFiles.search(token, value); runOnUiThread(() -> render(found)); }
            catch (Exception e) { fail(e); }
        }))); outer.addView(search);
        ScrollView scroll = new ScrollView(this); list = new LinearLayout(this); list.setOrientation(1);
        scroll.addView(list); outer.addView(scroll); setContentView(outer); load();
    }
    private void load() { io.execute(() -> {
        try { JSONArray files = DriveFiles.list(token, folder); runOnUiThread(() -> render(files)); }
        catch (Exception e) { fail(e); }
    }); }
    private void render(JSONArray files) {
        list.removeAllViews();
        if (files.length() == 0) { TextView empty = new TextView(this); empty.setText("Folder kosong"); list.addView(empty); }
        for (int i = 0; i < files.length(); i++) {
            JSONObject file = files.optJSONObject(i); if (file == null) continue;
            String id = file.optString("id"), name = file.optString("name");
            boolean directory = "application/vnd.google-apps.folder".equals(file.optString("mimeType"));
            Button button = new Button(this); button.setAllCaps(false);
            button.setText((directory ? "📁 " : "📄 ") + name + (file.optBoolean("starred") ? " ★" : ""));
            list.addView(button);
            button.setOnClickListener(v -> {
                if (directory) { folder = id; load(); }
                else showActions(id, name, file.optBoolean("starred"), false);
            });
            if (directory) button.setOnLongClickListener(v -> { showActions(id, name, file.optBoolean("starred"), true); return true; });
        }
    }
    private void showActions(String id, String name, boolean starred, boolean directory) {
        new AlertDialog.Builder(this).setTitle(name)
                    .setItems(new String[]{"Ganti nama", "Link publik", "Pindah ke sampah",
                        starred ? "Hapus bintang" : "Beri bintang", "Cabut link publik", "Pindah ke folder…"}, (dialog, option) -> {
                        if (option == 0) input("Nama baru", name, text -> action(() -> DriveFiles.rename(token, id, text)));
                        if (option == 1) io.execute(() -> {
                            try { String link = DriveFiles.publicLink(token, id);
                                String publicUrl = directory ? "https://drive.google.com/drive/folders/" + id : link;
                                runOnUiThread(() -> new AlertDialog.Builder(this).setMessage(publicUrl).setPositiveButton("OK", null).show()); }
                            catch (Exception e) { fail(e); }
                        });
                        if (option == 2) new AlertDialog.Builder(this).setMessage("Pindahkan ke sampah?")
                            .setPositiveButton("Ya", (d, w) -> action(() -> DriveFiles.trash(token, id)))
                            .setNegativeButton("Batal", null).show();
                        if (option == 3) action(() -> DriveFiles.star(token, id, !starred));
                        if (option == 4) new AlertDialog.Builder(this).setMessage("Cabut akses publik untuk file/folder ini?")
                            .setPositiveButton("Cabut", (d, w) -> action(() -> DriveFiles.revokePublicLink(token, id)))
                            .setNegativeButton("Batal", null).show();
                        if (option == 5) chooseFolder(id, "root");
                    }).show();
    }
    private void chooseFolder(String itemId, String destination) {
        io.execute(() -> {
            try {
                JSONArray files = DriveFiles.list(token, destination);
                java.util.ArrayList<String> names = new java.util.ArrayList<>();
                java.util.ArrayList<String> ids = new java.util.ArrayList<>();
                for (int i = 0; i < files.length(); i++) {
                    JSONObject file = files.optJSONObject(i);
                    if (file != null && "application/vnd.google-apps.folder".equals(file.optString("mimeType"))
                        && !itemId.equals(file.optString("id"))) {
                        names.add("📁 " + file.optString("name")); ids.add(file.optString("id"));
                    }
                }
                runOnUiThread(() -> new AlertDialog.Builder(this).setTitle("Pilih folder tujuan")
                    .setItems(names.toArray(new String[0]), (d, choice) -> chooseFolder(itemId, ids.get(choice)))
                    .setPositiveButton("Pindahkan ke folder ini", (d, w) -> action(() -> DriveFiles.move(token, itemId, destination)))
                    .setNegativeButton("Batal", null).show());
            } catch (Exception e) { fail(e); }
        });
    }
    private interface Value { void submit(String text); }
    private interface Work { void run() throws Exception; }
    private void input(String title, String initial, Value callback) {
        EditText edit = new EditText(this); edit.setText(initial);
        new AlertDialog.Builder(this).setTitle(title).setView(edit).setPositiveButton("Simpan", (d, w) -> {
            String value = edit.getText().toString().trim(); if (!value.isEmpty()) callback.submit(value);
        }).setNegativeButton("Batal", null).show();
    }
    private void action(Work work) { io.execute(() -> {
        try { work.run(); load(); } catch (Exception e) { fail(e); }
    }); }
    private void fail(Exception e) { runOnUiThread(() -> Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show()); }
    @Override protected void onDestroy() { io.shutdownNow(); super.onDestroy(); }
}

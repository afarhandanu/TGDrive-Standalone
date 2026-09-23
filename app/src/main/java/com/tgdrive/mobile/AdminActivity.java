package com.tgdrive.mobile;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;

/** Device administration for the standalone, single-owner app. */
public class AdminActivity extends Activity {
    private LinearLayout content;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroll = new ScrollView(this);
        content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(28, 28, 28, 28); content.setBackgroundColor(Color.rgb(246, 248, 253));
        scroll.addView(content); setContentView(scroll);
        refresh();
    }
    private void refresh() {
        content.removeAllViews();
        title("Administrasi perangkat");
        JSONArray jobs = History.read(this);
        int complete = 0, failed = 0, waiting = 0;
        for (int i = 0; i < jobs.length(); i++) {
            JSONObject row = jobs.optJSONObject(i);
            if (row == null) continue;
            switch (row.optString("state")) {
                case "COMPLETED": complete++; break;
                case "FAILED": case "CANCELLED": case "INTERRUPTED": failed++; break;
                default: waiting++;
            }
        }
        title("Riwayat " + jobs.length() + " · selesai " + complete + " · gagal/batal " + failed +
            " · belum selesai " + waiting);
        title("Antrean: " + (DownloadService.isPaused() ? "dijeda" : "berjalan") +
            (DownloadService.hasPendingJobs() ? " · ada pekerjaan aktif" : " · kosong"));
        title("Akun Drive: " + getSharedPreferences("drive_account", MODE_PRIVATE)
            .getString("email", "belum tersambung"));
        title("Situs login yang disimpan: " + SavedSites.list(this).length());
        long cache = diskUsage(getCacheDir());
        title("Cache sementara: " + (cache / 1024 / 1024) + " MB");
        action("Jeda / lanjutkan antrean", () -> {
            boolean pause = !DownloadService.isPaused();
            startService(new Intent(this, DownloadService.class).setAction(pause ? "PAUSE" : "RESUME"));
            refresh();
        });
        action("Batalkan tugas aktif", () -> startService(new Intent(this, DownloadService.class).setAction("CANCEL")));
        action("Bersihkan cache job lama", () -> confirm("Hapus berkas sementara dari job yang tidak berjalan?", () -> {
            if (DownloadService.hasPendingJobs()) { message("Tunggu sampai antrean kosong"); return; }
            File[] entries = getCacheDir().listFiles();
            if (entries != null) for (File file : entries)
                if (file.getName().startsWith("job-")) deleteTree(file);
            refresh(); message("Cache job dibersihkan");
        }));
        action("Hapus riwayat unduhan", () -> confirm("Hapus semua catatan riwayat di perangkat ini?", () -> {
            if (DownloadService.hasPendingJobs()) { message("Tunggu sampai antrean kosong"); return; }
            getSharedPreferences("jobs", MODE_PRIVATE).edit().remove("history").apply();
            refresh(); message("Riwayat dibersihkan");
        }));
        title("Panel ini mengelola data dan antrean pada ponsel ini. Hak akses Telegram, kuota per pengguna, dan broadcast hanya berlaku pada bot yang memiliki server dan banyak pengguna.");
    }
    private void confirm(String question, Runnable task) {
        new AlertDialog.Builder(this).setMessage(question).setNegativeButton("Batal", null)
            .setPositiveButton("Lanjutkan", (dialog, which) -> task.run()).show();
    }
    private void title(String value) {
        TextView text = new TextView(this); text.setText(value); text.setTextSize(17);
        text.setTextColor(Color.rgb(30, 39, 65)); text.setPadding(0, 10, 0, 10); content.addView(text);
    }
    private void action(String value, Runnable click) {
        Button button = new Button(this); button.setAllCaps(false); button.setText(value);
        button.setOnClickListener(view -> click.run()); content.addView(button);
    }
    private long diskUsage(File file) {
        if (file.isFile()) return file.length();
        long total = 0; File[] files = file.listFiles();
        if (files != null) for (File child : files) if (!java.nio.file.Files.isSymbolicLink(child.toPath())) total += diskUsage(child);
        return total;
    }
    private void deleteTree(File file) {
        if (java.nio.file.Files.isSymbolicLink(file.toPath())) return;
        File[] list = file.listFiles();
        if (list != null) for (File child : list) deleteTree(child);
        file.delete();
    }
    private void message(String value) { Toast.makeText(this, value, Toast.LENGTH_SHORT).show(); }
}

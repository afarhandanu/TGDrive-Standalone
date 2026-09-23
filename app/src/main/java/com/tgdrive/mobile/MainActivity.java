package com.tgdrive.mobile;

import android.app.Activity;
import android.accounts.Account;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.database.Cursor;
import android.provider.OpenableColumns;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import com.google.android.gms.auth.api.identity.AuthorizationRequest;
import com.google.android.gms.auth.api.identity.AuthorizationResult;
import com.google.android.gms.auth.api.identity.Identity;
import com.google.android.gms.common.api.Scope;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Collections;
import java.util.ArrayList;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Local downloader UI. Google authorization is only requested when Drive is selected. */
public class MainActivity extends Activity {
    private static final int DRIVE_AUTH = 712;
    private static final int IMPORT_FILE = 713;
    private static final int LOCAL_FILE = 714;
    private EditText url, limit, folder;
    private CheckBox subtitles, thumbnail, metadata;
    private TextView history, destinationLabel, qualityLabel, driveStatus;
    private String destination = "gallery", quality = "best";
    private String pendingUrl;
    private String pendingImportPath;
    private final ArrayList<Uri> selectedFiles = new ArrayList<>();
    private final ArrayList<String> pendingLocalPaths = new ArrayList<>();
    private String importCategory = "all";
    private ArrayList<String> pendingUrls = new ArrayList<>();
    private String driveAction = "connect", driveEmail;
    private boolean authorizingDrive;
    private final ExecutorService driveIo = Executors.newSingleThreadExecutor();
    private BroadcastReceiver receiver;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(16, 25, 54));
        getWindow().setNavigationBarColor(Color.rgb(16, 25, 54));
        render();
        acceptShare(getIntent());
        receiver = new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) { showHistory(); }
        };
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, new IntentFilter(DownloadService.EVENTS), Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(receiver, new IntentFilter(DownloadService.EVENTS));
    }
    @Override protected void onNewIntent(Intent intent) { super.onNewIntent(intent); setIntent(intent); acceptShare(intent); }
    @Override protected void onDestroy() { unregisterReceiver(receiver); driveIo.shutdownNow(); super.onDestroy(); }
    private void acceptShare(Intent intent) {
        if (Intent.ACTION_SEND.equals(intent.getAction()) && intent.getStringExtra(Intent.EXTRA_TEXT) != null)
            url.setText(intent.getStringExtra(Intent.EXTRA_TEXT));
        else if (Intent.ACTION_VIEW.equals(intent.getAction()) && intent.getData() != null)
            url.setText(intent.getDataString());
        else if (Intent.ACTION_SEND.equals(intent.getAction()) && intent.getParcelableExtra(Intent.EXTRA_STREAM) != null) {
            selectedFiles.clear(); selectedFiles.add(intent.getParcelableExtra(Intent.EXTRA_STREAM));
            url.setHint("1 file dibagikan · pilih tujuan lalu mulai");
        } else if (Intent.ACTION_SEND_MULTIPLE.equals(intent.getAction())) {
            selectedFiles.clear();
            var streams = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
            if (streams != null) for (Object stream : streams) if (stream instanceof Uri) selectedFiles.add((Uri) stream);
            url.setHint(selectedFiles.size() + " file dibagikan · pilih tujuan lalu mulai");
        }
    }
    private void render() {
        int navy = Color.rgb(16, 25, 54), ink = Color.rgb(30, 39, 65), muted = Color.rgb(101, 111, 137);
        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setPadding(dp(22), dp(22), dp(22), dp(28));
        outer.setBackgroundColor(Color.rgb(246, 248, 253));
        ScrollView scroll = new ScrollView(this); scroll.addView(outer); setContentView(scroll);
        TextView brand = label("TG / DRIVE", 13, Color.rgb(65, 87, 220), true); outer.addView(brand);
        TextView title = label("Simpan yang kamu suka.", 30, navy, true); outer.addView(title);
        TextView sub = label("Unduh media ke ponsel, Drive, atau keduanya. Antrean berjalan langsung di perangkatmu.", 15, muted, false);
        outer.addView(sub);

        LinearLayout card = new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(18), dp(18), dp(18));
        GradientDrawable bg = new GradientDrawable(); bg.setColor(Color.WHITE); bg.setCornerRadius(dp(24));
        card.setBackground(bg); card.setElevation(dp(4));
        LinearLayout.LayoutParams cardP = new LinearLayout.LayoutParams(-1, -2); cardP.topMargin = dp(24); outer.addView(card, cardP);
        card.addView(label("TAUTAN MEDIA", 12, muted, true));
        url = new EditText(this); url.setHint("Tempel satu atau beberapa link video, post, Reel, Story…"); url.setSingleLine(false);
        url.setTextColor(ink); url.setTextSize(16); card.addView(url);

        destinationLabel = label("Tujuan · Galeri", 15, ink, true); card.addView(destinationLabel);
        row(card, new String[]{"Galeri", "Drive", "Keduanya"}, new String[]{"gallery", "drive", "both"}, value -> {
            destination = value;
            destinationLabel.setText("Tujuan · " + (value.equals("both") ? "Galeri + Drive" : value.equals("drive") ? "Drive" : "Galeri"));
        });
        qualityLabel = label("Kualitas · Terbaik", 15, ink, true); card.addView(qualityLabel);
        row(card, new String[]{"Terbaik", "1080p", "720p", "Audio"}, new String[]{"best", "1080", "720", "audio"}, value -> {
            quality = value; qualityLabel.setText("Kualitas · " + value);
        });
        card.addView(label("Maksimum item (profil / playlist)", 13, muted, false));
        limit = new EditText(this); limit.setInputType(2); limit.setText("1"); card.addView(limit);
        subtitles = new CheckBox(this); subtitles.setText("Sertakan subtitle jika ada"); card.addView(subtitles);
        thumbnail = new CheckBox(this); thumbnail.setText("Sertakan thumbnail"); card.addView(thumbnail);
        metadata = new CheckBox(this); metadata.setText("Sertakan metadata JSON"); card.addView(metadata);
        card.addView(label("ID folder Drive (opsional)", 13, muted, false));
        folder = new EditText(this); folder.setHint("Kosong = My Drive"); card.addView(folder);
        Button go = button("Mulai download  ↗", navy); go.setOnClickListener(v -> start()); card.addView(go);
        Button instagram = button("Masuk Instagram", Color.rgb(225, 62, 118));
        instagram.setOnClickListener(v -> openLogin("instagram")); outer.addView(instagram);
        Button x = button("Masuk X", navy); x.setOnClickListener(v -> openLogin("x")); outer.addView(x);
        driveStatus = label("Google Drive · Belum terhubung", 14, ink, true); outer.addView(driveStatus);
        Button connect = button("Hubungkan / ganti akun Google Drive", Color.rgb(65, 87, 220));
        connect.setOnClickListener(v -> { driveAction = "connect"; authorizeDrive(true); }); outer.addView(connect);
        Button files = button("Kelola file Google Drive", Color.rgb(65, 87, 220));
        files.setOnClickListener(v -> { driveAction = "browse"; authorizeDrive(driveEmail == null); }); outer.addView(files);
        outer.addView(label("IMPORT INSTAGRAM JSON / ZIP", 13, muted, true));
        row(outer, new String[]{"Semua", "Feed", "Reels", "Stories", "Tagged"},
            new String[]{"all", "feed", "reels", "stories", "mentions"}, value -> {
                importCategory = value; toast("Kategori import: " + value);
            });
        Button importButton = button("Pilih file JSON / ZIP", Color.rgb(65, 87, 220));
        importButton.setOnClickListener(v -> {
            Intent choose = new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*")
                .addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(choose, IMPORT_FILE);
        }); outer.addView(importButton);
        Button pickLocal = button("Pilih file dari ponsel", navy);
        pickLocal.setOnClickListener(v -> startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT)
            .setType("*/*").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true), LOCAL_FILE));
        outer.addView(pickLocal);

        TextView recent = label("AKTIVITAS", 13, muted, true);
        LinearLayout.LayoutParams recentP = new LinearLayout.LayoutParams(-1, -2); recentP.topMargin = dp(24);
        outer.addView(recent, recentP);
        history = label("Belum ada unduhan.", 14, ink, false); outer.addView(history);
        Button cancel = button("Batalkan job aktif", Color.rgb(132, 52, 72));
        cancel.setOnClickListener(v -> startService(new Intent(this, DownloadService.class).setAction("CANCEL")));
        outer.addView(cancel);
        Button retry = button("Ulangi job gagal / batal", Color.rgb(65, 87, 220));
        retry.setOnClickListener(v -> retryLast()); outer.addView(retry);
        showHistory();
    }
    private void start() {
        if (!selectedFiles.isEmpty()) { copyLocal(); return; }
        pendingImportPath = null;
        pendingLocalPaths.clear();
        pendingUrl = url.getText().toString().trim();
        pendingUrls.clear();
        Matcher links = Pattern.compile("https?://[^\\s<>]+", Pattern.CASE_INSENSITIVE).matcher(pendingUrl);
        while (links.find() && pendingUrls.size() < 50) pendingUrls.add(links.group().replaceAll("[.,;]+$", ""));
        if (pendingUrls.isEmpty()) {
            toast("Masukkan URL http/https yang valid"); return;
        }
        if (destination.equals("gallery")) { enqueue(null); return; }
        driveAction = "download";
        authorizeDrive(driveEmail == null);
    }
    private void authorizeDrive(boolean chooseAccount) {
        if (authorizingDrive) { toast("Tunggu proses koneksi Drive selesai"); return; }
        authorizingDrive = true;
        driveStatus.setText("Google Drive · Menghubungkan…");
        AuthorizationRequest.Builder builder = AuthorizationRequest.builder()
            .setRequestedScopes(Collections.singletonList(new Scope("https://www.googleapis.com/auth/drive")));
        if (chooseAccount) builder.setPrompt(AuthorizationRequest.Prompt.SELECT_ACCOUNT);
        else if (driveEmail != null) builder.setAccount(new Account(driveEmail, "com.google"));
        AuthorizationRequest request = builder.build();
        Identity.getAuthorizationClient(this).authorize(request).addOnSuccessListener(result -> {
            if (result.hasResolution()) {
                try { startIntentSenderForResult(result.getPendingIntent().getIntentSender(), DRIVE_AUTH, null, 0, 0, 0); }
                catch (Exception e) { driveFailed("Login Drive: " + e.getMessage()); }
            } else authorized(result.getAccessToken());
        }).addOnFailureListener(e -> driveFailed("Drive: " + e.getMessage()));
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == IMPORT_FILE && result == RESULT_OK && data != null && data.getData() != null) {
            copyImport(data.getData()); return;
        }
        if (request == LOCAL_FILE && result == RESULT_OK && data != null) {
            selectedFiles.clear();
            if (data.getClipData() != null) for (int i = 0; i < data.getClipData().getItemCount(); i++)
                selectedFiles.add(data.getClipData().getItemAt(i).getUri());
            else if (data.getData() != null) selectedFiles.add(data.getData());
            url.setHint(selectedFiles.size() + " file dipilih · pilih tujuan lalu mulai");
            return;
        }
        if (request == DRIVE_AUTH) {
            if (result != RESULT_OK || data == null) { driveFailed("Pemilihan akun Drive dibatalkan"); return; }
            try { AuthorizationResult auth = Identity.getAuthorizationClient(this).getAuthorizationResultFromIntent(data);
                authorized(auth.getAccessToken());
            } catch (Exception e) { driveFailed("Drive: " + e.getMessage()); }
        }
    }
    private void authorized(String token) {
        if (token == null || token.isEmpty()) { driveFailed("Drive tidak memberikan akses. Periksa izin akun lalu coba lagi."); return; }
        String action = driveAction;
        driveIo.execute(() -> {
            try {
                String email = DriveFiles.accountLabel(token);
                runOnUiThread(() -> {
                    authorizingDrive = false;
                    driveEmail = email;
                    driveStatus.setText("Google Drive · Terhubung: " + email);
                    toast("Drive terhubung: " + email);
                    if ("browse".equals(action))
                        startActivity(new Intent(this, DriveBrowserActivity.class).putExtra("token", token));
                    else if ("download".equals(action)) enqueue(token);
                });
            } catch (Exception e) { runOnUiThread(() -> driveFailed("Drive: " + e.getMessage())); }
        });
    }
    private void driveFailed(String message) {
        authorizingDrive = false;
        driveStatus.setText("Google Drive · " + (driveEmail == null ? "Belum terhubung" : "Akun terakhir: " + driveEmail));
        toast(message);
    }
    private void copyImport(Uri selected) {
        new Thread(() -> {
            try {
                String name = "";
                try (Cursor cursor = getContentResolver().query(selected, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
                    if (cursor != null && cursor.moveToFirst()) name = cursor.getString(0);
                }
                String extension = name.toLowerCase().endsWith(".zip") ? ".zip" :
                    name.toLowerCase().endsWith(".json") ? ".json" : "";
                if (extension.isEmpty()) throw new IllegalArgumentException("Pilih file .json atau .zip");
                File local = new File(getCacheDir(), "import-" + System.currentTimeMillis() + extension);
                try (InputStream in = getContentResolver().openInputStream(selected);
                     FileOutputStream out = new FileOutputStream(local)) {
                    if (in == null) throw new IllegalArgumentException("File import tidak bisa dibaca");
                    byte[] buffer = new byte[262144]; int n;
                    while ((n = in.read(buffer)) >= 0) out.write(buffer, 0, n);
                }
                runOnUiThread(() -> {
                    pendingImportPath = local.getAbsolutePath();
                    pendingUrls.clear(); pendingLocalPaths.clear(); selectedFiles.clear(); driveAction = "download";
                    if (destination.equals("gallery")) enqueue(null);
                    else authorizeDrive(driveEmail == null);
                });
            } catch (Exception e) { runOnUiThread(() -> toast("Import: " + e.getMessage())); }
        }).start();
    }
    private void copyLocal() {
        ArrayList<Uri> items = new ArrayList<>(selectedFiles);
        new Thread(() -> {
            try {
                ArrayList<String> staged = new ArrayList<>();
                for (Uri uri : items) {
                    String name = "";
                    try (Cursor cursor = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
                        if (cursor != null && cursor.moveToFirst()) name = cursor.getString(0);
                    }
                    name = name.replaceAll("[^a-zA-Z0-9._-]", "_");
                    if (name.trim().isEmpty()) name = "file_" + staged.size();
                    File local = new File(getCacheDir(), "selected_" + System.nanoTime() + "_" + name);
                    try (InputStream in = getContentResolver().openInputStream(uri);
                         FileOutputStream out = new FileOutputStream(local)) {
                        if (in == null) throw new IllegalArgumentException("File tidak bisa dibaca");
                        byte[] buffer = new byte[262144]; int n;
                        while ((n = in.read(buffer)) >= 0) out.write(buffer, 0, n);
                    }
                    staged.add(local.getAbsolutePath());
                }
                runOnUiThread(() -> {
                    pendingLocalPaths.clear(); pendingLocalPaths.addAll(staged);
                    pendingImportPath = null; pendingUrls.clear(); selectedFiles.clear(); driveAction = "download";
                    if (destination.equals("gallery")) enqueue(null); else authorizeDrive(driveEmail == null);
                });
            } catch (Exception e) { runOnUiThread(() -> toast("File: " + e.getMessage())); }
        }).start();
    }
    private void enqueue(String token) {
        int count = 1;
        try { count = Math.max(1, Math.min(500, Integer.parseInt(limit.getText().toString()))); }
        catch (NumberFormatException ignored) {}
        try {
            int jobs = pendingLocalPaths.isEmpty() ? (pendingImportPath == null ? pendingUrls.size() : 1) : pendingLocalPaths.size();
            for (int i = 0; i < jobs; i++) {
                String link = pendingLocalPaths.isEmpty() && pendingImportPath == null ? pendingUrls.get(i) : "";
                Intent job = new Intent(this, DownloadService.class).putExtra("url", link)
                    .putExtra("target", destination).putExtra("quality", quality).putExtra("count", count)
                    .putExtra("subtitles", subtitles.isChecked()).putExtra("thumbnail", thumbnail.isChecked())
                    .putExtra("metadata", metadata.isChecked())
                    .putExtra("folder_id", folder.getText().toString().trim()).putExtra("drive_token", token)
                    .putExtra("import_path", pendingImportPath).putExtra("category", importCategory)
                    .putExtra("local_path", pendingLocalPaths.isEmpty() ? null : pendingLocalPaths.get(i));
                startForegroundService(job);
            }
            toast(jobs + " tugas masuk antrean");
            pendingImportPath = null;
            pendingLocalPaths.clear();
        }
        catch (Exception e) { toast("Gagal memulai unduhan: " + e.getMessage()); }
    }
    private void openLogin(String site) { startActivity(new Intent(this, LoginActivity.class).putExtra("site", site)); }
    private void retryLast() {
        JSONObject previous = History.lastRetryable(this);
        if (previous == null) { toast("Tidak ada job yang bisa diulang"); return; }
        url.setText(previous.optString("url"));
        destination = previous.optString("target", "gallery");
        quality = previous.optString("quality", "best");
        limit.setText(String.valueOf(previous.optInt("count", 1)));
        folder.setText(previous.optString("folder"));
        subtitles.setChecked(previous.optBoolean("subtitles"));
        thumbnail.setChecked(previous.optBoolean("thumbnail"));
        metadata.setChecked(previous.optBoolean("metadata"));
        destinationLabel.setText("Tujuan · " + destination);
        qualityLabel.setText("Kualitas · " + quality);
        start();
    }
    private void showHistory() {
        JSONArray jobs = History.read(this);
        if (jobs.length() == 0) { history.setText("Belum ada unduhan."); return; }
        StringBuilder output = new StringBuilder();
        for (int i = 0; i < Math.min(15, jobs.length()); i++) {
            JSONObject item = jobs.optJSONObject(i); if (item == null) continue;
            output.append("● ").append(item.optString("state")).append(" · ")
                .append(item.optString("message")).append("\n\n");
        }
        history.setText(output.toString());
    }
    private interface Select { void pick(String value); }
    private void row(LinearLayout parent, String[] names, String[] values, Select select) {
        LinearLayout line = new LinearLayout(this); line.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < names.length; i++) {
            final String value = values[i]; Button b = button(names[i], Color.rgb(225, 230, 249));
            b.setTextColor(Color.rgb(40, 51, 108)); b.setTextSize(11);
            line.addView(b, new LinearLayout.LayoutParams(0, dp(45), 1));
            b.setOnClickListener(v -> select.pick(value));
        }
        parent.addView(line);
    }
    private Button button(String text, int color) {
        Button b = new Button(this); b.setText(text); b.setAllCaps(false); b.setTextColor(Color.WHITE);
        GradientDrawable shape = new GradientDrawable(); shape.setColor(color); shape.setCornerRadius(dp(16));
        b.setBackground(shape); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(50));
        lp.topMargin = dp(10); b.setLayoutParams(lp); return b;
    }
    private TextView label(String content, int size, int color, boolean bold) {
        TextView t = new TextView(this); t.setText(content); t.setTextSize(size); t.setTextColor(color);
        t.setGravity(Gravity.START); t.setPadding(0, dp(5), 0, dp(5));
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }
    private int dp(int value) { return Math.round(getResources().getDisplayMetrics().density * value); }
    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_LONG).show(); }
}

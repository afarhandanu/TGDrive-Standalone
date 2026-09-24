package com.tgdrive.mobile;

import android.app.Activity;
import android.app.AlertDialog;
import android.accounts.Account;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.util.Linkify;
import android.database.Cursor;
import android.provider.OpenableColumns;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;
import android.widget.MediaController;
import android.text.method.LinkMovementMethod;
import com.google.android.gms.auth.api.identity.AuthorizationRequest;
import com.google.android.gms.auth.api.identity.AuthorizationResult;
import com.google.android.gms.auth.api.identity.Identity;
import com.google.android.gms.common.api.Scope;
import com.google.android.gms.common.api.ApiException;
import com.chaquo.python.Python;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Collections;
import java.util.ArrayList;
import java.io.File;
import java.io.FileOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.time.LocalDate;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Local downloader UI. Google authorization is only requested when Drive is selected. */
public class MainActivity extends Activity {
    private static final int DRIVE_AUTH = 712;
    private static final int IMPORT_FILE = 713;
    private static final int LOCAL_FILE = 714;
    private static final int EXPORT_BACKUP = 715;
    private static final int IMPORT_BACKUP = 716;
    private static final int IMPORT_COOKIES = 717;
    private static final int TORRENT_FILE = 718;
    private static final int MUX_FILES = 719;
    private EditText url, limit, folder, dateFrom, dateTo;
    private CheckBox subtitles, thumbnail, metadata, skipCompleted, anonymous, skipDrive, albumMode, verifyDrive;
    private TextView destinationLabel, qualityLabel, driveStatus, siteStatus;
    private TextView profileContentLabel, importCategoryLabel, importOutputLabel;
    private TextView pendingFileNotice;
    private LinearLayout historyList;
    private LinearLayout savedSites;
    private ScrollView[] tabs;
    private TextView[] tabButtons;
    private int currentTab;
    private String destination = "gallery", quality = "best";
    private String pendingUrl;
    private String pendingImportPath;
    private String pendingTorrentPath;
    private boolean muxSelection;
    private final ArrayList<Uri> selectedFiles = new ArrayList<>();
    private final ArrayList<String> pendingLocalPaths = new ArrayList<>();
    private String importCategory = "all", importOutput = "folder", profileContent = "all";
    private ArrayList<String> pendingUrls = new ArrayList<>();
    private final ArrayList<String> selectedStoryUrls = new ArrayList<>();
    private String driveAction = "connect", driveEmail;
    private String cookieHost;
    private boolean authorizingDrive;
    private final ExecutorService driveIo = Executors.newSingleThreadExecutor();
    private final ExecutorService storyPreviewIo = Executors.newFixedThreadPool(2);
    private BroadcastReceiver receiver;
    private boolean restoringSettings;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        WebSessions.restore(this);
        if (!DownloadService.hasPendingJobs()) History.markInterrupted(this);
        driveEmail = getSharedPreferences("drive_account", MODE_PRIVATE).getString("email", null);
        getWindow().setStatusBarColor(Color.rgb(246, 248, 253));
        getWindow().setNavigationBarColor(Color.rgb(246, 248, 253));
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        render();
        restoreSettings();
        acceptShare(getIntent());
        if (driveEmail != null) restoreDriveSession();
        receiver = new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) { showHistory(); }
        };
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, new IntentFilter(DownloadService.EVENTS), Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(receiver, new IntentFilter(DownloadService.EVENTS));
    }
    @Override protected void onNewIntent(Intent intent) { super.onNewIntent(intent); setIntent(intent); acceptShare(intent); }
    @Override public void onBackPressed() {
        if (currentTab != 0) openTab(0);
        else super.onBackPressed();
    }
    @Override protected void onResume() {
        super.onResume();
        if (siteStatus != null) updateSiteStatus();
        if (savedSites != null) renderSavedSites();
        if (historyList != null && currentTab == 4) showHistory();
    }
    @Override protected void onPause() { saveSettings(); super.onPause(); }
    @Override protected void onDestroy() {
        unregisterReceiver(receiver); driveIo.shutdownNow(); storyPreviewIo.shutdownNow(); super.onDestroy();
    }
    private void acceptShare(Intent intent) {
        if (Intent.ACTION_SEND.equals(intent.getAction()) && intent.getStringExtra(Intent.EXTRA_TEXT) != null) {
            selectedFiles.clear(); muxSelection = false;
            url.setText(intent.getStringExtra(Intent.EXTRA_TEXT));
        } else if (Intent.ACTION_VIEW.equals(intent.getAction()) && intent.getData() != null) {
            selectedFiles.clear(); muxSelection = false;
            url.setText(intent.getDataString());
        }
        else if (Intent.ACTION_SEND.equals(intent.getAction()) && intent.getParcelableExtra(Intent.EXTRA_STREAM) != null) {
            selectedFiles.clear(); muxSelection = false; selectedFiles.add(intent.getParcelableExtra(Intent.EXTRA_STREAM));
            url.setHint("1 file dibagikan · pilih tujuan lalu mulai");
        } else if (Intent.ACTION_SEND_MULTIPLE.equals(intent.getAction())) {
            selectedFiles.clear(); muxSelection = false;
            var streams = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
            if (streams != null) for (Object stream : streams) if (stream instanceof Uri) selectedFiles.add((Uri) stream);
            url.setHint(selectedFiles.size() + " file dibagikan · pilih tujuan lalu mulai");
        }
        updatePendingFileNotice();
        if (intent.getAction() != null && (Intent.ACTION_SEND.equals(intent.getAction()) ||
                Intent.ACTION_SEND_MULTIPLE.equals(intent.getAction()) || Intent.ACTION_VIEW.equals(intent.getAction()))) {
            openTab(0);
            tabs[0].post(() -> tabs[0].smoothScrollTo(0, 0));
        }
    }
    private void render() {
        int navy = Color.rgb(16, 25, 54), ink = Color.rgb(30, 39, 65), muted = Color.rgb(101, 111, 137);
        LinearLayout outer = page();
        LinearLayout options = page();
        LinearLayout accounts = page();
        LinearLayout filesPage = page();
        LinearLayout activity = page();
        LinearLayout info = page();
        outer.addView(brandBanner(112));
        TextView title = label("Simpan yang kamu suka.", 30, navy, true); outer.addView(title);
        TextView sub = label("Tautan masuk, pilih tujuan, lalu unduh. Semua proses berjalan di perangkatmu.", 15, muted, false);
        outer.addView(sub);
        pageTitle(options, "Opsi unduhan", "Kualitas, folder, dan filter yang kamu pilih akan tetap tersimpan.");
        pageTitle(accounts, "Akun & Drive", "Kelola login situs dan file di Google Drive.");
        pageTitle(filesPage, "Berkas & impor", "Impor data Instagram atau pilih file dari ponsel.");
        pageTitle(activity, "Aktivitas", "Pantau antrean dan buka detail setiap hasil unduhan.");
        pageTitle(info, "Info aplikasi", "Tentang The Great Drive, daftar fitur, dan perubahan versi.");

        LinearLayout card = new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(18), dp(18), dp(18));
        GradientDrawable bg = new GradientDrawable(); bg.setColor(Color.WHITE); bg.setCornerRadius(dp(24));
        card.setBackground(bg); card.setElevation(dp(4));
        LinearLayout.LayoutParams cardP = new LinearLayout.LayoutParams(-1, -2); cardP.topMargin = dp(24); outer.addView(card, cardP);
        card.addView(label("TAUTAN MEDIA", 12, muted, true));
        url = new EditText(this); url.setHint("Tempel satu atau beberapa link video, post, Reel, Story…"); url.setSingleLine(false);
        url.setTextColor(ink); url.setTextSize(16); card.addView(url);
        pendingFileNotice = label("", 13, Color.rgb(65, 87, 220), true);
        pendingFileNotice.setVisibility(View.GONE);
        pendingFileNotice.setOnClickListener(v -> {
            selectedFiles.clear(); muxSelection = false; updatePendingFileNotice();
        });
        card.addView(pendingFileNotice);

        destinationLabel = label("Tujuan · Lokal", 15, ink, true); card.addView(destinationLabel);
        row(card, new String[]{"Lokal", "Drive", "Keduanya"}, new String[]{"gallery", "drive", "both"}, value -> {
            destination = value;
            destinationLabel.setText("Tujuan · " + destinationName(value));
        });
        qualityLabel = label("Kualitas · Terbaik", 15, ink, true); card.addView(qualityLabel);
        row(card, new String[]{"Terbaik", "1080p", "720p", "Audio"}, new String[]{"best", "1080", "720", "audio"}, value -> {
            quality = value; qualityLabel.setText("Kualitas · " + qualityName(value));
        });
        row(card, new String[]{"2160p", "1440p", "480p", "Video saja"},
            new String[]{"2160", "1440", "480", "video"}, value -> {
                quality = value; qualityLabel.setText("Kualitas · " + qualityName(value));
            });
        card.addView(label("Maksimum item (profil / playlist)", 13, muted, false));
        limit = new EditText(this); limit.setInputType(2); limit.setHint("Kosong = tanpa batas"); limit.setText("1"); card.addView(limit);
        Button go = button("Mulai download  ↗", navy); go.setOnClickListener(v -> start()); card.addView(go);
        Button pickStories = button("Pilih Story Instagram", Color.rgb(65, 87, 220));
        pickStories.setOnClickListener(v -> pickStories()); outer.addView(pickStories);
        Button configure = button("Atur opsi unduhan & folder  →", Color.rgb(225, 230, 249));
        configure.setTextColor(ink); configure.setOnClickListener(v -> openTab(1)); outer.addView(configure);

        LinearLayout optionCard = panel(options);
        optionCard.addView(label("PILIHAN FILE", 12, muted, true));
        subtitles = new CheckBox(this); subtitles.setText("Sertakan subtitle jika ada"); optionCard.addView(subtitles);
        thumbnail = new CheckBox(this); thumbnail.setText("Sertakan thumbnail"); optionCard.addView(thumbnail);
        metadata = new CheckBox(this); metadata.setText("Sertakan metadata JSON"); optionCard.addView(metadata);
        skipCompleted = new CheckBox(this); skipCompleted.setText("Lewati link yang sudah berhasil diunduh"); optionCard.addView(skipCompleted);
        anonymous = new CheckBox(this); anonymous.setText("Tanpa akun untuk link publik (abaikan sesi login)"); optionCard.addView(anonymous);
        skipDrive = new CheckBox(this); skipDrive.setText("Drive: lewati file dengan nama yang sama"); optionCard.addView(skipDrive);
        verifyDrive = new CheckBox(this); verifyDrive.setText("Drive: cocokkan checksum MD5 setelah unggah"); optionCard.addView(verifyDrive);
        albumMode = new CheckBox(this);
        albumMode.setText("Mode profil / album (Instagram, X, Facebook; termasuk carousel)"); optionCard.addView(albumMode);
        profileContentLabel = label("Profil Facebook · Semua", 13, muted, false);
        optionCard.addView(profileContentLabel);
        row(optionCard, new String[]{"Semua", "Foto", "Video"},
            new String[]{"all", "photos", "videos"}, value -> {
                profileContent = value;
                profileContentLabel.setText("Profil Facebook · " + (value.equals("photos") ? "Foto" : value.equals("videos") ? "Video" : "Semua"));
            });
        optionCard.addView(label("ID folder Drive (opsional)", 13, muted, false));
        folder = new EditText(this); folder.setHint("Kosong = My Drive"); optionCard.addView(folder);
        optionCard.addView(label("Rentang tanggal import / profil (opsional, YYYY-MM-DD)", 13, muted, false));
        dateFrom = new EditText(this); dateFrom.setHint("Dari tanggal"); dateFrom.setSingleLine(true);
        dateFrom.setInputType(android.text.InputType.TYPE_CLASS_DATETIME | android.text.InputType.TYPE_DATETIME_VARIATION_DATE);
        optionCard.addView(dateFrom);
        dateTo = new EditText(this); dateTo.setHint("Sampai tanggal"); dateTo.setSingleLine(true);
        dateTo.setInputType(android.text.InputType.TYPE_CLASS_DATETIME | android.text.InputType.TYPE_DATETIME_VARIATION_DATE);
        optionCard.addView(dateTo);
        LinearLayout loginCard = panel(accounts);
        loginCard.addView(label("LOGIN SITUS", 12, muted, true));
        Button instagram = button("Masuk Instagram", Color.rgb(225, 62, 118));
        instagram.setOnClickListener(v -> openLogin("instagram")); loginCard.addView(instagram);
        Button x = button("Buka X / login", navy); x.setOnClickListener(v -> openLogin("x")); loginCard.addView(x);
        siteStatus = label("Sesi situs", 13, muted, false); loginCard.addView(siteStatus);
        Button other = button("Masuk situs lain (URL HTTPS)", navy);
        other.setOnClickListener(v -> {
            EditText input = new EditText(this);
            input.setSingleLine(true);
            input.setHint("https://contoh.com/login");
            input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI);
            String current = url.getText().toString().trim();
            if (WebSessions.host(current) != null) input.setText(current);
            new AlertDialog.Builder(this).setTitle("Alamat halaman login situs")
                .setView(input)
                .setNegativeButton("Batal", null)
                .setPositiveButton("Buka", (dialog, which) -> {
                    String loginUrl = input.getText().toString().trim();
                    if (WebSessions.host(loginUrl) == null) { toast("Masukkan URL HTTPS situs yang valid"); return; }
                    try { SavedSites.remember(this, loginUrl); }
                    catch (IllegalArgumentException e) { toast(e.getMessage()); return; }
                    renderSavedSites();
                    startActivity(new Intent(this, LoginActivity.class)
                        .putExtra("site", "custom").putExtra("login_url", loginUrl));
                }).show();
        }); loginCard.addView(other);
        Button cookies = button("Impor cookies.txt untuk situs", navy);
        cookies.setOnClickListener(v -> {
            EditText input = new EditText(this);
            input.setSingleLine(true); input.setHint("https://contoh.com/");
            String current = url.getText().toString().trim();
            if (WebSessions.host(current) != null) input.setText(current);
            new AlertDialog.Builder(this).setTitle("Situs pemilik cookies")
                .setView(input).setNegativeButton("Batal", null)
                .setPositiveButton("Pilih cookies.txt", (d, w) -> {
                    String address = input.getText().toString().trim();
                    cookieHost = WebSessions.host(address);
                    if (cookieHost == null) { toast("Gunakan alamat HTTPS situs"); return; }
                    try { SavedSites.remember(this, address); renderSavedSites(); }
                    catch (IllegalArgumentException e) { toast(e.getMessage()); return; }
                    startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT)
                        .setType("*/*").addCategory(Intent.CATEGORY_OPENABLE), IMPORT_COOKIES);
                }).show();
        }); loginCard.addView(cookies);
        savedSites = new LinearLayout(this);
        savedSites.setOrientation(LinearLayout.VERTICAL);
        loginCard.addView(savedSites);
        renderSavedSites();
        LinearLayout driveCard = panel(accounts);
        driveCard.addView(label("GOOGLE DRIVE", 12, muted, true));
        driveStatus = label("Google Drive · " + (driveEmail == null ? "Belum terhubung" :
            "Memeriksa akun " + driveEmail + "…"), 14, ink, true); driveCard.addView(driveStatus);
        Button connect = button("Hubungkan / ganti akun Google Drive", Color.rgb(65, 87, 220));
        connect.setOnClickListener(v -> { driveAction = "connect"; authorizeDrive(true); }); driveCard.addView(connect);
        Button files = button("Kelola file Google Drive", Color.rgb(65, 87, 220));
        files.setOnClickListener(v -> { driveAction = "browse"; authorizeDrive(driveEmail == null); }); driveCard.addView(files);
        LinearLayout importCard = panel(filesPage);
        importCategoryLabel = label("IMPORT INSTAGRAM JSON / ZIP · SEMUA", 13, muted, true);
        importCard.addView(importCategoryLabel);
        row(importCard, new String[]{"Semua", "Feed", "Reels", "Stories", "Tagged"},
            new String[]{"all", "feed", "reels", "stories", "mentions"}, value -> {
                importCategory = value; importCategoryLabel.setText("IMPORT INSTAGRAM JSON / ZIP · " + value.toUpperCase(java.util.Locale.ROOT));
            });
        Button dateOptions = button("Atur tanggal & opsi import  →", Color.rgb(225, 230, 249));
        dateOptions.setTextColor(ink);
        dateOptions.setOnClickListener(v -> openTab(1)); importCard.addView(dateOptions);
        importOutputLabel = label("Hasil import · Folder", 13, ink, true);
        importCard.addView(importOutputLabel);
        row(importCard, new String[]{"Folder", "Satu ZIP"}, new String[]{"folder", "zip"}, value -> {
            importOutput = value;
            importOutputLabel.setText("Hasil import · " + (value.equals("zip") ? "Satu ZIP" : "Folder"));
        });
        Button importButton = button("Pilih file JSON / ZIP", Color.rgb(65, 87, 220));
        importButton.setOnClickListener(v -> {
            Intent choose = new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*")
                .addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(choose, IMPORT_FILE);
        }); importCard.addView(importButton);
        LinearLayout localCard = panel(filesPage);
        localCard.addView(label("FILE DARI PONSEL", 12, muted, true));
        Button pickLocal = button("Pilih file dari ponsel", navy);
        pickLocal.setOnClickListener(v -> startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT)
            .setType("*/*").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true), LOCAL_FILE));
        localCard.addView(pickLocal);
        Button pickTorrent = button("Pilih file .torrent", Color.rgb(65, 87, 220));
        pickTorrent.setOnClickListener(v -> startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT)
            .setType("*/*").addCategory(Intent.CATEGORY_OPENABLE), TORRENT_FILE));
        localCard.addView(pickTorrent);
        Button muxFiles = button("Gabungkan video + audio (FFmpeg)", navy);
        muxFiles.setOnClickListener(v -> startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT)
            .setType("*/*").addCategory(Intent.CATEGORY_OPENABLE)
            .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true), MUX_FILES));
        localCard.addView(muxFiles);
        LinearLayout migrationCard = panel(filesPage);
        migrationCard.addView(label("PINDAHKAN FOLDER LAMA", 12, muted, true));
        migrationCard.addView(label("TGDrive → The Great Drive. Pilih simpan file lama atau hapus setelah salinan terverifikasi.", 14, muted, false));
        Button migrateLocal = button("Pindahkan folder Lokal", navy);
        migrateLocal.setOnClickListener(v -> startActivity(new Intent(this, MigrationActivity.class)));
        migrationCard.addView(migrateLocal);
        Button migrateDrive = button("Pindahkan folder Google Drive", Color.rgb(65, 87, 220));
        migrateDrive.setOnClickListener(v -> { driveAction = "migrate"; authorizeDrive(driveEmail == null); });
        migrationCard.addView(migrateDrive);

        LinearLayout queueCard = panel(activity);
        queueCard.addView(label("KONTROL ANTREAN", 12, muted, true));
        Button cancel = button("Batalkan job aktif", Color.rgb(132, 52, 72));
        cancel.setOnClickListener(v -> startService(new Intent(this, DownloadService.class).setAction("CANCEL")));
        queueCard.addView(cancel);
        Button retry = button("Ulangi job gagal / batal", Color.rgb(65, 87, 220));
        retry.setOnClickListener(v -> retryLast()); queueCard.addView(retry);
        Button pause = button("Jeda / lanjutkan antrean", Color.rgb(65, 87, 220));
        pause.setOnClickListener(v -> {
            boolean next = !DownloadService.isPaused();
            startService(new Intent(this, DownloadService.class).setAction(next ? "PAUSE" : "RESUME"));
            toast(next ? "Tugas berikutnya dijeda; tugas aktif tetap berjalan" : "Antrean dilanjutkan");
        }); queueCard.addView(pause);
        TextView recent = label("RIWAYAT TERBARU", 13, muted, true);
        Button errors = button("Lihat log error", navy);
        errors.setOnClickListener(v -> showErrorLog());
        activity.addView(errors);
        LinearLayout.LayoutParams recentP = new LinearLayout.LayoutParams(-1, -2); recentP.topMargin = dp(24);
        activity.addView(recent, recentP);
        historyList = new LinearLayout(this); historyList.setOrientation(LinearLayout.VERTICAL);
        activity.addView(historyList);
        LinearLayout backupCard = panel(accounts);
        backupCard.addView(label("DATA & PERANGKAT", 12, muted, true));
        Button backup = button("Ekspor riwayat dan daftar situs", navy);
        backup.setOnClickListener(v -> startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE).setType("application/json")
            .putExtra(Intent.EXTRA_TITLE, "The-Great-Drive-backup.json"), EXPORT_BACKUP));
        backupCard.addView(backup);
        Button restore = button("Pulihkan riwayat dan daftar situs", navy);
        restore.setOnClickListener(v -> {
            if (DownloadService.hasPendingJobs()) { toast("Tunggu antrean selesai sebelum memulihkan cadangan"); return; }
            startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE).setType("application/json"), IMPORT_BACKUP);
        }); backupCard.addView(restore);
        backupCard.addView(label("Cadangan memuat riwayat tautan dan alamat situs; sesi login tetap tersimpan hanya di perangkat ini.", 12, muted, false));
        Button admin = button("Administrasi perangkat", navy);
        admin.setOnClickListener(v -> startActivity(new Intent(this, AdminActivity.class)));
        backupCard.addView(admin);
        buildInfo(info);
        installTabs(outer, options, accounts, filesPage, activity, info);
        showHistory();
    }
    private ImageView brandBanner(int heightDp) {
        ImageView banner = new ImageView(this);
        banner.setImageResource(R.drawable.brand_banner);
        banner.setScaleType(ImageView.ScaleType.CENTER_CROP);
        banner.setContentDescription("Logo The Great Drive");
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.rgb(251, 248, 241));
        background.setCornerRadius(dp(16));
        banner.setBackground(background);
        banner.setClipToOutline(true);
        banner.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(heightDp)));
        return banner;
    }
    private void buildInfo(LinearLayout parent) {
        LinearLayout selector = new LinearLayout(this);
        parent.addView(selector);
        LinearLayout[] sections = {panel(parent), panel(parent), panel(parent)};
        Button[] buttons = new Button[3];
        String[] titles = {"Tentang", "Fitur", "Changelog"};
        for (int i = 0; i < titles.length; i++) {
            final int selected = i;
            Button button = new Button(this); button.setText(titles[i]); button.setAllCaps(false);
            button.setTextSize(12); buttons[i] = button;
            selector.addView(button, new LinearLayout.LayoutParams(0, -2, 1));
            button.setOnClickListener(v -> {
                for (int j = 0; j < sections.length; j++) {
                    sections[j].setVisibility(j == selected ? View.VISIBLE : View.GONE);
                    buttons[j].setTextColor(j == selected ? Color.rgb(65, 87, 220) : Color.rgb(101, 111, 137));
                    buttons[j].setTypeface(Typeface.DEFAULT, j == selected ? Typeface.BOLD : Typeface.NORMAL);
                }
            });
        }
        sections[0].addView(aboutContent());
        renderInfoDocument(sections[1], "FEATURES.md");
        renderInfoDocument(sections[2], "CHANGELOG.md");
        buttons[0].performClick();
    }
    private void renderInfoDocument(LinearLayout parent, String asset) {
        try (InputStream input = getAssets().open(asset)) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192]; int count;
            while ((count = input.read(buffer)) != -1) bytes.write(buffer, 0, count);
            String content = new String(bytes.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
            for (String line : content.split("\n")) {
                if (line.trim().isEmpty()) continue;
                boolean heading = line.startsWith("#");
                String text = line.replaceFirst("^#+\\s*", "").replace("**", "").replace("`", "");
                if (text.startsWith("- ")) text = "• " + text.substring(2);
                TextView item = label(text, heading ? 18 : 14, Color.rgb(30, 39, 65), heading);
                item.setTextIsSelectable(true); parent.addView(item);
            }
        } catch (Exception error) {
            ErrorLog.record(this, "Info aplikasi", error);
            parent.addView(label("Dokumen belum dapat dibuka.", 14, Color.RED, false));
        }
    }
    private LinearLayout aboutContent() {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(8), dp(20), dp(8));
        content.addView(brandBanner(110));
        String version = "";
        try { version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName; }
        catch (Exception ignored) { }
        content.addView(label("The Great Drive" + (version == null || version.isEmpty() ? "" : " · " + version),
            18, Color.rgb(25, 36, 46), true));
        content.addView(label("Unduh tautan dan simpan media ke ponsel, Google Drive, atau keduanya. " +
            "Antrean dan sesi situs dikelola di perangkat ini.", 14, Color.rgb(101, 111, 137), false));
        return content;
    }
    private LinearLayout page() {
        LinearLayout result = new LinearLayout(this);
        result.setOrientation(LinearLayout.VERTICAL);
        result.setPadding(dp(20), dp(20), dp(20), dp(30));
        result.setBackgroundColor(Color.rgb(246, 248, 253));
        return result;
    }
    private void pageTitle(LinearLayout parent, String title, String subtitle) {
        parent.addView(label("THE GREAT DRIVE", 12, Color.rgb(65, 87, 220), true));
        parent.addView(label(title, 27, Color.rgb(16, 25, 54), true));
        parent.addView(label(subtitle, 14, Color.rgb(101, 111, 137), false));
    }
    private LinearLayout panel(LinearLayout parent) {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(16), dp(18), dp(16), dp(18));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.WHITE); bg.setCornerRadius(dp(20));
        panel.setBackground(bg); panel.setElevation(dp(2));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(18); parent.addView(panel, params);
        return panel;
    }
    private void installTabs(LinearLayout... pages) {
        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setBackgroundColor(Color.rgb(246, 248, 253));
        shell.setOnApplyWindowInsetsListener((view, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
                shell.setPadding(0, bars.top, 0, bars.bottom);
            } else shell.setPadding(0, insets.getSystemWindowInsetTop(), 0, insets.getSystemWindowInsetBottom());
            return insets;
        });
        FrameLayout holder = new FrameLayout(this);
        tabs = new ScrollView[pages.length];
        for (int i = 0; i < pages.length; i++) {
            tabs[i] = new ScrollView(this);
            tabs[i].setFillViewport(true);
            tabs[i].addView(pages[i]);
            tabs[i].setVisibility(View.GONE);
            holder.addView(tabs[i], new FrameLayout.LayoutParams(-1, -1));
        }
        shell.addView(holder, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout navigation = new LinearLayout(this);
        navigation.setPadding(dp(6), dp(5), dp(6), dp(5));
        navigation.setBackgroundColor(Color.WHITE);
        navigation.setElevation(dp(8));
        tabButtons = new TextView[pages.length];
        String[] names = {"↓\nUnduh", "⚙\nOpsi", "○\nAkun", "▣\nBerkas", "≡\nAktivitas", "ⓘ\nInfo"};
        for (int i = 0; i < pages.length; i++) {
            final int index = i;
            TextView tab = label(names[i], 11, Color.rgb(101, 111, 137), false);
            tab.setGravity(Gravity.CENTER);
            tab.setOnClickListener(v -> openTab(index));
            tabButtons[i] = tab;
            navigation.addView(tab, new LinearLayout.LayoutParams(0, dp(58), 1));
        }
        shell.addView(navigation);
        setContentView(shell);
        openTab(0);
    }
    private void openTab(int index) {
        if (tabs == null || index < 0 || index >= tabs.length) return;
        currentTab = index;
        for (int i = 0; i < tabs.length; i++) {
            boolean selected = i == index;
            tabs[i].setVisibility(selected ? View.VISIBLE : View.GONE);
            tabButtons[i].setTextColor(selected ? Color.rgb(65, 87, 220) : Color.rgb(101, 111, 137));
            tabButtons[i].setTypeface(Typeface.DEFAULT, selected ? Typeface.BOLD : Typeface.NORMAL);
            GradientDrawable background = new GradientDrawable();
            background.setColor(selected ? Color.rgb(235, 240, 255) : Color.WHITE);
            background.setCornerRadius(dp(14));
            tabButtons[i].setBackground(background);
        }
        if (index == 4 && historyList != null) showHistory();
    }
    private String destinationName(String value) {
        return value.equals("both") ? "Lokal + Drive" : value.equals("drive") ? "Drive" : "Lokal";
    }
    private String qualityName(String value) {
        return value.equals("best") ? "Terbaik" : value.equals("audio") ? "Audio" :
            value.equals("video") ? "Video saja" : value + "p";
    }
    private void updatePendingFileNotice() {
        if (pendingFileNotice == null) return;
        pendingFileNotice.setVisibility(selectedFiles.isEmpty() ? View.GONE : View.VISIBLE);
        if (!selectedFiles.isEmpty()) pendingFileNotice.setText(selectedFiles.size() +
            (muxSelection ? " file untuk digabung" : " file dari ponsel") + " dipilih · ketuk untuk batal");
    }
    private void start() {
        selectedStoryUrls.clear();
        if (!selectedFiles.isEmpty()) { copyLocal(); return; }
        pendingImportPath = null;
        pendingTorrentPath = null;
        pendingLocalPaths.clear();
        pendingUrl = url.getText().toString().trim();
        pendingUrls.clear();
        Matcher links = Pattern.compile("https?://[^\\s<>]+", Pattern.CASE_INSENSITIVE).matcher(pendingUrl);
        while (links.find() && pendingUrls.size() < 50) pendingUrls.add(links.group().replaceAll("[.,;]+$", ""));
        if (pendingUrl.startsWith("magnet:?xt=urn:btih:") && !pendingUrl.contains("\n")) {
            pendingUrls.clear(); pendingUrls.add(pendingUrl);
        }
        if (pendingUrls.isEmpty()) {
            toast("Masukkan URL http/https yang valid"); return;
        }
        if (albumMode.isChecked() && !validDates()) return;
        if (destination.equals("gallery")) { enqueue(null); return; }
        driveAction = "download";
        authorizeDrive(driveEmail == null);
    }
    private void saveSettings() {
        if (restoringSettings || limit == null || dateFrom == null) return;
        try {
            JSONObject data = new JSONObject()
                .put("destination", destination).put("quality", quality)
                .put("limit", limit.getText().toString()).put("folder", folder.getText().toString())
                .put("subtitles", subtitles.isChecked()).put("thumbnail", thumbnail.isChecked())
                .put("metadata", metadata.isChecked()).put("skip_completed", skipCompleted.isChecked())
                .put("anonymous", anonymous.isChecked()).put("skip_drive", skipDrive.isChecked())
                .put("verify_drive", verifyDrive.isChecked()).put("album_mode", albumMode.isChecked())
                .put("profile_content", profileContent).put("import_category", importCategory)
                .put("import_output", importOutput)
                .put("date_from", dateFrom.getText().toString()).put("date_to", dateTo.getText().toString());
            getSharedPreferences("download_settings", MODE_PRIVATE).edit().putString("current", data.toString()).apply();
        } catch (Exception ignored) { }
    }
    private void restoreSettings() {
        restoringSettings = true;
        try {
            String saved = getSharedPreferences("download_settings", MODE_PRIVATE).getString("current", null);
            if (saved == null) return;
            JSONObject data = new JSONObject(saved);
            destination = data.optString("destination", "gallery");
            if (!destination.equals("gallery") && !destination.equals("drive") && !destination.equals("both")) destination = "gallery";
            quality = data.optString("quality", "best");
            limit.setText(data.optString("limit", "1")); folder.setText(data.optString("folder", ""));
            subtitles.setChecked(data.optBoolean("subtitles")); thumbnail.setChecked(data.optBoolean("thumbnail"));
            metadata.setChecked(data.optBoolean("metadata")); skipCompleted.setChecked(data.optBoolean("skip_completed"));
            anonymous.setChecked(data.optBoolean("anonymous")); skipDrive.setChecked(data.optBoolean("skip_drive"));
            verifyDrive.setChecked(data.optBoolean("verify_drive")); albumMode.setChecked(data.optBoolean("album_mode"));
            profileContent = data.optString("profile_content", "all");
            importCategory = data.optString("import_category", "all"); importOutput = data.optString("import_output", "folder");
            dateFrom.setText(data.optString("date_from", "")); dateTo.setText(data.optString("date_to", ""));
            destinationLabel.setText("Tujuan · " + destinationName(destination));
            qualityLabel.setText("Kualitas · " + qualityName(quality));
            profileContentLabel.setText("Profil Facebook · " + (profileContent.equals("photos") ? "Foto" : profileContent.equals("videos") ? "Video" : "Semua"));
            importCategoryLabel.setText("IMPORT INSTAGRAM JSON / ZIP · " + importCategory.toUpperCase(java.util.Locale.ROOT));
            importOutputLabel.setText("Hasil import · " + (importOutput.equals("zip") ? "Satu ZIP" : "Folder"));
        } catch (Exception ignored) { toast("Pengaturan tersimpan tidak bisa dibaca"); }
        finally { restoringSettings = false; }
    }
    private void pickStories() {
        String address = url.getText().toString().trim();
        Matcher story = Pattern.compile("^https://(?:www\\.)?instagram\\.com/stories/([A-Za-z0-9._]+)/?(?:\\?.*)?$", Pattern.CASE_INSENSITIVE).matcher(address);
        if (!story.matches()) { toast("Isi tautan profil Story: instagram.com/stories/username/"); return; }
        String username = story.group(1);
        String cookies = WebSessions.cookies(this, "www.instagram.com");
        if (cookies == null || !cookies.contains("sessionid=")) cookies = WebSessions.cookies(this, "instagram.com");
        if (cookies == null || !cookies.contains("sessionid=")) { toast("Masuk Instagram dahulu untuk melihat Story aktif"); return; }
        final String session = cookies;
        toast("Memuat daftar Story…");
        new Thread(() -> {
            try {
                JSONArray items = new JSONArray(Python.getInstance().getModule("story_picker")
                    .callAttr("list_stories", username, session).toString());
                runOnUiThread(() -> showStoryChoices(items));
            } catch (Exception e) {
                ErrorLog.record(this, "Pemilih Story Instagram", e);
                runOnUiThread(() -> new AlertDialog.Builder(this)
                    .setTitle("Gagal memuat Story")
                    .setMessage("Detail error tersimpan di Aktivitas → Lihat log error. Pastikan sesi Instagram masih aktif, lalu coba lagi.")
                    .setPositiveButton("Buka log", (dialog, which) -> { openTab(4); showErrorLog(); })
                    .setNegativeButton("Tutup", null).show());
            }
        }).start();
    }
    private void showStoryChoices(JSONArray items) {
        int n = items.length();
        if (n == 0) { toast("Tidak ada Story aktif yang dapat dibaca"); return; }
        boolean[] selected = new boolean[n];
        LinearLayout list = new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(12), 0, dp(12), dp(8));
        for (int i = 0; i < n; i++) {
            JSONObject item = items.optJSONObject(i);
            int index = i;
            LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(dp(4), dp(6), dp(4), dp(6));
            CheckBox check = new CheckBox(this);
            check.setOnCheckedChangeListener((button, checked) -> selected[index] = checked);
            row.addView(check);
            FrameLayout preview = new FrameLayout(this);
            GradientDrawable background = new GradientDrawable();
            background.setColor(Color.rgb(225, 230, 249)); background.setCornerRadius(dp(12));
            preview.setBackground(background); preview.setClipToOutline(true);
            ImageView image = new ImageView(this); image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            preview.addView(image, new FrameLayout.LayoutParams(-1, -1));
            TextView placeholder = label(item != null && item.optBoolean("video") ? "▶" : "▧", 22,
                Color.rgb(65, 87, 220), true);
            placeholder.setGravity(Gravity.CENTER);
            preview.addView(placeholder, new FrameLayout.LayoutParams(-1, -1));
            LinearLayout.LayoutParams thumb = new LinearLayout.LayoutParams(dp(76), dp(76));
            thumb.rightMargin = dp(10); row.addView(preview, thumb);
            String labelText = item == null ? "Story " + (i + 1) :
                (item.optBoolean("video") ? "Video" : "Foto") + " · " + item.optString("date") +
                "\nID " + item.optString("id");
            TextView title = label(labelText, 14, Color.rgb(30, 39, 65), false);
            row.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
            row.setOnClickListener(v -> check.setChecked(!check.isChecked()));
            if (item != null) {
                String previewUrl = item.optString("preview");
                if (!previewUrl.isEmpty()) loadStoryPreview(previewUrl, image, placeholder);
                String videoUrl = item.optString("video_preview");
                preview.setOnClickListener(v -> {
                    if (item.optBoolean("video") && allowedStoryMediaUri(videoUrl)) {
                        VideoView player = new VideoView(this);
                        player.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(420)));
                        player.setVideoURI(Uri.parse(videoUrl));
                        MediaController controls = new MediaController(this);
                        controls.setAnchorView(player); player.setMediaController(controls);
                        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(labelText)
                            .setView(player).setPositiveButton("Tutup", null).create();
                        dialog.setOnDismissListener(d -> player.stopPlayback());
                        dialog.show(); player.start();
                    } else if (image.getDrawable() instanceof android.graphics.drawable.BitmapDrawable) {
                        ImageView larger = new ImageView(this);
                        larger.setImageDrawable(image.getDrawable());
                        larger.setAdjustViewBounds(true);
                        larger.setPadding(dp(8), dp(8), dp(8), dp(8));
                        new AlertDialog.Builder(this).setTitle(labelText).setView(larger)
                            .setPositiveButton("Tutup", null).show();
                    } else toast("Pratinjau belum tersedia; Story tetap dapat diunduh");
                });
            }
            list.addView(row);
        }
        ScrollView scroll = new ScrollView(this); scroll.addView(list);
        new AlertDialog.Builder(this).setTitle("Pilih Story (" + n + ")")
            .setView(scroll)
            .setNeutralButton("Semua", (dialog, which) -> downloadStories(items, null))
            .setNegativeButton("Batal", null)
            .setPositiveButton("Unduh dipilih", (dialog, which) -> downloadStories(items, selected)).show();
    }
    private void loadStoryPreview(String address, ImageView image, TextView placeholder) {
        try {
            if (!allowedStoryMediaUri(address)) return;
            storyPreviewIo.execute(() -> {
                HttpURLConnection connection = null;
                try {
                    connection = (HttpURLConnection) new URL(address).openConnection();
                    connection.setConnectTimeout(6000); connection.setReadTimeout(6000);
                    connection.setInstanceFollowRedirects(false);
                    if (connection.getResponseCode() != 200 ||
                        !connection.getContentType().toLowerCase(java.util.Locale.ROOT).startsWith("image/")) return;
                    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                    try (InputStream stream = connection.getInputStream()) {
                        byte[] buffer = new byte[8192]; int size;
                        while ((size = stream.read(buffer)) != -1 && bytes.size() + size <= 2_000_000)
                            bytes.write(buffer, 0, size);
                    }
                    byte[] data = bytes.toByteArray();
                    BitmapFactory.Options bounds = new BitmapFactory.Options(); bounds.inJustDecodeBounds = true;
                    BitmapFactory.decodeByteArray(data, 0, data.length, bounds);
                    BitmapFactory.Options scaled = new BitmapFactory.Options();
                    scaled.inSampleSize = Math.max(1, Math.max(bounds.outWidth, bounds.outHeight) / 300);
                    Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length, scaled);
                    if (bitmap != null) runOnUiThread(() -> {
                        image.setImageBitmap(bitmap); placeholder.setVisibility(View.GONE);
                    });
                } catch (Exception ignored) { /* Keep the photo/video placeholder if its CDN preview expires. */ }
                finally { if (connection != null) connection.disconnect(); }
            });
        } catch (Exception ignored) { }
    }
    private boolean allowedStoryMediaUri(String address) {
        try {
            Uri uri = Uri.parse(address);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(java.util.Locale.ROOT);
            return "https".equalsIgnoreCase(uri.getScheme()) &&
                (host.equals("instagram.com") || host.endsWith(".instagram.com") ||
                 host.equals("cdninstagram.com") || host.endsWith(".cdninstagram.com") ||
                 host.equals("fbcdn.net") || host.endsWith(".fbcdn.net"));
        } catch (Exception error) { return false; }
    }
    private void downloadStories(JSONArray items, boolean[] selected) {
        selectedStoryUrls.clear(); pendingUrls.clear(); pendingImportPath = null; pendingLocalPaths.clear();
        for (int i = 0; i < items.length(); i++) {
            if (selected != null && !selected[i]) continue;
            JSONObject item = items.optJSONObject(i);
            if (item != null && item.optString("url").startsWith("https://www.instagram.com/stories/"))
                selectedStoryUrls.add(item.optString("url"));
        }
        if (selectedStoryUrls.isEmpty()) { toast("Pilih setidaknya satu Story"); return; }
        if (destination.equals("gallery")) enqueueStoryBatch(null);
        else { driveAction = "download"; authorizeDrive(driveEmail == null); }
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
    private void restoreDriveSession() {
        authorizingDrive = true;
        AuthorizationRequest request = AuthorizationRequest.builder()
            .setRequestedScopes(Collections.singletonList(new Scope("https://www.googleapis.com/auth/drive")))
            .setAccount(new Account(driveEmail, "com.google")).build();
        Identity.getAuthorizationClient(this).authorize(request).addOnSuccessListener(result -> {
            if (result.hasResolution() || result.getAccessToken() == null) {
                authorizingDrive = false;
                driveStatus.setText("Google Drive · " + driveEmail + " · ketuk Hubungkan untuk memberi izin lagi");
            } else authorized(result.getAccessToken());
        }).addOnFailureListener(e -> {
            authorizingDrive = false;
            driveStatus.setText("Google Drive · " + driveEmail + " · ketuk Hubungkan untuk menyambung ulang");
        });
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == TORRENT_FILE && result == RESULT_OK && data != null && data.getData() != null) {
            Uri selected = data.getData();
            new Thread(() -> {
                try {
                    File local = new File(getCacheDir(), "chosen-" + System.nanoTime() + ".torrent");
                    try (InputStream input = getContentResolver().openInputStream(selected);
                         FileOutputStream output = new FileOutputStream(local)) {
                        if (input == null) throw new IllegalArgumentException("Berkas torrent tidak dapat dibaca");
                        byte[] buffer = new byte[32768]; int bytes, total = 0;
                        while ((bytes = input.read(buffer)) != -1) {
                            total += bytes;
                            if (total > 8 * 1024 * 1024) throw new IllegalArgumentException("Berkas torrent lebih dari 8 MB");
                            output.write(buffer, 0, bytes);
                        }
                    }
                    runOnUiThread(() -> {
                        selectedFiles.clear(); pendingUrls.clear(); pendingLocalPaths.clear(); pendingImportPath = null;
                        updatePendingFileNotice();
                        pendingTorrentPath = local.getAbsolutePath();
                        driveAction = "download";
                        if (destination.equals("gallery")) enqueue(null); else authorizeDrive(driveEmail == null);
                    });
                } catch (Exception e) { runOnUiThread(() -> toast("Torrent: " + e.getMessage())); }
            }).start();
            return;
        }
        if (request == IMPORT_FILE && result == RESULT_OK && data != null && data.getData() != null) {
            copyImport(data.getData()); return;
        }
        if (request == LOCAL_FILE && result == RESULT_OK && data != null) {
            muxSelection = false;
            selectedFiles.clear();
            if (data.getClipData() != null) for (int i = 0; i < data.getClipData().getItemCount(); i++)
                selectedFiles.add(data.getClipData().getItemAt(i).getUri());
            else if (data.getData() != null) selectedFiles.add(data.getData());
            url.setHint(selectedFiles.size() + " file dipilih · pilih tujuan lalu mulai");
            updatePendingFileNotice();
            openTab(0);
            return;
        }
        if (request == MUX_FILES && result == RESULT_OK && data != null) {
            selectedFiles.clear(); muxSelection = true;
            if (data.getClipData() != null) for (int i = 0; i < data.getClipData().getItemCount(); i++)
                selectedFiles.add(data.getClipData().getItemAt(i).getUri());
            else if (data.getData() != null) selectedFiles.add(data.getData());
            if (selectedFiles.size() != 2) { selectedFiles.clear(); muxSelection = false; updatePendingFileNotice();
                toast("Pilih tepat dua file: satu video dan satu audio"); return; }
            url.setHint("2 file untuk digabung · pilih tujuan lalu mulai");
            updatePendingFileNotice();
            openTab(0);
            return;
        }
        if ((request == EXPORT_BACKUP || request == IMPORT_BACKUP) && result == RESULT_OK && data != null && data.getData() != null) {
            Uri selected = data.getData();
            if (request == EXPORT_BACKUP) new Thread(() -> {
                try (java.io.OutputStream output = getContentResolver().openOutputStream(selected, "w")) {
                    if (output == null) throw new IllegalStateException("Tidak bisa menulis file cadangan");
                    output.write(AppBackup.exportData(this));
                    runOnUiThread(() -> toast("Cadangan tersimpan"));
                } catch (Exception e) { runOnUiThread(() -> toast("Ekspor: " + e.getMessage())); }
            }).start();
            else new AlertDialog.Builder(this).setMessage("Ganti riwayat unduhan dengan isi cadangan ini?")
                .setNegativeButton("Batal", null).setPositiveButton("Pulihkan", (d, w) -> new Thread(() -> {
                    try (InputStream input = getContentResolver().openInputStream(selected)) {
                        if (input == null) throw new IllegalStateException("Tidak bisa membaca cadangan");
                        AppBackup.restoreData(this, input);
                        runOnUiThread(() -> { showHistory(); renderSavedSites(); toast("Cadangan dipulihkan"); });
                    } catch (Exception e) { runOnUiThread(() -> toast("Pulihkan: " + e.getMessage())); }
                }).start()).show();
            return;
        }
        if (request == IMPORT_COOKIES && result == RESULT_OK && data != null && data.getData() != null && cookieHost != null) {
            String host = cookieHost;
            new Thread(() -> {
                try (InputStream input = getContentResolver().openInputStream(data.getData())) {
                    if (input == null) throw new IllegalStateException("File cookies tidak dapat dibaca");
                    ArrayList<String> parsed = CookieImporter.parse(input, host);
                    runOnUiThread(() -> CookieImporter.install(this, host, parsed, () -> {
                        updateSiteStatus(); toast(parsed.size() + " cookie tersimpan untuk " + host);
                    }));
                } catch (Exception e) { runOnUiThread(() -> toast("Cookies: " + e.getMessage())); }
            }).start();
            cookieHost = null;
            return;
        }
        if (request == DRIVE_AUTH) {
            // The authorization result itself is authoritative: some Google flows do not
            // return RESULT_OK even when an Intent with a valid token is provided.
            if (data != null) {
                try {
                    AuthorizationResult auth = Identity.getAuthorizationClient(this).getAuthorizationResultFromIntent(data);
                    if (auth.getAccessToken() != null && !auth.getAccessToken().isEmpty()) {
                        authorized(auth.getAccessToken()); return;
                    }
                } catch (ApiException e) {
                    driveFailed("Otorisasi Drive gagal (kode " + e.getStatusCode() + "). Periksa OAuth Android dan akun penguji.");
                    return;
                } catch (Exception e) {
                    driveFailed("Drive: " + e.getMessage()); return;
                }
            }
            driveFailed("Otorisasi Drive tidak selesai setelah memilih akun (hasil " + result +
                "). Periksa OAuth Android, SHA-1 sertifikat rilis, dan akun penguji.");
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
                    getSharedPreferences("drive_account", MODE_PRIVATE).edit().putString("email", email).apply();
                    driveStatus.setText("Google Drive · Terhubung: " + email);
                    toast("Drive terhubung: " + email);
                    if ("browse".equals(action))
                        startActivity(new Intent(this, DriveBrowserActivity.class).putExtra("token", token));
                    else if ("migrate".equals(action))
                        startActivity(new Intent(this, MigrationActivity.class).putExtra("token", token)
                            .putExtra("parent", folder.getText().toString().trim()).putExtra("drive", true));
                    else if ("download".equals(action)) {
                        if (selectedStoryUrls.isEmpty()) enqueue(token);
                        else enqueueStoryBatch(token);
                    }
                });
            } catch (Exception e) { runOnUiThread(() -> driveFailed("Drive: " + e.getMessage())); }
        });
    }
    private void driveFailed(String message) {
        authorizingDrive = false;
        driveStatus.setText("Google Drive · " + (driveEmail == null ? "Belum terhubung" : "Akun terakhir: " + driveEmail));
        toast(message);
    }
    private boolean validDates() {
        String from = dateFrom.getText().toString().trim(), to = dateTo.getText().toString().trim();
        try {
            if (!from.isEmpty()) LocalDate.parse(from);
            if (!to.isEmpty()) LocalDate.parse(to);
            if (!from.isEmpty() && !to.isEmpty() && LocalDate.parse(from).isAfter(LocalDate.parse(to)))
                throw new IllegalArgumentException("Tanggal awal melewati tanggal akhir");
            return true;
        } catch (Exception e) { toast("Periksa tanggal (YYYY-MM-DD): " + e.getMessage()); return false; }
    }
    private void copyImport(Uri selected) {
        if (!validDates()) return;
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
                    updatePendingFileNotice();
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
                    if (muxSelection) {
                        staged.sort((first, second) -> {
                            boolean firstVideo = first.toLowerCase(java.util.Locale.ROOT).endsWith(".mp4");
                            boolean secondVideo = second.toLowerCase(java.util.Locale.ROOT).endsWith(".mp4");
                            return Boolean.compare(secondVideo, firstVideo);
                        });
                        if (!staged.get(0).toLowerCase(java.util.Locale.ROOT).endsWith(".mp4") ||
                            !staged.get(1).toLowerCase(java.util.Locale.ROOT).matches(".*\\.(m4a|aac|mp3|wav|ogg|opus)$")) {
                            for (String path : staged) new File(path).delete();
                            pendingLocalPaths.clear(); selectedFiles.clear(); muxSelection = false; updatePendingFileNotice();
                            toast("Pilih satu video MP4 dan satu audio (M4A/MP3/WAV)"); return;
                        }
                        pendingLocalPaths.clear(); pendingLocalPaths.addAll(staged);
                    }
                    pendingImportPath = null; pendingUrls.clear(); selectedFiles.clear(); driveAction = "download";
                    updatePendingFileNotice();
                    if (destination.equals("gallery")) enqueue(null); else authorizeDrive(driveEmail == null);
                });
            } catch (Exception e) { runOnUiThread(() -> toast("File: " + e.getMessage())); }
        }).start();
    }
    private void enqueue(String token) {
        int count = 1;
        String countText = limit.getText().toString().trim();
        if (countText.isEmpty()) count = 0;
        else try { count = Integer.parseInt(countText); if (count < 1) throw new NumberFormatException(); }
        catch (NumberFormatException bad) { toast("Maksimum item harus angka positif atau kosong untuk tanpa batas"); return; }
        saveSettings();
        try {
            int jobs = pendingLocalPaths.isEmpty() ? (pendingImportPath == null && pendingTorrentPath == null ? pendingUrls.size() : 1) : muxSelection ? 1 : pendingLocalPaths.size();
            int submitted = 0;
            for (int i = 0; i < jobs; i++) {
                String link = pendingLocalPaths.isEmpty() && pendingImportPath == null && pendingTorrentPath == null ? pendingUrls.get(i) : "";
                if (!link.isEmpty() && skipCompleted.isChecked() && History.alreadyCompleted(this, link)) continue;
                Intent job = new Intent(this, DownloadService.class).putExtra("url", link)
                    .putExtra("target", destination).putExtra("quality", quality).putExtra("count", count)
                    .putExtra("subtitles", subtitles.isChecked()).putExtra("thumbnail", thumbnail.isChecked())
                    .putExtra("metadata", metadata.isChecked())
                    .putExtra("anonymous", anonymous.isChecked())
                    .putExtra("skip_drive", skipDrive.isChecked())
                    .putExtra("verify_drive", verifyDrive.isChecked())
                    .putExtra("album_mode", albumMode.isChecked())
                    .putExtra("profile_content", profileContent)
                    .putExtra("folder_id", folder.getText().toString().trim()).putExtra("drive_token", token)
                    .putExtra("import_path", pendingImportPath).putExtra("category", importCategory)
                    .putExtra("torrent_path", pendingTorrentPath)
                    .putExtra("date_from", dateFrom.getText().toString().trim())
                    .putExtra("date_to", dateTo.getText().toString().trim())
                    .putExtra("import_output", importOutput)
                    .putExtra("local_path", pendingLocalPaths.isEmpty() ? null : pendingLocalPaths.get(i));
                if (muxSelection) job.putExtra("mux_audio_path", pendingLocalPaths.get(1));
                startForegroundService(job);
                submitted++;
            }
            toast(submitted + " tugas masuk antrean" + (submitted < jobs ? "; " + (jobs - submitted) + " sudah pernah selesai" : ""));
            pendingImportPath = null;
            pendingTorrentPath = null;
            pendingLocalPaths.clear();
            muxSelection = false;
        }
        catch (Exception e) { toast("Gagal memulai unduhan: " + e.getMessage()); }
    }
    private void enqueueStoryBatch(String token) {
        if (selectedStoryUrls.isEmpty()) return;
        saveSettings();
        ArrayList<String> urls = new ArrayList<>(selectedStoryUrls);
        try {
            Intent job = new Intent(this, DownloadService.class)
                .putStringArrayListExtra("story_urls", urls)
                .putExtra("target", destination).putExtra("quality", quality).putExtra("count", 1)
                .putExtra("subtitles", subtitles.isChecked()).putExtra("thumbnail", thumbnail.isChecked())
                .putExtra("metadata", metadata.isChecked()).putExtra("anonymous", anonymous.isChecked())
                .putExtra("skip_drive", skipDrive.isChecked()).putExtra("verify_drive", verifyDrive.isChecked())
                .putExtra("album_mode", albumMode.isChecked()).putExtra("profile_content", profileContent)
                .putExtra("folder_id", folder.getText().toString().trim()).putExtra("drive_token", token)
                .putExtra("date_from", dateFrom.getText().toString().trim())
                .putExtra("date_to", dateTo.getText().toString().trim());
            // The user chose these exact Story IDs; do not apply skip-completed to this selection.
            startForegroundService(job);
            selectedStoryUrls.clear();
            toast(urls.size() + " Story masuk antrean");
        } catch (Exception e) {
            ErrorLog.record(this, "Antrean Story", e);
            toast("Gagal memasukkan Story ke antrean. Lihat log error di Aktivitas.");
        }
    }
    private void openLogin(String site) { startActivity(new Intent(this, LoginActivity.class).putExtra("site", site)); }
    private void renderSavedSites() {
        savedSites.removeAllViews();
        JSONArray sites = SavedSites.list(this);
        for (int i = 0; i < sites.length(); i++) {
            JSONObject site = sites.optJSONObject(i);
            if (site == null) continue;
            String host = site.optString("host"), loginUrl = site.optString("url");
            if (!host.equals(WebSessions.host(loginUrl))) continue;
            Button open = button("Buka situs · " + host, Color.rgb(65, 87, 220));
            open.setOnClickListener(v -> startActivity(new Intent(this, LoginActivity.class)
                .putExtra("site", "custom").putExtra("login_url", loginUrl)));
            open.setOnLongClickListener(v -> {
                new AlertDialog.Builder(this).setMessage("Hapus tombol " + host + " dari daftar?")
                    .setNegativeButton("Batal", null)
                    .setPositiveButton("Hapus", (dialog, which) -> {
                        SavedSites.forget(this, host); renderSavedSites();
                    }).show();
                return true;
            });
            savedSites.addView(open);
        }
    }
    private void updateSiteStatus() {
        String ig = WebSessions.cookies(this, "instagram.com");
        if (ig == null || !ig.contains("sessionid=")) ig = WebSessions.cookies(this, "www.instagram.com");
        siteStatus.setText("Sesi tersimpan · Instagram: " + (ig != null && ig.contains("sessionid=") ? "ada" : "belum") +
            " · X: " + (WebSessions.hasXSession(this) ? "ada" : "belum"));
    }
    private void retryLast() {
        JSONObject previous = History.lastRetryable(this);
        if (previous == null) { toast("Tidak ada job yang bisa diulang"); return; }
        url.setText(previous.optString("url"));
        destination = previous.optString("target", "gallery");
        quality = previous.optString("quality", "best");
        int oldCount = previous.optInt("count", 1);
        limit.setText(oldCount == 0 ? "" : String.valueOf(oldCount));
        folder.setText(previous.optString("folder"));
        subtitles.setChecked(previous.optBoolean("subtitles"));
        thumbnail.setChecked(previous.optBoolean("thumbnail"));
        metadata.setChecked(previous.optBoolean("metadata"));
        anonymous.setChecked(previous.optBoolean("anonymous"));
        skipDrive.setChecked(previous.optBoolean("skip_drive"));
        verifyDrive.setChecked(previous.optBoolean("verify_drive"));
        albumMode.setChecked(previous.optBoolean("album_mode"));
        profileContent = previous.optString("profile_content", "all");
        dateFrom.setText(previous.optString("date_from"));
        dateTo.setText(previous.optString("date_to"));
        destinationLabel.setText("Tujuan · " + destinationName(destination));
        qualityLabel.setText("Kualitas · " + qualityName(quality));
        start();
    }
    private void showHistory() {
        if (historyList == null || currentTab != 4) return;
        JSONArray jobs = History.read(this);
        historyList.removeAllViews();
        if (jobs.length() == 0) {
            historyList.addView(label("Belum ada unduhan. Tugas yang kamu mulai akan muncul di sini.",
                14, Color.rgb(101, 111, 137), false));
            return;
        }
        for (int i = 0; i < Math.min(15, jobs.length()); i++) {
            JSONObject item = jobs.optJSONObject(i); if (item == null) continue;
            String state = item.optString("state");
            String message = item.optString("message").replace("Galeri:", "Lokal:")
                .replace("Menyimpan ke Galeri", "Menyimpan ke lokal");
            int color = "COMPLETED".equals(state) ? Color.rgb(20, 126, 104) :
                ("FAILED".equals(state) || "CANCELLED".equals(state)) ? Color.rgb(151, 62, 78) :
                Color.rgb(65, 87, 220);
            String status = "COMPLETED".equals(state) ? "SELESAI" :
                "FAILED".equals(state) ? "GAGAL" : "RUNNING".equals(state) ? "BERJALAN" :
                "CANCELLED".equals(state) ? "DIBATALKAN" : "INTERRUPTED".equals(state) ? "TERHENTI" :
                "UPLOADING".equals(state) ? "UNGGAH DRIVE" : "SAVING".equals(state) ? "MENYIMPAN" : "ANTREAN";
            LinearLayout card = panel(historyList);
            card.addView(label(status + (item.optInt("progress", 0) > 0 && !"COMPLETED".equals(state) ?
                "  ·  " + item.optInt("progress") + "%" : ""), 12, color, true));
            String[] lines = message.split("\\n");
            String headline = lines.length == 0 ? "Tugas unduhan" : lines[0].trim();
            if (headline.startsWith("Lokal:")) {
                headline = headline.substring("Lokal:".length()).trim();
                headline = headline.substring(headline.lastIndexOf('/') + 1);
            } else if (headline.startsWith("Drive:")) headline = "File tersimpan ke Drive";
            TextView summary = label(headline.isEmpty() ? "Tugas unduhan" : headline, 15, Color.rgb(30, 39, 65), true);
            summary.setMaxLines(2); summary.setEllipsize(android.text.TextUtils.TruncateAt.END);
            card.addView(summary);
            int local = 0, drive = 0;
            for (String line : lines) {
                if (line.startsWith("Lokal:")) local++;
                if (line.startsWith("Drive:")) drive++;
            }
            String count = local > 0 || drive > 0 ?
                (local > 0 ? local + " file lokal" : "") + (local > 0 && drive > 0 ? "  ·  " : "") +
                    (drive > 0 ? drive + " file Drive" : "") : "Ketuk untuk membaca detail";
            card.addView(label(count, 12, Color.rgb(101, 111, 137), false));
            card.setOnClickListener(v -> showJobDetail(status, message));
        }
    }
    private void showJobDetail(String status, String message) {
        TextView detail = label(message, 14, Color.rgb(30, 39, 65), false);
        detail.setPadding(dp(18), dp(12), dp(18), dp(16));
        detail.setTextIsSelectable(true);
        Linkify.addLinks(detail, Linkify.WEB_URLS);
        detail.setMovementMethod(LinkMovementMethod.getInstance());
        ScrollView scroll = new ScrollView(this); scroll.addView(detail);
        new AlertDialog.Builder(this).setTitle(status).setView(scroll).setPositiveButton("Tutup", null).show();
    }
    private void showErrorLog() {
        String report = ErrorLog.read(this);
        TextView detail = label(report, 14, Color.rgb(30, 39, 65), false);
        detail.setPadding(dp(18), dp(12), dp(18), dp(16));
        detail.setTextIsSelectable(true);
        ScrollView scroll = new ScrollView(this); scroll.addView(detail);
        new AlertDialog.Builder(this).setTitle("Log error")
            .setView(scroll)
            .setNeutralButton("Bagikan log", (dialog, which) -> {
                Intent share = new Intent(Intent.ACTION_SEND).setType("text/plain")
                    .putExtra(Intent.EXTRA_TEXT, report);
                startActivity(Intent.createChooser(share, "Bagikan log The Great Drive"));
            })
            .setPositiveButton("Tutup", null).show();
    }
    private interface Select { void pick(String value); }
    private void row(LinearLayout parent, String[] names, String[] values, Select select) {
        LinearLayout group = new LinearLayout(this); group.setOrientation(LinearLayout.VERTICAL);
        int columns = names.length >= 4 ? (names.length == 5 ? 3 : 2) : names.length;
        LinearLayout line = null;
        for (int i = 0; i < names.length; i++) {
            if (i % columns == 0) {
                line = new LinearLayout(this); line.setOrientation(LinearLayout.HORIZONTAL);
                group.addView(line);
            }
            final String value = values[i]; Button b = button(names[i], Color.rgb(225, 230, 249));
            b.setTextColor(Color.rgb(40, 51, 108)); b.setTextSize(12);
            LinearLayout.LayoutParams chip = new LinearLayout.LayoutParams(0, dp(45), 1);
            chip.rightMargin = dp(4); chip.topMargin = dp(5);
            line.addView(b, chip);
            b.setOnClickListener(v -> select.pick(value));
        }
        parent.addView(group);
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

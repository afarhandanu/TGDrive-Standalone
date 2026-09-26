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
public class MainActivity extends LocalizedActivity {
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
    private TextView tiktokPhotoModeLabel, tiktokWatermarkLabel;
    private TextView pendingFileNotice;
    private LinearLayout historyList;
    private LinearLayout savedSites;
    private ScrollView[] tabs;
    private LinearLayout[] tabButtons;
    private ImageView[] tabIcons;
    private TextView[] tabLabels;
    private int currentTab;
    private String destination = "gallery", quality = "best";
    private String tiktokPhotoMode = "combine", tiktokWatermark = "without";
    private String pendingUrl;
    private String pendingImportPath;
    private String pendingTorrentPath;
    private boolean muxSelection;
    private final ArrayList<Uri> selectedFiles = new ArrayList<>();
    private final ArrayList<String> pendingLocalPaths = new ArrayList<>();
    private final ArrayList<String> pendingMuxImages = new ArrayList<>();
    private String pendingMuxVideoPath, pendingMuxAudioPath;
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
    private boolean driveConnected;
    private CheckBox automaticRetry;
    private TextView driveIndicator;
    private Button disconnectDrive;
    private int driveGeneration;
    private int driveResolutionGeneration = -1;
    private boolean silentDriveCheck;
    private android.net.ConnectivityManager connectivity;
    private android.net.ConnectivityManager.NetworkCallback networkCallback;
    private final ArrayList<Intent> pendingRetries = new ArrayList<>();


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
        watchNetwork();
        if (driveEmail != null && networkOnline()) restoreDriveSession();
        else updateDriveIndicator();
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
        driveGeneration++;
        if (receiver != null) unregisterReceiver(receiver);
        if (networkCallback != null) connectivity.unregisterNetworkCallback(networkCallback);
        driveIo.shutdownNow(); storyPreviewIo.shutdownNow(); super.onDestroy();
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
            url.setHint(t("1 file dibagikan · pilih tujuan lalu mulai"));
        } else if (Intent.ACTION_SEND_MULTIPLE.equals(intent.getAction())) {
            selectedFiles.clear(); muxSelection = false;
            var streams = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
            if (streams != null) for (Object stream : streams) if (stream instanceof Uri) selectedFiles.add((Uri) stream);
            url.setHint(selectedFiles.size() + t(" file dibagikan · pilih tujuan lalu mulai"));
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
        LinearLayout statusRow = new LinearLayout(this); statusRow.setGravity(Gravity.END);
        driveIndicator = label("", 12, navy, true);
        driveIndicator.setPadding(dp(12), dp(8), dp(12), dp(8));
        driveIndicator.setMinHeight(dp(40));
        driveIndicator.setOnClickListener(v -> openTab(2));
        statusRow.addView(driveIndicator); outer.addView(statusRow);
        TextView title = label(t("Simpan yang kamu suka."), 30, navy, true); outer.addView(title);
        TextView sub = label(t("Tautan masuk, pilih tujuan, lalu unduh. Semua proses berjalan di perangkatmu."), 15, muted, false);
        outer.addView(sub);
        pageTitle(options, t("Opsi unduhan"), t("Kualitas, folder, dan filter yang kamu pilih akan tetap tersimpan."));
        pageTitle(accounts, t("Akun & Drive"), t("Kelola login situs dan file di Google Drive."));
        pageTitle(filesPage, t("Berkas & impor"), t("Impor data Instagram atau pilih file dari ponsel."));
        pageTitle(activity, t("Aktivitas"), t("Pantau antrean dan buka detail setiap hasil unduhan."));
        pageTitle(info, t("Info aplikasi"), t("Tentang The Great Drive, daftar fitur, dan perubahan versi."));

        LinearLayout card = new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(18), dp(18), dp(18));
        GradientDrawable bg = new GradientDrawable(); bg.setColor(Color.WHITE); bg.setCornerRadius(dp(24));
        card.setBackground(bg); card.setElevation(dp(4));
        LinearLayout.LayoutParams cardP = new LinearLayout.LayoutParams(-1, -2); cardP.topMargin = dp(24); outer.addView(card, cardP);
        card.addView(label(t("TAUTAN MEDIA"), 12, muted, true));
        url = new EditText(this); url.setHint(t("Tempel satu atau beberapa link video, post, Reel, Story…")); url.setSingleLine(false);
        url.setTextColor(ink); url.setTextSize(16); card.addView(url);
        pendingFileNotice = label("", 13, Color.rgb(65, 87, 220), true);
        pendingFileNotice.setVisibility(View.GONE);
        pendingFileNotice.setOnClickListener(v -> {
            selectedFiles.clear(); muxSelection = false; updatePendingFileNotice();
        });
        card.addView(pendingFileNotice);

        destinationLabel = label(t("Tujuan · Lokal"), 15, ink, true); card.addView(destinationLabel);
        row(card, new String[]{t("Lokal"), "Drive", t("Keduanya")}, new String[]{"gallery", "drive", "both"}, value -> {
            destination = value;
            destinationLabel.setText(t("Tujuan · ") + destinationName(value));
        });
        qualityLabel = label(t("Kualitas · Terbaik"), 15, ink, true); card.addView(qualityLabel);
        row(card, new String[]{t("Terbaik"), "1080p", "720p", "Audio"}, new String[]{"best", "1080", "720", "audio"}, value -> {
            quality = value; qualityLabel.setText(t("Kualitas · ") + qualityName(value));
        });
        row(card, new String[]{"2160p", "1440p", "480p", t("Video saja")},
            new String[]{"2160", "1440", "480", "video"}, value -> {
                quality = value; qualityLabel.setText(t("Kualitas · ") + qualityName(value));
            });
        card.addView(label(t("Maksimum item (profil / playlist)"), 13, muted, false));
        limit = new EditText(this); limit.setInputType(2); limit.setHint(t("Kosong = tanpa batas")); limit.setText("1"); card.addView(limit);
        Button go = button(t("Mulai download  ↗"), navy); go.setOnClickListener(v -> start()); card.addView(go);
        Button pickStories = button(t("Pilih Story Instagram"), Color.rgb(65, 87, 220));
        pickStories.setOnClickListener(v -> pickStories()); outer.addView(pickStories);
        Button configure = button(t("Atur opsi unduhan & folder  →"), Color.rgb(225, 230, 249));
        configure.setTextColor(ink); configure.setOnClickListener(v -> openTab(1)); outer.addView(configure);

        LinearLayout languageCard = panel(options);
        languageCard.addView(label(t("Bahasa aplikasi"), 17, ink, true));
        languageCard.addView(label(t("Pilihan bahasa disimpan di perangkat ini."), 13, muted, false));
        android.widget.RadioGroup languages = new android.widget.RadioGroup(this);
        String[] languageCodes = {"id", "en"};
        String[] languageNames = {"Bahasa Indonesia", "English"};
        for (int i = 0; i < languageCodes.length; i++) {
            final String code = languageCodes[i];
            android.widget.RadioButton choice = new android.widget.RadioButton(this);
            choice.setId(View.generateViewId()); choice.setText(languageNames[i]);
            languages.addView(choice);
            choice.setChecked(code.equals(L10n.language(this)));
            choice.setOnClickListener(v -> changeLanguage(code));
        }
        languageCard.addView(languages);
        LinearLayout retryCard = panel(options);
        retryCard.addView(label(t("KONEKSI & RETRY"), 12, muted, true));
        automaticRetry = new CheckBox(this); automaticRetry.setText(t("Retry otomatis saat koneksi media terputus"));
        automaticRetry.setChecked(true); retryCard.addView(automaticRetry);
        retryCard.addView(label(t("Maksimal 3 percobaan ulang per media. Pilih pengaturan per tautan atau Story; retry manual tersedia di Aktivitas."), 13, muted, false));
        Button retryChoices = button(t("Atur retry per tautan"), navy);
        retryChoices.setOnClickListener(v -> chooseAutomaticLinks()); retryCard.addView(retryChoices);
        LinearLayout optionCard = panel(options);
        optionCard.addView(label(t("PILIHAN FILE"), 12, muted, true));
        subtitles = new CheckBox(this); subtitles.setText(t("Sertakan subtitle jika ada")); optionCard.addView(subtitles);
        thumbnail = new CheckBox(this); thumbnail.setText(t("Sertakan thumbnail")); optionCard.addView(thumbnail);
        metadata = new CheckBox(this); metadata.setText(t("Sertakan metadata JSON")); optionCard.addView(metadata);
        skipCompleted = new CheckBox(this); skipCompleted.setText(t("Lewati link yang sudah berhasil diunduh")); optionCard.addView(skipCompleted);
        anonymous = new CheckBox(this); anonymous.setText(t("Tanpa akun untuk link publik (abaikan sesi login)")); optionCard.addView(anonymous);
        skipDrive = new CheckBox(this); skipDrive.setText(t("Drive: lewati file dengan nama yang sama")); optionCard.addView(skipDrive);
        verifyDrive = new CheckBox(this); verifyDrive.setText(t("Drive: cocokkan checksum MD5 setelah unggah")); optionCard.addView(verifyDrive);
        albumMode = new CheckBox(this);
        albumMode.setText(t("Mode profil / album (Instagram, X, Facebook; termasuk carousel)")); optionCard.addView(albumMode);
        profileContentLabel = label(t("Profil Facebook · Semua"), 13, muted, false);
        optionCard.addView(profileContentLabel);
        row(optionCard, new String[]{t("Semua"), t("Foto"), "Video"},
            new String[]{"all", "photos", "videos"}, value -> {
                profileContent = value;
                profileContentLabel.setText(t("Profil Facebook · ") + (value.equals("photos") ? t("Foto") : value.equals("videos") ? "Video" : t("Semua")));
            });
        optionCard.addView(label(t("ID folder Drive (opsional)"), 13, muted, false));
        folder = new EditText(this); folder.setHint(t("Kosong = My Drive")); optionCard.addView(folder);
        optionCard.addView(label(t("Rentang tanggal import / profil (opsional, YYYY-MM-DD)"), 13, muted, false));
        dateFrom = new EditText(this); dateFrom.setHint(t("Dari tanggal")); dateFrom.setSingleLine(true);
        dateFrom.setInputType(android.text.InputType.TYPE_CLASS_DATETIME | android.text.InputType.TYPE_DATETIME_VARIATION_DATE);
        optionCard.addView(dateFrom);
        dateTo = new EditText(this); dateTo.setHint(t("Sampai tanggal")); dateTo.setSingleLine(true);
        dateTo.setInputType(android.text.InputType.TYPE_CLASS_DATETIME | android.text.InputType.TYPE_DATETIME_VARIATION_DATE);
        optionCard.addView(dateTo);

        LinearLayout tiktokCard = panel(options);
        tiktokCard.addView(label("TIKTOK", 12, muted, true));
        tiktokCard.addView(label(t("Atur hasil post foto/carousel dan pilihan watermark untuk video TikTok."), 13, muted, false));
        tiktokPhotoModeLabel = label(t("Foto / carousel · Gabungkan jadi video"), 14, ink, true);
        tiktokCard.addView(tiktokPhotoModeLabel);
        rowWithIcons(tiktokCard,
            new String[]{t("Gabungkan jadi video"), t("Simpan terpisah")},
            new String[]{"combine", "separate"},
            new int[]{R.drawable.ic_media_combine, R.drawable.ic_media_separate}, value -> {
                tiktokPhotoMode = value;
                tiktokPhotoModeLabel.setText(t("Foto / carousel · ") +
                    ("combine".equals(value) ? t("Gabungkan jadi video") : t("Simpan terpisah")));
            });
        tiktokCard.addView(label(t("Mode gabung membuat satu MP4 dari semua gambar dan audio. Mode terpisah menyimpan gambar dan audio seperti sumber."), 12, muted, false));
        tiktokWatermarkLabel = label(t("Video TikTok · Tanpa watermark"), 14, ink, true);
        tiktokCard.addView(tiktokWatermarkLabel);
        rowWithIcons(tiktokCard,
            new String[]{t("Tanpa watermark"), t("Dengan watermark")},
            new String[]{"without", "with"},
            new int[]{R.drawable.ic_watermark_off, R.drawable.ic_watermark_on}, value -> {
                tiktokWatermark = value;
                tiktokWatermarkLabel.setText(t("Video TikTok · ") +
                    ("with".equals(value) ? t("Dengan watermark") : t("Tanpa watermark")));
            });
        tiktokCard.addView(label(t("Pilihan watermark berlaku pada post video jika TikTok menyediakan format tersebut; slideshow foto tidak diberi watermark buatan."), 12, muted, false));

        LinearLayout loginCard = panel(accounts);
        loginCard.addView(label(t("LOGIN SITUS"), 12, muted, true));
        Button instagram = button(t("Masuk Instagram"), Color.rgb(225, 62, 118));
        instagram.setOnClickListener(v -> openLogin("instagram")); loginCard.addView(instagram);
        Button x = button(t("Buka X / login"), navy); x.setOnClickListener(v -> openLogin("x")); loginCard.addView(x);
        siteStatus = label(t("Sesi situs"), 13, muted, false); loginCard.addView(siteStatus);
        Button other = button(t("Masuk situs lain (URL HTTPS)"), navy);
        other.setOnClickListener(v -> {
            EditText input = new EditText(this);
            input.setSingleLine(true);
            input.setHint(t("https://contoh.com/login"));
            input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI);
            String current = url.getText().toString().trim();
            if (WebSessions.host(current) != null) input.setText(current);
            new AlertDialog.Builder(this).setTitle(t("Alamat halaman login situs"))
                .setView(input)
                .setNegativeButton(t("Batal"), null)
                .setPositiveButton(t("Buka"), (dialog, which) -> {
                    String loginUrl = input.getText().toString().trim();
                    if (WebSessions.host(loginUrl) == null) { toast(t("Masukkan URL HTTPS situs yang valid")); return; }
                    try { SavedSites.remember(this, loginUrl); }
                    catch (IllegalArgumentException e) { toast(message(e.getMessage())); return; }
                    renderSavedSites();
                    startActivity(new Intent(this, LoginActivity.class)
                        .putExtra("site", "custom").putExtra("login_url", loginUrl));
                }).show();
        }); loginCard.addView(other);
        Button cookies = button(t("Impor cookies.txt untuk situs"), navy);
        cookies.setOnClickListener(v -> {
            EditText input = new EditText(this);
            input.setSingleLine(true); input.setHint(t("https://contoh.com/"));
            String current = url.getText().toString().trim();
            if (WebSessions.host(current) != null) input.setText(current);
            new AlertDialog.Builder(this).setTitle(t("Situs pemilik cookies"))
                .setView(input).setNegativeButton(t("Batal"), null)
                .setPositiveButton(t("Pilih cookies.txt"), (d, w) -> {
                    String address = input.getText().toString().trim();
                    cookieHost = WebSessions.host(address);
                    if (cookieHost == null) { toast(t("Gunakan alamat HTTPS situs")); return; }
                    try { SavedSites.remember(this, address); renderSavedSites(); }
                    catch (IllegalArgumentException e) { toast(message(e.getMessage())); return; }
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
        driveStatus = label("Google Drive · " + (driveEmail == null ? t("Belum terhubung") :
            t("Memeriksa akun ") + driveEmail + "…"), 14, ink, true); driveCard.addView(driveStatus);
        Button connect = button(t("Hubungkan / ganti akun Google Drive"), Color.rgb(65, 87, 220));
        connect.setOnClickListener(v -> { driveAction = "connect"; authorizeDrive(true); }); driveCard.addView(connect);
        Button files = button(t("Kelola file Google Drive"), Color.rgb(65, 87, 220));
        files.setOnClickListener(v -> { driveAction = "browse"; authorizeDrive(driveEmail == null); }); driveCard.addView(files);
        disconnectDrive = button(t("Putuskan koneksi Drive"), Color.rgb(151, 62, 78));
        disconnectDrive.setOnClickListener(v -> disconnectDriveAccount()); driveCard.addView(disconnectDrive);
        driveCard.addView(label(t("Koneksi Drive di aplikasi ini diputus. File tetap tersimpan; unggahan yang belum selesai dapat diulang setelah terhubung kembali."), 12, muted, false));
        updateDriveIndicator();
        LinearLayout importCard = panel(filesPage);
        importCategoryLabel = label(t("IMPORT INSTAGRAM JSON / ZIP · SEMUA"), 13, muted, true);
        importCard.addView(importCategoryLabel);
        row(importCard, new String[]{t("Semua"), t("Feed"), t("Reels"), t("Stories"), t("Tagged")},
            new String[]{"all", "feed", "reels", "stories", "mentions"}, value -> {
                importCategory = value; importCategoryLabel.setText(t("IMPORT INSTAGRAM JSON / ZIP · ") + categoryName(value));
            });
        Button dateOptions = button(t("Atur tanggal & opsi import  →"), Color.rgb(225, 230, 249));
        dateOptions.setTextColor(ink);
        dateOptions.setOnClickListener(v -> openTab(1)); importCard.addView(dateOptions);
        importOutputLabel = label(t("Hasil import · Folder"), 13, ink, true);
        importCard.addView(importOutputLabel);
        row(importCard, new String[]{"Folder", t("Satu ZIP")}, new String[]{"folder", "zip"}, value -> {
            importOutput = value;
            importOutputLabel.setText(t("Hasil import · ") + (value.equals("zip") ? t("Satu ZIP") : "Folder"));
        });
        Button importButton = button(t("Pilih file JSON / ZIP"), Color.rgb(65, 87, 220));
        importButton.setOnClickListener(v -> {
            Intent choose = new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*")
                .addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(choose, IMPORT_FILE);
        }); importCard.addView(importButton);
        LinearLayout localCard = panel(filesPage);
        localCard.addView(label(t("FILE DARI PONSEL"), 12, muted, true));
        Button pickLocal = button(t("Pilih file dari ponsel"), navy);
        pickLocal.setOnClickListener(v -> startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT)
            .setType("*/*").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true), LOCAL_FILE));
        localCard.addView(pickLocal);
        Button pickTorrent = button(t("Pilih file .torrent"), Color.rgb(65, 87, 220));
        pickTorrent.setOnClickListener(v -> startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT)
            .setType("*/*").addCategory(Intent.CATEGORY_OPENABLE), TORRENT_FILE));
        localCard.addView(pickTorrent);
        Button muxFiles = button(t("Gabungkan media + audio (FFmpeg)"), navy);
        muxFiles.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_media_combine, 0, 0, 0);
        muxFiles.setCompoundDrawablePadding(dp(10));
        if (Build.VERSION.SDK_INT >= 23) muxFiles.setCompoundDrawableTintList(android.content.res.ColorStateList.valueOf(Color.WHITE));
        muxFiles.setOnClickListener(v -> startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT)
            .setType("*/*").addCategory(Intent.CATEGORY_OPENABLE)
            .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true), MUX_FILES));
        localCard.addView(muxFiles);
        localCard.addView(label(t("Pilih 1 video + 1 audio, atau satu/lebih gambar + 1 audio. Setelah dipilih, proses FFmpeg langsung masuk antrean tanpa kembali ke beranda."), 12, muted, false));
        LinearLayout migrationCard = panel(filesPage);
        migrationCard.addView(label(t("PINDAHKAN FOLDER LAMA"), 12, muted, true));
        migrationCard.addView(label(t("TGDrive → The Great Drive. Pilih simpan file lama atau hapus setelah salinan terverifikasi."), 14, muted, false));
        Button migrateLocal = button(t("Pindahkan folder Lokal"), navy);
        migrateLocal.setOnClickListener(v -> startActivity(new Intent(this, MigrationActivity.class)));
        migrationCard.addView(migrateLocal);
        Button migrateDrive = button(t("Pindahkan folder Google Drive"), Color.rgb(65, 87, 220));
        migrateDrive.setOnClickListener(v -> { driveAction = "migrate"; authorizeDrive(driveEmail == null); });
        migrationCard.addView(migrateDrive);

        LinearLayout queueCard = panel(activity);
        queueCard.addView(label(t("KONTROL ANTREAN"), 12, muted, true));
        Button cancel = button(t("Batalkan job aktif"), Color.rgb(132, 52, 72));
        cancel.setOnClickListener(v -> startService(new Intent(this, DownloadService.class).setAction("CANCEL")));
        queueCard.addView(cancel);
        Button retry = button(t("Ulangi job gagal / batal"), Color.rgb(65, 87, 220));
        retry.setOnClickListener(v -> retryLast()); queueCard.addView(retry);
        Button pause = button(t("Jeda / lanjutkan antrean"), Color.rgb(65, 87, 220));
        pause.setOnClickListener(v -> {
            boolean next = !DownloadService.isPaused();
            startService(new Intent(this, DownloadService.class).setAction(next ? "PAUSE" : "RESUME"));
            toast(next ? t("Tugas berikutnya dijeda; tugas aktif tetap berjalan") : t("Antrean dilanjutkan"));
        }); queueCard.addView(pause);
        TextView recent = label(t("RIWAYAT TERBARU"), 13, muted, true);
        Button errors = button(t("Lihat log error"), navy);
        errors.setOnClickListener(v -> showErrorLog());
        activity.addView(errors);
        LinearLayout.LayoutParams recentP = new LinearLayout.LayoutParams(-1, -2); recentP.topMargin = dp(24);
        activity.addView(recent, recentP);
        historyList = new LinearLayout(this); historyList.setOrientation(LinearLayout.VERTICAL);
        activity.addView(historyList);
        LinearLayout backupCard = panel(accounts);
        backupCard.addView(label(t("DATA & PERANGKAT"), 12, muted, true));
        Button backup = button(t("Ekspor riwayat dan daftar situs"), navy);
        backup.setOnClickListener(v -> startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE).setType("application/json")
            .putExtra(Intent.EXTRA_TITLE, "The-Great-Drive-backup.json"), EXPORT_BACKUP));
        backupCard.addView(backup);
        Button restore = button(t("Pulihkan riwayat dan daftar situs"), navy);
        restore.setOnClickListener(v -> {
            if (DownloadService.hasPendingJobs()) { toast(t("Tunggu antrean selesai sebelum memulihkan cadangan")); return; }
            startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE).setType("application/json"), IMPORT_BACKUP);
        }); backupCard.addView(restore);
        backupCard.addView(label(t("Cadangan memuat riwayat tautan dan alamat situs; sesi login tetap tersimpan hanya di perangkat ini."), 12, muted, false));
        Button admin = button(t("Administrasi perangkat"), navy);
        admin.setOnClickListener(v -> startActivity(new Intent(this, AdminActivity.class)));
        backupCard.addView(admin);
        buildInfo(info);
        installTabs(outer, options, accounts, filesPage, activity, info);
        showHistory();
    }
    private void changeLanguage(String code) {
        if (code.equals(L10n.language(this))) return;
        saveSettings();
        String enteredUrl = url.getText().toString();
        int previousTab = currentTab;
        L10n.setLanguage(this, code);
        L10n.refreshNotifications(this);
        render(); restoreSettings();
        url.setText(enteredUrl); updatePendingFileNotice();
        updateDriveIndicator();
        updateSiteStatus(); renderSavedSites(); openTab(previousTab);
    }
    private String categoryName(String category) {
        switch (category) {
            case "feed": return t("Feed");
            case "reels": return t("Reels");
            case "stories": return t("Stories");
            case "mentions": return t("Tagged");
            default: return t("Semua");
        }
    }
    private ImageView brandBanner(int heightDp) {
        ImageView banner = new ImageView(this);
        banner.setImageResource(R.drawable.brand_banner);
        banner.setScaleType(ImageView.ScaleType.CENTER_CROP);
        banner.setContentDescription(t("Logo The Great Drive"));
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
        String[] titles = {t("Tentang"), t("Fitur"), t("Changelog")};
        for (int i = 0; i < titles.length; i++) {
            final int selected = i;
            Button button = button(titles[i], Color.rgb(225, 230, 249));
            button.setTextSize(12); buttons[i] = button;
            LinearLayout.LayoutParams choiceParams = new LinearLayout.LayoutParams(0, -2, 1);
            choiceParams.setMargins(dp(3), dp(10), dp(3), 0);
            button.setMinHeight(dp(48)); selector.addView(button, choiceParams);
            button.setOnClickListener(v -> {
                for (int j = 0; j < sections.length; j++) {
                    sections[j].setVisibility(j == selected ? View.VISIBLE : View.GONE);
                    GradientDrawable selectedShape = new GradientDrawable();
                    selectedShape.setColor(j == selected ? Color.rgb(65, 87, 220) : Color.rgb(225, 230, 249));
                    selectedShape.setCornerRadius(dp(14)); buttons[j].setBackground(selectedShape);
                    buttons[j].setTextColor(j == selected ? Color.WHITE : Color.rgb(40, 51, 108));
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
        try (InputStream input = getAssets().open("info/" + L10n.language(this) + "/" + asset)) {
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
            ErrorLog.record(this, t("Info aplikasi"), error);
            parent.addView(label(t("Dokumen belum dapat dibuka."), 14, Color.RED, false));
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
        content.addView(label(t("Unduh tautan dan simpan media ke ponsel, Google Drive, atau keduanya. ") +
            t("Antrean dan sesi situs dikelola di perangkat ini."), 14, Color.rgb(101, 111, 137), false));
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
        navigation.setPadding(dp(4), dp(6), dp(4), dp(6));
        navigation.setBackgroundColor(Color.WHITE);
        navigation.setElevation(dp(8));
        tabButtons = new LinearLayout[pages.length];
        tabIcons = new ImageView[pages.length];
        tabLabels = new TextView[pages.length];
        String[] names = {t("↓\nUnduh"), t("⚙\nOpsi"), t("○\nAkun"), t("▣\nBerkas"), t("≡\nAktivitas"), t("ⓘ\nInfo")};
        int[] icons = {R.drawable.nav_download, R.drawable.nav_options, R.drawable.nav_accounts,
            R.drawable.nav_files, R.drawable.nav_activity, R.drawable.nav_info};
        for (int i = 0; i < pages.length; i++) {
            final int index = i;
            LinearLayout tab = new LinearLayout(this);
            tab.setOrientation(LinearLayout.VERTICAL);
            tab.setGravity(Gravity.CENTER);
            ImageView icon = new ImageView(this);
            icon.setImageResource(icons[i]);
            tab.addView(icon, new LinearLayout.LayoutParams(dp(26), dp(26)));
            TextView title = label(names[i].substring(names[i].indexOf('\n') + 1), 12,
                Color.rgb(101, 111, 137), false);
            title.setGravity(Gravity.CENTER);
            title.setSingleLine(true);
            title.setMaxLines(1);
            title.setAutoSizeTextTypeUniformWithConfiguration(10, 12, 1, android.util.TypedValue.COMPLEX_UNIT_SP);
            LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(-1, dp(22));
            titleParams.topMargin = dp(4);
            tab.addView(title, titleParams);
            tab.setContentDescription(names[i].substring(names[i].indexOf('\n') + 1));
            tab.setOnClickListener(v -> openTab(index));
            tabButtons[i] = tab;
            tabIcons[i] = icon;
            tabLabels[i] = title;
            navigation.addView(tab, new LinearLayout.LayoutParams(0, dp(72), 1));
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
            int color = selected ? Color.rgb(65, 87, 220) : Color.rgb(101, 111, 137);
            tabIcons[i].setColorFilter(color);
            tabLabels[i].setTextColor(color);
            tabLabels[i].setTypeface(Typeface.DEFAULT, selected ? Typeface.BOLD : Typeface.NORMAL);
            GradientDrawable background = new GradientDrawable();
            background.setColor(selected ? Color.rgb(235, 240, 255) : Color.WHITE);
            background.setCornerRadius(dp(14));
            tabButtons[i].setBackground(background);
        }
        if (index == 4 && historyList != null) showHistory();
    }
    private String destinationName(String value) {
        return value.equals("both") ? t("Lokal + Drive") : value.equals("drive") ? "Drive" : t("Lokal");
    }
    private String qualityName(String value) {
        return value.equals("best") ? t("Terbaik") : value.equals("audio") ? "Audio" :
            value.equals("video") ? t("Video saja") : value + "p";
    }
    private void updatePendingFileNotice() {
        if (pendingFileNotice == null) return;
        pendingFileNotice.setVisibility(selectedFiles.isEmpty() ? View.GONE : View.VISIBLE);
        if (!selectedFiles.isEmpty()) pendingFileNotice.setText(selectedFiles.size() +
            (muxSelection ? t(" file untuk digabung") : t(" file dari ponsel")) + t(" dipilih · ketuk untuk batal"));
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
            toast(t("Masukkan URL http/https yang valid")); return;
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
                .put("destination", destination).put("quality", quality).put("auto_retry", automaticRetry.isChecked())
                .put("limit", limit.getText().toString()).put("folder", folder.getText().toString())
                .put("subtitles", subtitles.isChecked()).put("thumbnail", thumbnail.isChecked())
                .put("metadata", metadata.isChecked()).put("skip_completed", skipCompleted.isChecked())
                .put("anonymous", anonymous.isChecked()).put("skip_drive", skipDrive.isChecked())
                .put("verify_drive", verifyDrive.isChecked()).put("album_mode", albumMode.isChecked())
                .put("profile_content", profileContent).put("import_category", importCategory)
                .put("import_output", importOutput)
                .put("tiktok_photo_mode", tiktokPhotoMode).put("tiktok_watermark", tiktokWatermark)
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
            automaticRetry.setChecked(data.optBoolean("auto_retry", true));
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
            tiktokPhotoMode = data.optString("tiktok_photo_mode", "combine");
            if (!tiktokPhotoMode.equals("combine") && !tiktokPhotoMode.equals("separate")) tiktokPhotoMode = "combine";
            tiktokWatermark = data.optString("tiktok_watermark", "without");
            if (!tiktokWatermark.equals("with") && !tiktokWatermark.equals("without")) tiktokWatermark = "without";
            dateFrom.setText(data.optString("date_from", "")); dateTo.setText(data.optString("date_to", ""));
            destinationLabel.setText(t("Tujuan · ") + destinationName(destination));
            qualityLabel.setText(t("Kualitas · ") + qualityName(quality));
            profileContentLabel.setText(t("Profil Facebook · ") + (profileContent.equals("photos") ? t("Foto") : profileContent.equals("videos") ? "Video" : t("Semua")));
            importCategoryLabel.setText(t("IMPORT INSTAGRAM JSON / ZIP · ") + categoryName(importCategory));
            importOutputLabel.setText(t("Hasil import · ") + (importOutput.equals("zip") ? t("Satu ZIP") : "Folder"));
            tiktokPhotoModeLabel.setText(t("Foto / carousel · ") +
                (tiktokPhotoMode.equals("combine") ? t("Gabungkan jadi video") : t("Simpan terpisah")));
            tiktokWatermarkLabel.setText(t("Video TikTok · ") +
                (tiktokWatermark.equals("with") ? t("Dengan watermark") : t("Tanpa watermark")));
        } catch (Exception ignored) { toast(t("Pengaturan tersimpan tidak bisa dibaca")); }
        finally { restoringSettings = false; }
    }
    private void pickStories() {
        String address = url.getText().toString().trim();
        Matcher story = Pattern.compile("^https://(?:www\\.)?instagram\\.com/stories/([A-Za-z0-9._]+)/?(?:\\?.*)?$", Pattern.CASE_INSENSITIVE).matcher(address);
        if (!story.matches()) { toast(t("Isi tautan profil Story: instagram.com/stories/username/")); return; }
        String username = story.group(1);
        String cookies = WebSessions.cookies(this, "www.instagram.com");
        if (cookies == null || !cookies.contains("sessionid=")) cookies = WebSessions.cookies(this, "instagram.com");
        if (cookies == null || !cookies.contains("sessionid=")) { toast(t("Masuk Instagram dahulu untuk melihat Story aktif")); return; }
        final String session = cookies;
        toast(t("Memuat daftar Story…"));
        new Thread(() -> {
            try {
                JSONArray items = new JSONArray(Python.getInstance().getModule("story_picker")
                    .callAttr("list_stories", username, session).toString());
                runOnUiThread(() -> showStoryChoices(items));
            } catch (Exception e) {
                ErrorLog.record(this, t("Pemilih Story Instagram"), e);
                runOnUiThread(() -> new AlertDialog.Builder(this)
                    .setTitle(t("Gagal memuat Story"))
                    .setMessage(t("Detail error tersimpan di Aktivitas → Lihat log error. Pastikan sesi Instagram masih aktif, lalu coba lagi."))
                    .setPositiveButton(t("Buka log"), (dialog, which) -> { openTab(4); showErrorLog(); })
                    .setNegativeButton(t("Tutup"), null).show());
            }
        }).start();
    }
    private void showStoryChoices(JSONArray items) {
        int n = items.length();
        if (n == 0) { toast(t("Tidak ada Story aktif yang dapat dibaca")); return; }
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
                (item.optBoolean("video") ? "Video" : t("Foto")) + " · " + item.optString("date") +
                "\nID " + item.optString("id");
            TextView title = label(labelText, 14, Color.rgb(30, 39, 65), false);
            LinearLayout storyText = new LinearLayout(this); storyText.setOrientation(LinearLayout.VERTICAL);
            storyText.addView(title);
            if (item != null) {
                CheckBox auto = new CheckBox(this); auto.setText(t("Retry otomatis")); auto.setTextSize(12);
                auto.setChecked(autoRetryFor(item.optString("url")));
                auto.setOnCheckedChangeListener((view, value) -> getSharedPreferences("media_retry", MODE_PRIVATE)
                    .edit().putBoolean(item.optString("url"), value).apply());
                storyText.addView(auto);
            }
            row.addView(storyText, new LinearLayout.LayoutParams(0, -2, 1));
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
                            .setView(player).setPositiveButton(t("Tutup"), null).create();
                        dialog.setOnDismissListener(d -> player.stopPlayback());
                        dialog.show(); player.start();
                    } else if (image.getDrawable() instanceof android.graphics.drawable.BitmapDrawable) {
                        ImageView larger = new ImageView(this);
                        larger.setImageDrawable(image.getDrawable());
                        larger.setAdjustViewBounds(true);
                        larger.setPadding(dp(8), dp(8), dp(8), dp(8));
                        new AlertDialog.Builder(this).setTitle(labelText).setView(larger)
                            .setPositiveButton(t("Tutup"), null).show();
                    } else toast(t("Pratinjau belum tersedia; Story tetap dapat diunduh"));
                });
            }
            list.addView(row);
        }
        ScrollView scroll = new ScrollView(this); scroll.addView(list);
        new AlertDialog.Builder(this).setTitle(t("Pilih Story (") + n + ")")
            .setView(scroll)
            .setNeutralButton(t("Semua"), (dialog, which) -> downloadStories(items, null))
            .setNegativeButton(t("Batal"), null)
            .setPositiveButton(t("Unduh dipilih"), (dialog, which) -> downloadStories(items, selected)).show();
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
        if (selectedStoryUrls.isEmpty()) { toast(t("Pilih setidaknya satu Story")); return; }
        if (destination.equals("gallery")) enqueueStoryBatch(null);
        else { driveAction = "download"; authorizeDrive(driveEmail == null); }
    }
    private void authorizeDrive(boolean chooseAccount) {
        if (authorizingDrive) { updateDriveIndicator(); return; }
        if (!networkOnline()) { updateDriveIndicator(); return; }
        final int generation = ++driveGeneration;
        silentDriveCheck = false;
        authorizingDrive = true;
        updateDriveIndicator();
        AuthorizationRequest.Builder builder = AuthorizationRequest.builder()
            .setRequestedScopes(Collections.singletonList(new Scope("https://www.googleapis.com/auth/drive")));
        if (chooseAccount) builder.setPrompt(AuthorizationRequest.Prompt.SELECT_ACCOUNT);
        else if (driveEmail != null) builder.setAccount(new Account(driveEmail, "com.google"));
        AuthorizationRequest request = builder.build();
        Identity.getAuthorizationClient(this).authorize(request).addOnSuccessListener(result -> {
            if (generation != driveGeneration || isDestroyed()) return;
            if (result.hasResolution()) {
                try { driveResolutionGeneration = generation; startIntentSenderForResult(result.getPendingIntent().getIntentSender(), DRIVE_AUTH, null, 0, 0, 0); }
                catch (Exception e) { driveFailed(t("Login Drive: ") + message(e.getMessage())); }
            } else authorized(result.getAccessToken());
        }).addOnFailureListener(e -> { if (generation == driveGeneration && !isDestroyed()) driveFailed("Drive: " + message(e.getMessage())); });
    }
    private void restoreDriveSession() {
        if (driveEmail == null || authorizingDrive || !networkOnline()) { updateDriveIndicator(); return; }
        final int generation = ++driveGeneration;
        silentDriveCheck = true; driveAction = "restore";
        authorizingDrive = true; updateDriveIndicator();
        AuthorizationRequest request = AuthorizationRequest.builder()
            .setRequestedScopes(Collections.singletonList(new Scope("https://www.googleapis.com/auth/drive")))
            .setAccount(new Account(driveEmail, "com.google")).build();
        Identity.getAuthorizationClient(this).authorize(request).addOnSuccessListener(result -> {
            if (generation != driveGeneration || isDestroyed()) return;
            if (result.hasResolution() || result.getAccessToken() == null) {
                authorizingDrive = false; driveConnected = false; updateDriveIndicator();
            } else authorized(result.getAccessToken());
        }).addOnFailureListener(e -> {
            if (generation != driveGeneration || isDestroyed()) return;
            authorizingDrive = false; driveConnected = false; updateDriveIndicator();
        });
    }
    private boolean networkOnline() {
        android.net.ConnectivityManager manager = getSystemService(android.net.ConnectivityManager.class);
        android.net.NetworkCapabilities caps = manager.getNetworkCapabilities(manager.getActiveNetwork());
        return caps != null && caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED);
    }
    private void watchNetwork() {
        connectivity = getSystemService(android.net.ConnectivityManager.class);
        networkCallback = new android.net.ConnectivityManager.NetworkCallback() {
            @Override public void onCapabilitiesChanged(android.net.Network network, android.net.NetworkCapabilities caps) {
                runOnUiThread(() -> {
                    if (isDestroyed()) return;
                    if (!networkOnline()) driveConnected = false;
                    updateDriveIndicator();
                    if (networkOnline() && driveEmail != null && !driveConnected && !authorizingDrive) restoreDriveSession();
                });
            }
            @Override public void onLost(android.net.Network network) {
                runOnUiThread(() -> { if (!isDestroyed()) {
                    // Read the new default: losing an old Wi-Fi network may be a mobile handover.
                    if (!networkOnline()) driveConnected = false;
                    updateDriveIndicator();
                } });
            }
        };
        connectivity.registerDefaultNetworkCallback(networkCallback);
    }
    private void updateDriveIndicator() {
        if (driveIndicator == null) return;
        boolean online = networkOnline();
        String state = !online ? t("Offline") : authorizingDrive ? t("Menghubungkan…") :
            driveConnected ? t("Terhubung") : t("Terputus");
        int color = !online ? Color.rgb(157, 49, 62) : authorizingDrive ? Color.rgb(151, 106, 0) :
            driveConnected ? Color.rgb(22, 119, 83) : Color.rgb(157, 49, 62);
        int background = !online ? 0xFFFCECEF : authorizingDrive ? 0xFFFFF4D1 :
            driveConnected ? 0xFFE7F5EE : 0xFFFCECEF;
        driveIndicator.setText("●  Drive · " + state); driveIndicator.setTextColor(color);
        GradientDrawable shape = new GradientDrawable(); shape.setColor(background); shape.setCornerRadius(dp(18));
        driveIndicator.setBackground(shape);
        driveIndicator.setContentDescription("Google Drive · " + state);
        if (driveStatus != null) driveStatus.setText("Google Drive · " + state + (driveEmail == null ? "" : "\n" + driveEmail));
        if (disconnectDrive != null) disconnectDrive.setEnabled(driveEmail != null || authorizingDrive);
    }
    private void disconnectDriveAccount() {
        driveGeneration++;
        driveEmail = null; driveConnected = false; authorizingDrive = false; driveAction = "connect";
        pendingRetries.clear(); selectedStoryUrls.clear();
        getSharedPreferences("drive_account", MODE_PRIVATE).edit().remove("email").putBoolean("disconnected", true).commit();
        updateDriveIndicator();
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
                        if (input == null) throw new IllegalArgumentException(t("Berkas torrent tidak dapat dibaca"));
                        byte[] buffer = new byte[32768]; int bytes, total = 0;
                        while ((bytes = input.read(buffer)) != -1) {
                            total += bytes;
                            if (total > 8 * 1024 * 1024) throw new IllegalArgumentException(t("Berkas torrent lebih dari 8 MB"));
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
                } catch (Exception e) { runOnUiThread(() -> toast("Torrent: " + message(e.getMessage()))); }
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
            url.setHint(selectedFiles.size() + t(" file dipilih · pilih tujuan lalu mulai"));
            updatePendingFileNotice();
            openTab(0);
            return;
        }
        if (request == MUX_FILES && result == RESULT_OK && data != null) {
            selectedFiles.clear(); muxSelection = true;
            if (data.getClipData() != null) for (int i = 0; i < data.getClipData().getItemCount(); i++)
                selectedFiles.add(data.getClipData().getItemAt(i).getUri());
            else if (data.getData() != null) selectedFiles.add(data.getData());
            if (selectedFiles.size() < 2) { selectedFiles.clear(); muxSelection = false; updatePendingFileNotice();
                toast(t("Pilih minimal dua file: media dan audio")); return; }
            updatePendingFileNotice();
            toast(t("Media dipilih; menyiapkan FFmpeg…"));
            copyLocal();
            return;
        }
        if ((request == EXPORT_BACKUP || request == IMPORT_BACKUP) && result == RESULT_OK && data != null && data.getData() != null) {
            Uri selected = data.getData();
            if (request == EXPORT_BACKUP) new Thread(() -> {
                try (java.io.OutputStream output = getContentResolver().openOutputStream(selected, "w")) {
                    if (output == null) throw new IllegalStateException(t("Tidak bisa menulis file cadangan"));
                    output.write(AppBackup.exportData(this));
                    runOnUiThread(() -> toast(t("Cadangan tersimpan")));
                } catch (Exception e) { runOnUiThread(() -> toast(t("Ekspor: ") + message(e.getMessage()))); }
            }).start();
            else new AlertDialog.Builder(this).setMessage(t("Ganti riwayat unduhan dengan isi cadangan ini?"))
                .setNegativeButton(t("Batal"), null).setPositiveButton(t("Pulihkan"), (d, w) -> new Thread(() -> {
                    try (InputStream input = getContentResolver().openInputStream(selected)) {
                        if (input == null) throw new IllegalStateException(t("Tidak bisa membaca cadangan"));
                        AppBackup.restoreData(this, input);
                        runOnUiThread(() -> { showHistory(); renderSavedSites(); toast(t("Cadangan dipulihkan")); });
                    } catch (Exception e) { runOnUiThread(() -> toast(t("Pulihkan: ") + message(e.getMessage()))); }
                }).start()).show();
            return;
        }
        if (request == IMPORT_COOKIES && result == RESULT_OK && data != null && data.getData() != null && cookieHost != null) {
            String host = cookieHost;
            new Thread(() -> {
                try (InputStream input = getContentResolver().openInputStream(data.getData())) {
                    if (input == null) throw new IllegalStateException(t("File cookies tidak dapat dibaca"));
                    ArrayList<String> parsed = CookieImporter.parse(input, host);
                    runOnUiThread(() -> CookieImporter.install(this, host, parsed, () -> {
                        updateSiteStatus(); toast(parsed.size() + t(" cookie tersimpan untuk ") + host);
                    }));
                } catch (Exception e) { runOnUiThread(() -> toast("Cookies: " + message(e.getMessage()))); }
            }).start();
            cookieHost = null;
            return;
        }
        if (request == DRIVE_AUTH) {
            if (!authorizingDrive || driveResolutionGeneration != driveGeneration) return;
            driveResolutionGeneration = -1;
            // The authorization result itself is authoritative: some Google flows do not
            // return RESULT_OK even when an Intent with a valid token is provided.
            if (data != null) {
                try {
                    AuthorizationResult auth = Identity.getAuthorizationClient(this).getAuthorizationResultFromIntent(data);
                    if (auth.getAccessToken() != null && !auth.getAccessToken().isEmpty()) {
                        authorized(auth.getAccessToken()); return;
                    }
                } catch (ApiException e) {
                    driveFailed(t("Otorisasi Drive gagal (kode ") + e.getStatusCode() + t("). Periksa OAuth Android dan akun penguji."));
                    return;
                } catch (Exception e) {
                    driveFailed("Drive: " + message(e.getMessage())); return;
                }
            }
            driveFailed(t("Otorisasi Drive tidak selesai setelah memilih akun (hasil ") + result +
                t("). Periksa OAuth Android, SHA-1 sertifikat rilis, dan akun penguji."));
        }
    }
    private void authorized(String token) {
        if (token == null || token.isEmpty()) { driveFailed(t("Drive tidak memberikan akses. Periksa izin akun lalu coba lagi.")); return; }
        String action = driveAction;
        final int generation = driveGeneration;
        driveIo.execute(() -> {
            try {
                String email = DriveFiles.accountLabel(token);
                runOnUiThread(() -> {
                    if (generation != driveGeneration || isDestroyed()) return;
                    authorizingDrive = false;
                    driveEmail = email; driveConnected = true;
                    getSharedPreferences("drive_account", MODE_PRIVATE).edit().putString("email", email).putBoolean("disconnected", false).apply();
                    updateDriveIndicator();
                    if ("retry".equals(action)) { enqueueRetries(token); return; }
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
            } catch (Exception e) { runOnUiThread(() -> {
                if (generation == driveGeneration && !isDestroyed()) driveFailed("Drive: " + message(e.getMessage()));
            }); }
        });
    }
    private void driveFailed(String message) {
        authorizingDrive = false; driveConnected = false;
        updateDriveIndicator();
        if (!silentDriveCheck) new AlertDialog.Builder(this).setTitle(t("Google Drive"))
            .setMessage(message).setPositiveButton(t("Tutup"), null).show();
    }
    private boolean validDates() {
        String from = dateFrom.getText().toString().trim(), to = dateTo.getText().toString().trim();
        try {
            if (!from.isEmpty()) LocalDate.parse(from);
            if (!to.isEmpty()) LocalDate.parse(to);
            if (!from.isEmpty() && !to.isEmpty() && LocalDate.parse(from).isAfter(LocalDate.parse(to)))
                throw new IllegalArgumentException(t("Tanggal awal melewati tanggal akhir"));
            return true;
        } catch (Exception e) { toast(t("Periksa tanggal (YYYY-MM-DD): ") + message(e.getMessage())); return false; }
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
                if (extension.isEmpty()) throw new IllegalArgumentException(t("Pilih file .json atau .zip"));
                File local = new File(getCacheDir(), "import-" + System.currentTimeMillis() + extension);
                try (InputStream in = getContentResolver().openInputStream(selected);
                     FileOutputStream out = new FileOutputStream(local)) {
                    if (in == null) throw new IllegalArgumentException(t("File import tidak bisa dibaca"));
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
            } catch (Exception e) { runOnUiThread(() -> toast(t("Import: ") + message(e.getMessage()))); }
        }).start();
    }
    private void copyLocal() {
        ArrayList<Uri> items = new ArrayList<>(selectedFiles);
        final boolean mergeRequest = muxSelection;
        new Thread(() -> {
            try {
                ArrayList<String> staged = new ArrayList<>();
                for (Uri uri : items) {
                    String name = "";
                    try (Cursor cursor = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
                        if (cursor != null && cursor.moveToFirst()) name = cursor.getString(0);
                    }
                    name = name == null ? "" : name.replaceAll("[^a-zA-Z0-9._-]", "_");
                    if (name.trim().isEmpty()) name = "file_" + staged.size();
                    File local = new File(getCacheDir(), "selected_" + System.nanoTime() + "_" + name);
                    try (InputStream in = getContentResolver().openInputStream(uri);
                         FileOutputStream out = new FileOutputStream(local)) {
                        if (in == null) throw new IllegalArgumentException(t("File tidak bisa dibaca"));
                        byte[] buffer = new byte[262144]; int n;
                        while ((n = in.read(buffer)) >= 0) out.write(buffer, 0, n);
                    }
                    staged.add(local.getAbsolutePath());
                }
                runOnUiThread(() -> {
                    pendingLocalPaths.clear();
                    pendingMuxImages.clear(); pendingMuxVideoPath = null; pendingMuxAudioPath = null;
                    if (mergeRequest) {
                        ArrayList<String> videos = new ArrayList<>(), audio = new ArrayList<>(), images = new ArrayList<>();
                        for (String path : staged) {
                            if (isVideoFile(path)) videos.add(path);
                            else if (isAudioFile(path)) audio.add(path);
                            else if (isImageFile(path)) images.add(path);
                        }
                        images.sort(String.CASE_INSENSITIVE_ORDER);
                        boolean videoAudio = videos.size() == 1 && audio.size() == 1 && images.isEmpty();
                        boolean imageAudio = videos.isEmpty() && audio.size() == 1 && !images.isEmpty();
                        if (!videoAudio && !imageAudio) {
                            for (String path : staged) new File(path).delete();
                            selectedFiles.clear(); muxSelection = false; updatePendingFileNotice();
                            toast(t("Pilih 1 video + 1 audio, atau satu/lebih gambar + 1 audio")); return;
                        }
                        pendingMuxAudioPath = audio.get(0);
                        if (videoAudio) pendingMuxVideoPath = videos.get(0);
                        else pendingMuxImages.addAll(images);
                        muxSelection = true;
                    } else {
                        pendingLocalPaths.addAll(staged);
                        muxSelection = false;
                    }
                    pendingImportPath = null; pendingTorrentPath = null; pendingUrls.clear(); selectedFiles.clear(); driveAction = "download";
                    updatePendingFileNotice();
                    if (destination.equals("gallery")) enqueue(null); else authorizeDrive(driveEmail == null);
                });
            } catch (Exception e) { runOnUiThread(() -> toast(t("File: ") + message(e.getMessage()))); }
        }).start();
    }
    private boolean isVideoFile(String path) {
        return path != null && path.toLowerCase(java.util.Locale.ROOT).matches(".*\\.(mp4|m4v|mov|webm|mkv)$");
    }
    private boolean isAudioFile(String path) {
        return path != null && path.toLowerCase(java.util.Locale.ROOT).matches(".*\\.(m4a|aac|mp3|wav|ogg|opus|flac)$");
    }
    private boolean isImageFile(String path) {
        return path != null && path.toLowerCase(java.util.Locale.ROOT).matches(".*\\.(jpg|jpeg|png|webp|bmp|gif|heic|heif|avif)$");
    }
    private void enqueue(String token) {
        int count = 1;
        String countText = limit.getText().toString().trim();
        if (countText.isEmpty()) count = 0;
        else try { count = Integer.parseInt(countText); if (count < 1) throw new NumberFormatException(); }
        catch (NumberFormatException bad) { toast(t("Maksimum item harus angka positif atau kosong untuk tanpa batas")); return; }
        saveSettings();
        try {
            int jobs = muxSelection ? 1 : pendingLocalPaths.isEmpty() ?
                (pendingImportPath == null && pendingTorrentPath == null ? pendingUrls.size() : 1) : pendingLocalPaths.size();
            int submitted = 0;
            for (int i = 0; i < jobs; i++) {
                String link = muxSelection ? "" :
                    (pendingLocalPaths.isEmpty() && pendingImportPath == null && pendingTorrentPath == null ? pendingUrls.get(i) : "");
                if (!link.isEmpty() && skipCompleted.isChecked() && History.alreadyCompleted(this, link)) continue;
                Intent job = new Intent(this, DownloadService.class).putExtra("url", link)
                    .putExtra("auto_retry", autoRetryFor(link))
                    .putExtra("selected_story", isExactStory(link))
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
                    .putExtra("tiktok_photo_mode", tiktokPhotoMode)
                    .putExtra("tiktok_watermark", tiktokWatermark)
                    .putExtra("local_path", muxSelection ? pendingMuxVideoPath :
                        (pendingLocalPaths.isEmpty() ? null : pendingLocalPaths.get(i)));
                if (muxSelection) {
                    job.putExtra("mux_audio_path", pendingMuxAudioPath);
                    if (!pendingMuxImages.isEmpty()) job.putStringArrayListExtra("mux_image_paths", new ArrayList<>(pendingMuxImages));
                }
                startForegroundService(job);
                submitted++;
            }
            toast(submitted + t(" tugas masuk antrean") + (submitted < jobs ? "; " + (jobs - submitted) + t(" sudah pernah selesai") : ""));
            pendingImportPath = null;
            pendingTorrentPath = null;
            pendingLocalPaths.clear();
            pendingMuxImages.clear(); pendingMuxVideoPath = null; pendingMuxAudioPath = null;
            muxSelection = false;
        }
        catch (Exception e) { toast(t("Gagal memulai unduhan: ") + message(e.getMessage())); }
    }
    private void enqueueStoryBatch(String token) {
        if (selectedStoryUrls.isEmpty()) return;
        saveSettings();
        ArrayList<String> urls = new ArrayList<>(selectedStoryUrls);
        ArrayList<String> manual = new ArrayList<>();
        for (String story : urls) if (!autoRetryFor(story)) manual.add(story);
        try {
            Intent job = new Intent(this, DownloadService.class)
                .putStringArrayListExtra("story_urls", urls)
                .putStringArrayListExtra("manual_story_urls", manual).putExtra("auto_retry", true)
                .putExtra("target", destination).putExtra("quality", quality).putExtra("count", 1)
                .putExtra("subtitles", subtitles.isChecked()).putExtra("thumbnail", thumbnail.isChecked())
                .putExtra("metadata", metadata.isChecked()).putExtra("anonymous", anonymous.isChecked())
                .putExtra("skip_drive", skipDrive.isChecked()).putExtra("verify_drive", verifyDrive.isChecked())
                .putExtra("album_mode", albumMode.isChecked()).putExtra("profile_content", profileContent)
                .putExtra("folder_id", folder.getText().toString().trim()).putExtra("drive_token", token)
                .putExtra("date_from", dateFrom.getText().toString().trim())
                .putExtra("date_to", dateTo.getText().toString().trim())
                .putExtra("tiktok_photo_mode", tiktokPhotoMode)
                .putExtra("tiktok_watermark", tiktokWatermark);
            // The user chose these exact Story IDs; do not apply skip-completed to this selection.
            startForegroundService(job);
            selectedStoryUrls.clear();
            toast(urls.size() + t(" Story masuk antrean"));
        } catch (Exception e) {
            ErrorLog.record(this, t("Antrean Story"), e);
            toast(t("Gagal memasukkan Story ke antrean. Lihat log error di Aktivitas."));
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
            Button open = button(t("Buka situs · ") + host, Color.rgb(65, 87, 220));
            open.setOnClickListener(v -> startActivity(new Intent(this, LoginActivity.class)
                .putExtra("site", "custom").putExtra("login_url", loginUrl)));
            open.setOnLongClickListener(v -> {
                new AlertDialog.Builder(this).setMessage(t("Hapus tombol ") + host + t(" dari daftar?"))
                    .setNegativeButton(t("Batal"), null)
                    .setPositiveButton(t("Hapus"), (dialog, which) -> {
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
        siteStatus.setText(t("Sesi tersimpan · Instagram: ") + (ig != null && ig.contains("sessionid=") ? t("ada") : t("belum")) +
            " · X: " + (WebSessions.hasXSession(this) ? t("ada") : t("belum")));
    }
    private boolean autoRetryFor(String link) {
        return getSharedPreferences("media_retry", MODE_PRIVATE).getBoolean(link, automaticRetry.isChecked());
    }
    private boolean isExactStory(String link) {
        return link != null && link.matches("^https://www\\.instagram\\.com/stories/[A-Za-z0-9._]+/[0-9]+/$");
    }
    private void chooseAutomaticLinks() {
        Matcher matches = Pattern.compile("https?://[^\\s<>]+", Pattern.CASE_INSENSITIVE).matcher(url.getText().toString());
        java.util.LinkedHashSet<String> links = new java.util.LinkedHashSet<>();
        while (matches.find() && links.size() < 50) links.add(matches.group().replaceAll("[.,;]+$", ""));
        if (links.isEmpty()) { toast(t("Masukkan URL http/https yang valid")); return; }
        String[] addresses = links.toArray(new String[0]); boolean[] enabled = new boolean[addresses.length];
        for (int i = 0; i < addresses.length; i++) enabled[i] = autoRetryFor(addresses[i]);
        new AlertDialog.Builder(this).setTitle(t("Pilih media untuk retry otomatis"))
            .setMultiChoiceItems(addresses, enabled, (dialog, which, value) -> enabled[which] = value)
            .setNegativeButton(t("Batal"), null).setPositiveButton(t("Simpan"), (dialog, which) -> {
                var edit = getSharedPreferences("media_retry", MODE_PRIVATE).edit();
                for (int i = 0; i < addresses.length; i++) edit.putBoolean(addresses[i], enabled[i]);
                edit.apply();
            }).show();
    }
    private Intent retryIntent(JSONObject previous, boolean automatic) {
        Intent intent = new Intent(this, DownloadService.class)
            .putExtra("retry_id", previous.optLong("id"))
            .putExtra("url", previous.optString("url"))
            .putExtra("auto_retry", automatic)
            .putExtra("selected_story", previous.optBoolean("selected_story") || isExactStory(previous.optString("url")))
            .putExtra("target", previous.optString("target", "gallery"))
            .putExtra("quality", previous.optString("quality", "best"))
            .putExtra("count", previous.optInt("count", 1))
            .putExtra("folder_id", previous.optString("folder"));
        for (String key : new String[]{"subtitles", "thumbnail", "metadata", "anonymous", "skip_drive", "album_mode", "verify_drive"})
            intent.putExtra(key, previous.optBoolean(key));
        for (String key : new String[]{"profile_content", "date_from", "date_to", "tiktok_photo_mode", "tiktok_watermark"})
            intent.putExtra(key, previous.optString(key));
        return intent;
    }
    private boolean canRetry(JSONObject item) {
        if (!History.retryable(item)) return false;
        String link = item.optString("url");
        return link.startsWith("https://") || link.startsWith("http://") || link.startsWith("magnet:?") ||
            RetryFiles.read(this, item.optLong("id")) != null;
    }
    private void retryLast() {
        JSONArray history = History.read(this);
        ArrayList<JSONObject> jobs = new ArrayList<>();
        for (int i = 0; i < history.length(); i++) if (canRetry(history.optJSONObject(i))) jobs.add(history.optJSONObject(i));
        if (jobs.isEmpty()) { toast(t("Tidak ada job yang bisa diulang")); return; }
        LinearLayout list = new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(16), dp(8), dp(16), dp(8));
        list.addView(label(t("Pilih media yang ingin diulang. Aktifkan otomatis untuk mencoba ulang bila koneksi kembali terputus."), 14, Color.DKGRAY, false));
        ArrayList<CheckBox> selections = new ArrayList<>(), policies = new ArrayList<>();
        for (JSONObject item : jobs) {
            CheckBox selected = new CheckBox(this); selected.setText(item.optString("url"));
            list.addView(selected); selections.add(selected);
            CheckBox automatic = new CheckBox(this); automatic.setText(t("Retry otomatis"));
            automatic.setChecked(item.optBoolean("auto_retry", true)); list.addView(automatic); policies.add(automatic);
        }
        ScrollView scroll = new ScrollView(this); scroll.addView(list);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(t("Retry media terpilih"))
            .setView(scroll).setNegativeButton(t("Batal"), null).setPositiveButton(t("Ulangi dipilih"), null).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            pendingRetries.clear();
            for (int i = 0; i < jobs.size(); i++) if (selections.get(i).isChecked())
                pendingRetries.add(retryIntent(jobs.get(i), policies.get(i).isChecked()));
            if (pendingRetries.isEmpty()) { toast(t("Pilih setidaknya satu media")); return; }
            dialog.dismiss(); authorizeRetries();
        })); dialog.show();
    }
    private void retryMedia(JSONObject item) {
        JSONArray files = RetryFiles.read(this, item.optLong("id"));
        ArrayList<Integer> indices = new ArrayList<>(); ArrayList<String> names = new ArrayList<>();
        if (files != null) for (int i = 0; i < files.length(); i++) {
            JSONObject file = files.optJSONObject(i);
            if (file != null && !RetryFiles.complete(file, item.optString("target"))) {
                indices.add(i); names.add(file.optString("name", file.optString("path")));
            }
        }
        if (names.isEmpty()) { indices.add(-1); names.add(item.optString("url")); }
        LinearLayout list = new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(16), dp(8), dp(16), dp(8));
        list.addView(label(t("Pilih media yang ingin diulang. Aktifkan otomatis untuk mencoba ulang bila koneksi kembali terputus."), 14, Color.DKGRAY, false));
        ArrayList<CheckBox> selected = new ArrayList<>(), automatic = new ArrayList<>();
        for (int row = 0; row < names.size(); row++) {
            String name = names.get(row);
            CheckBox choose = new CheckBox(this); choose.setText(name); choose.setChecked(true); list.addView(choose); selected.add(choose);
            CheckBox auto = new CheckBox(this); auto.setText(t("Retry otomatis")); auto.setChecked(files != null && indices.get(row) >= 0 ?
                files.optJSONObject(indices.get(row)).optBoolean("auto_retry", item.optBoolean("auto_retry", true)) : item.optBoolean("auto_retry", true));
            list.addView(auto); automatic.add(auto);
        }
        ScrollView scroll = new ScrollView(this); scroll.addView(list);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(t("Retry media terpilih")).setView(scroll)
            .setNegativeButton(t("Batal"), null).setPositiveButton(t("Ulangi dipilih"), null).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            ArrayList<Integer> chosen = new ArrayList<>(), manual = new ArrayList<>();
            for (int i = 0; i < indices.size(); i++) if (selected.get(i).isChecked()) {
                chosen.add(indices.get(i)); if (!automatic.get(i).isChecked()) manual.add(indices.get(i));
            }
            if (chosen.isEmpty()) { toast(t("Pilih setidaknya satu media")); return; }
            Intent retry = retryIntent(item, !manual.contains(-1));
            if (!chosen.contains(-1)) retry.putExtra("retry_files", chosen.stream().mapToInt(Integer::intValue).toArray())
                .putExtra("manual_files", manual.stream().mapToInt(Integer::intValue).toArray());
            pendingRetries.clear(); pendingRetries.add(retry); dialog.dismiss(); authorizeRetries();
        })); dialog.show();
    }
    private void authorizeRetries() {
        boolean drive = false;
        for (Intent intent : pendingRetries) if (!"gallery".equals(intent.getStringExtra("target"))) drive = true;
        if (drive) { driveAction = "retry"; authorizeDrive(driveEmail == null); }
        else enqueueRetries(null);
    }
    private void enqueueRetries(String token) {
        for (Intent intent : pendingRetries) { intent.putExtra("drive_token", token); startForegroundService(intent); }
        pendingRetries.clear(); openTab(4);
    }
    private void showHistory() {
        if (historyList == null || currentTab != 4) return;
        JSONArray jobs = History.read(this);
        historyList.removeAllViews();
        if (jobs.length() == 0) {
            historyList.addView(label(t("Belum ada unduhan. Tugas yang kamu mulai akan muncul di sini."),
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
            String status = "COMPLETED".equals(state) ? t("SELESAI") :
                "FAILED".equals(state) ? t("GAGAL") : "RUNNING".equals(state) ? t("BERJALAN") :
                "CANCELLED".equals(state) ? t("DIBATALKAN") : "INTERRUPTED".equals(state) ? t("TERHENTI") :
                "UPLOADING".equals(state) ? t("UNGGAH DRIVE") : "SAVING".equals(state) ? t("MENYIMPAN") : t("ANTREAN");
            LinearLayout card = panel(historyList);
            card.addView(label(status + (item.optInt("progress", 0) > 0 && !"COMPLETED".equals(state) ?
                "  ·  " + item.optInt("progress") + "%" : ""), 12, color, true));
            String[] lines = message.split("\\n");
            String headline = lines.length == 0 ? t("Tugas unduhan") : lines[0].trim();
            if (headline.startsWith("Lokal:")) {
                headline = headline.substring("Lokal:".length()).trim();
                headline = headline.substring(headline.lastIndexOf('/') + 1);
            } else if (headline.startsWith("Drive:")) headline = t("File tersimpan ke Drive");
            TextView summary = label(headline.isEmpty() ? t("Tugas unduhan") : (lines.length > 0 && lines[0].startsWith("Lokal:") ? headline : message(headline)), 15, Color.rgb(30, 39, 65), true);
            summary.setMaxLines(2); summary.setEllipsize(android.text.TextUtils.TruncateAt.END);
            card.addView(summary);
            int local = 0, drive = 0;
            for (String line : lines) {
                if (line.startsWith("Lokal:")) local++;
                if (line.startsWith("Drive:")) drive++;
            }
            String count = local > 0 || drive > 0 ?
                (local > 0 ? local + t(" file lokal") : "") + (local > 0 && drive > 0 ? "  ·  " : "") +
                    (drive > 0 ? drive + t(" file Drive") : "") : t("Ketuk untuk membaca detail");
            card.addView(label(count, 12, Color.rgb(101, 111, 137), false));
            card.setOnClickListener(v -> showJobDetail(status, message, item));
        }
    }
    private void showJobDetail(String status, String message, JSONObject job) {
        TextView detail = label(message(message), 14, Color.rgb(30, 39, 65), false);
        detail.setPadding(dp(18), dp(12), dp(18), dp(16));
        detail.setTextIsSelectable(true);
        Linkify.addLinks(detail, Linkify.WEB_URLS);
        detail.setMovementMethod(LinkMovementMethod.getInstance());
        ScrollView scroll = new ScrollView(this); scroll.addView(detail);
        AlertDialog.Builder dialog = new AlertDialog.Builder(this).setTitle(status).setView(scroll).setPositiveButton(t("Tutup"), null);
        if (canRetry(job)) dialog.setNeutralButton(t("Retry media terpilih"), (d, which) -> retryMedia(job));
        dialog.show();
    }
    private void showErrorLog() {
        String report = ErrorLog.read(this);
        TextView detail = label(message(report), 14, Color.rgb(30, 39, 65), false);
        detail.setPadding(dp(18), dp(12), dp(18), dp(16));
        detail.setTextIsSelectable(true);
        ScrollView scroll = new ScrollView(this); scroll.addView(detail);
        new AlertDialog.Builder(this).setTitle(t("Log error"))
            .setView(scroll)
            .setNeutralButton(t("Bagikan log"), (dialog, which) -> {
                Intent share = new Intent(Intent.ACTION_SEND).setType("text/plain")
                    .putExtra(Intent.EXTRA_TEXT, report);
                startActivity(Intent.createChooser(share, t("Bagikan log The Great Drive")));
            })
            .setPositiveButton(t("Tutup"), null).show();
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
    private void rowWithIcons(LinearLayout parent, String[] names, String[] values, int[] icons, Select select) {
        LinearLayout line = new LinearLayout(this); line.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < names.length; i++) {
            final String value = values[i];
            Button b = button(names[i], Color.rgb(225, 230, 249));
            b.setTextColor(Color.rgb(40, 51, 108)); b.setTextSize(12);
            b.setCompoundDrawablesWithIntrinsicBounds(icons[i], 0, 0, 0); b.setCompoundDrawablePadding(dp(7));
            if (Build.VERSION.SDK_INT >= 23) b.setCompoundDrawableTintList(android.content.res.ColorStateList.valueOf(Color.rgb(40, 51, 108)));
            LinearLayout.LayoutParams chip = new LinearLayout.LayoutParams(0, dp(54), 1);
            chip.rightMargin = dp(4); chip.topMargin = dp(5); line.addView(b, chip);
            b.setOnClickListener(v -> select.pick(value));
        }
        parent.addView(line);
    }
    private Button button(String text, int color) {
        Button b = new Button(this); b.setText(text); b.setAllCaps(false); b.setTextColor(Color.WHITE);
        GradientDrawable shape = new GradientDrawable(); shape.setColor(color); shape.setCornerRadius(dp(16));
        b.setBackground(shape); b.setMinHeight(dp(50)); b.setPadding(dp(12), dp(10), dp(12), dp(10)); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(10); b.setLayoutParams(lp); return b;
    }
    private TextView label(String content, int size, int color, boolean bold) {
        TextView t = new TextView(this); t.setText(content); t.setTextSize(size); t.setTextColor(color);
        t.setGravity(Gravity.START); t.setPadding(0, dp(5), 0, dp(5));
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }
    private int dp(int value) { return Math.round(getResources().getDisplayMetrics().density * value); }
    private void toast(String message) { Toast.makeText(this, message(message), Toast.LENGTH_LONG).show(); }
}

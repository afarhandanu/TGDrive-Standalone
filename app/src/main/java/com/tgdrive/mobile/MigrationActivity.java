package com.tgdrive.mobile;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MigrationActivity extends LocalizedActivity {
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private final Handler handler=new Handler(Looper.getMainLooper());
    private boolean drive,scanning;
    private String token,parent;
    private int count=-1;
    private TextView summary,status;
    private Button scan,start;
    private RadioButton remove;
    private final Runnable refresh=new Runnable() {
        public void run() {
            status.setText(message(MigrationService.status(MigrationActivity.this)));
            boolean active=MigrationService.active();
            scan.setEnabled(!active && !scanning);
            start.setEnabled(!active && !scanning && count>0);
            handler.postDelayed(this,1000);
        }
    };
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        drive=getIntent().getBooleanExtra("drive",false); token=getIntent().getStringExtra("token");
        parent=getIntent().getStringExtra("parent");
        if(parent==null || parent.trim().isEmpty()) parent="root";
        LinearLayout shell=new LinearLayout(this); shell.setOrientation(1);
        shell.setBackgroundColor(Color.rgb(246,248,253));
        shell.setOnApplyWindowInsetsListener((view,insets) -> {
            if(android.os.Build.VERSION.SDK_INT>=30) {
                android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars());
                shell.setPadding(0,bars.top,0,bars.bottom);
            } else shell.setPadding(0,insets.getSystemWindowInsetTop(),0,insets.getSystemWindowInsetBottom());
            return insets;
        });
        ScrollView scroll=new ScrollView(this);
        LinearLayout page=new LinearLayout(this); page.setOrientation(1); page.setPadding(dp(20),dp(12),dp(20),dp(24));
        scroll.addView(page); shell.addView(scroll); setContentView(shell);
        Button back=button(t("← Kembali")); back.setOnClickListener(v -> finish()); page.addView(back);
        page.addView(text(t("Pindahkan folder ")+(drive?"Drive":t("Lokal")),24));
        page.addView(text("TGDrive → The Great Drive",18));
        page.addView(text(drive ? t("Folder TGDrive di ")+("root".equals(parent)?"My Drive":t("folder induk ")+parent)+
            t(" akan disalin ke The Great Drive pada induk yang sama.") :
            t("Periksa folder TGDrive milik aplikasi ini di Movies, Pictures, Music, dan Download. Struktur site/username/jenis tetap dipertahankan."),14));
        RadioGroup modes=new RadioGroup(this);
        RadioButton keep=new RadioButton(this); keep.setId(View.generateViewId()); keep.setText(t("Simpan file lama (salin)"));
        remove=new RadioButton(this); remove.setId(View.generateViewId()); remove.setText(t("Hapus file lama setelah verifikasi"));
        modes.addView(keep); modes.addView(remove); modes.check(keep.getId()); page.addView(modes);
        page.addView(text(drive ? t("Mode hapus memindahkan file sumber ke Sampah Drive setelah ukuran dan checksum cocok.") :
            t("Mode hapus menghapus file sumber dari ponsel setelah ukuran dan SHA-256 salinan cocok."),13));
        page.addView(text(t("File dengan isi berbeda tidak ditimpa. Salinan yang sama dapat dipakai kembali. Folder lama yang kosong tetap ada."),13));
        scan=button(t("Periksa folder lama")); scan.setOnClickListener(v -> scan()); page.addView(scan);
        summary=text(t("Periksa folder untuk melihat jumlah file sebelum memulai."),14); page.addView(summary);
        start=button(t("Mulai pemindahan")); start.setEnabled(false); start.setOnClickListener(v -> confirm()); page.addView(start);
        Button cancel=button(t("Hentikan pemindahan")); cancel.setOnClickListener(v -> {
            if(MigrationService.active()) new AlertDialog.Builder(this).setMessage(t("Hentikan setelah operasi yang sedang berjalan? File yang sudah diproses tetap tersimpan."))
                .setNegativeButton(t("Kembali"),null).setPositiveButton(t("Hentikan"),(d,w) -> MigrationService.cancel()).show();
        }); page.addView(cancel);
        page.addView(text(t("HASIL TERAKHIR"),13)); status=text("",14); status.setTextIsSelectable(true); page.addView(status);
        Button log=button(t("Lihat log error")); log.setOnClickListener(v -> {
            TextView details=text(message(ErrorLog.read(this)),13); details.setTextIsSelectable(true);
            ScrollView logScroll=new ScrollView(this); logScroll.setPadding(dp(16),0,dp(16),0); logScroll.addView(details);
            new AlertDialog.Builder(this).setTitle(t("Log error")).setView(logScroll).setPositiveButton(t("Tutup"),null).show();
        }); page.addView(log);
    }
    private void scan() {
        if(drive && (token==null || token.isEmpty())) {
            summary.setText(t("Buka Berkas → Pindahkan folder Google Drive untuk menghubungkan akun kembali.")); return;
        }
        scanning=true; count=-1; start.setEnabled(false); scan.setEnabled(false); summary.setText(t("Memeriksa folder…"));
        io.execute(() -> {
            try {
                List<FolderMigration.Entry> entries=drive?FolderMigration.scanDrive(token,parent,() -> false):FolderMigration.scanLocal(this);
                long bytes=0; for(FolderMigration.Entry entry:entries) bytes+=entry.size;
                final String label=entries.size()+t(" file · ")+android.text.format.Formatter.formatFileSize(this,bytes);
                runOnUiThread(() -> {
                    if(isDestroyed()) return;
                    count=entries.size(); summary.setText(count==0 ? t("Tidak ada file lama yang dapat diakses. ")+
                        (drive?t("Periksa ID folder induk pada Opsi."):t("Pencarian hanya mencakup file milik instalasi aplikasi ini.")) : label);
                    scanning=false; scan.setEnabled(true); start.setEnabled(count>0 && !MigrationService.active());
                });
            } catch(Exception error) {
                ErrorLog.record(this,t("Periksa folder lama"),error);
                runOnUiThread(() -> { if(!isDestroyed()) { scanning=false; scan.setEnabled(true); summary.setText(t("Gagal memeriksa: ")+message(error.getMessage())); } });
            }
        });
    }
    private void confirm() {
        boolean delete=remove.isChecked();
        new AlertDialog.Builder(this).setTitle(delete?t("Pindahkan dan hapus sumber?"):t("Salin dan simpan sumber?"))
            .setMessage(count+t(" file terdeteksi. Tujuan: The Great Drive.\n\n")+
                (delete?(drive?t("Sumber masuk Sampah Drive"):t("Sumber dihapus dari ponsel"))+t(" hanya setelah salinan terverifikasi."):t("Semua file sumber tetap disimpan."))+
                t("\n\nDaftar file diperiksa ulang saat mulai."))
            .setNegativeButton(t("Batal"),null).setPositiveButton(t("Mulai"),(d,w) -> {
                start.setEnabled(false); count=-1;
                startForegroundService(new Intent(this,MigrationService.class).putExtra("drive",drive).putExtra("token",token)
                    .putExtra("parent",parent).putExtra("remove",delete));
            }).show();
    }
    private int dp(int value) { return Math.round(value*getResources().getDisplayMetrics().density); }
    private TextView text(String value,int size) {
        TextView t=new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(Color.rgb(30,39,65));
        t.setPadding(0,dp(8),0,dp(8)); return t;
    }
    private Button button(String label) { Button b=new Button(this); b.setText(label); b.setAllCaps(false); return b; }
    @Override protected void onResume() { super.onResume(); handler.post(refresh); }
    @Override protected void onPause() { handler.removeCallbacks(refresh); super.onPause(); }
    @Override protected void onDestroy() { handler.removeCallbacks(refresh); io.shutdownNow(); super.onDestroy(); }
}

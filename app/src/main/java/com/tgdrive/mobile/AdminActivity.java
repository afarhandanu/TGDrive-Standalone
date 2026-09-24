package com.tgdrive.mobile;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Controls for this device's queue and data. */
public class AdminActivity extends LocalizedActivity {
    private static final int INK=0xff101936, MUTED=0xff657089, BLUE=0xff4157dc, RED=0xff87394e;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private TextView completed,failed,unfinished,queue,cache;
    private Button pause,cancel,clearCache,clearHistory;
    private boolean cleaning;
    private final Runnable ticker=new Runnable() {
        @Override public void run() { refreshStats(); handler.postDelayed(this,1000); }
    };
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(0xfff6f8fd);
        getWindow().setNavigationBarColor(0xfff6f8fd);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        LinearLayout shell=new LinearLayout(this); shell.setOrientation(1); shell.setBackgroundColor(0xfff6f8fd);
        shell.setOnApplyWindowInsetsListener((view,insets) -> {
            if(android.os.Build.VERSION.SDK_INT>=30) {
                android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars());
                shell.setPadding(bars.left,bars.top,bars.right,bars.bottom);
            } else shell.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());
            return insets;
        });
        ScrollView scroll=new ScrollView(this); scroll.setFillViewport(true);
        LinearLayout page=new LinearLayout(this); page.setOrientation(1); page.setPadding(dp(20),dp(12),dp(20),dp(28));
        scroll.addView(page); shell.addView(scroll); setContentView(shell); shell.requestApplyInsets();
        Button back=action(page,t("← Kembali"),0xffe4e9fc,() -> finish()); back.setTextColor(BLUE);
        page.addView(text(t("Administrasi perangkat"),27,INK,true));
        page.addView(text(t("Kelola antrean, penyimpanan sementara, dan riwayat perangkatmu."),14,MUTED,false));
        LinearLayout overview=card(page,t("RINGKASAN UNDUHAN"));
        completed=metric(overview,t("Selesai"),0xff147e68);
        failed=metric(overview,t("Gagal / batal"),RED);
        unfinished=metric(overview,t("Belum selesai"),BLUE);
        LinearLayout queueCard=card(page,t("KONTROL ANTREAN"));
        queue=text("",16,INK,true); queueCard.addView(queue);
        pause=action(queueCard,t("Jeda antrean"),BLUE,() -> {
            startService(new Intent(this,DownloadService.class).setAction(DownloadService.isPaused()?"RESUME":"PAUSE"));
        });
        cancel=action(queueCard,t("Batalkan tugas aktif"),RED,() -> confirm(t("Batalkan tugas yang sedang berjalan?"),() ->
            startService(new Intent(this,DownloadService.class).setAction("CANCEL"))));
        LinearLayout accounts=card(page,t("AKUN & SITUS"));
        accounts.addView(text("Google Drive",13,MUTED,true));
        String email=getSharedPreferences("drive_account",MODE_PRIVATE).getString("email",null);
        accounts.addView(text(email==null?t("Belum tersambung"):email,16,INK,false));
        accounts.addView(text(t("Situs tersimpan")+" · "+SavedSites.list(this).length(),14,MUTED,false));
        LinearLayout storage=card(page,t("PENYIMPANAN"));
        storage.addView(text(t("Cache sementara"),15,INK,true));
        cache=text(t("Menghitung…"),22,BLUE,true); storage.addView(cache);
        storage.addView(text(t("Berkas unduhan dan sesi login tetap tersimpan."),13,MUTED,false));
        clearCache=action(storage,t("Bersihkan cache tugas lama"),INK,() -> confirm(t("Hapus berkas sementara dari job yang tidak berjalan?"),() -> {
            if(DownloadService.hasPendingJobs()) { toast(t("Tunggu sampai antrean kosong")); return; }
            cleaning=true; refreshStats();
            io.execute(() -> {
                File[] entries=getCacheDir().listFiles();
                if(entries!=null) for(File file:entries) if(!DownloadService.hasPendingJobs() && file.getName().startsWith("job-")) deleteTree(file);
                runOnUiThread(() -> { if(isDestroyed()) return; cleaning=false; refreshStats(); measureCache(); toast(t("Cache job dibersihkan")); });
            });
        }));
        LinearLayout history=card(page,t("RIWAYAT TERBARU"));
        history.addView(text(t("Hanya catatan riwayat yang dihapus. Berkas unduhan tetap tersimpan."),13,MUTED,false));
        clearHistory=action(history,t("Hapus riwayat unduhan"),RED,() -> confirm(t("Hapus semua catatan riwayat di perangkat ini?"),() -> {
            if(DownloadService.hasPendingJobs()) { toast(t("Tunggu sampai antrean kosong")); return; }
            getSharedPreferences("jobs",MODE_PRIVATE).edit().remove("history").apply();
            refreshStats(); toast(t("Riwayat dibersihkan"));
        }));
        measureCache(); refreshStats();
    }
    private void refreshStats() {
        if(queue==null) return;
        int done=0,bad=0,pending=0;
        JSONArray jobs=History.read(this);
        for(int i=0;i<jobs.length();i++) {
            JSONObject row=jobs.optJSONObject(i); if(row==null) continue;
            switch(row.optString("state")) {
                case "COMPLETED": done++; break;
                case "FAILED": case "CANCELLED": case "INTERRUPTED": bad++; break;
                default: pending++;
            }
        }
        completed.setText(String.valueOf(done)); failed.setText(String.valueOf(bad)); unfinished.setText(String.valueOf(pending));
        boolean active=DownloadService.hasPendingJobs(),paused=DownloadService.isPaused();
        queue.setText((paused?t("Dijeda"):t("Berjalan"))+" · "+(active?t("Ada tugas aktif atau menunggu"):t("Kosong")));
        pause.setText(paused?t("Lanjutkan antrean"):t("Jeda antrean"));
        enabled(cancel,active); enabled(clearCache,!active&&!cleaning); enabled(clearHistory,!active);
    }
    private void measureCache() {
        io.execute(() -> {
            long bytes=diskUsage(getCacheDir());
            runOnUiThread(() -> { if(!isDestroyed()) cache.setText(android.text.format.Formatter.formatFileSize(L10n.wrap(this),bytes)); });
        });
    }
    private void enabled(Button button,boolean enabled) { button.setEnabled(enabled); button.setAlpha(enabled?1f:0.45f); }
    private LinearLayout card(LinearLayout parent,String heading) {
        LinearLayout card=new LinearLayout(this); card.setOrientation(1); card.setPadding(dp(18),dp(16),dp(18),dp(18));
        card.setBackground(shape(Color.WHITE,22)); card.setElevation(dp(2));
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2); params.topMargin=dp(18); parent.addView(card,params);
        card.addView(text(heading,12,MUTED,true)); return card;
    }
    private TextView metric(LinearLayout parent,String name,int color) {
        LinearLayout row=new LinearLayout(this); row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.addView(text(name,15,INK,false),new LinearLayout.LayoutParams(0,-2,1));
        TextView value=text("0",24,color,true); row.addView(value); parent.addView(row); return value;
    }
    private TextView text(String value,int size,int color,boolean bold) {
        TextView text=new TextView(this); text.setText(value); text.setTextSize(size); text.setTextColor(color);
        text.setPadding(0,dp(6),0,dp(6)); if(bold) text.setTypeface(Typeface.DEFAULT,Typeface.BOLD); return text;
    }
    private Button action(LinearLayout parent,String name,int color,Runnable click) {
        Button button=new Button(this); button.setText(name); button.setAllCaps(false); button.setTextSize(14); button.setTextColor(Color.WHITE);
        button.setBackground(shape(color,16)); button.setMinHeight(dp(52)); button.setPadding(dp(12),dp(10),dp(12),dp(10));
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2); params.topMargin=dp(10); parent.addView(button,params);
        button.setOnClickListener(view -> click.run()); return button;
    }
    private GradientDrawable shape(int color,int radius) { GradientDrawable shape=new GradientDrawable(); shape.setColor(color); shape.setCornerRadius(dp(radius)); return shape; }
    private int dp(int value) { return Math.round(value*getResources().getDisplayMetrics().density); }
    private void confirm(String question,Runnable action) {
        new AlertDialog.Builder(this).setMessage(question).setNegativeButton(t("Batal"),null)
            .setPositiveButton(t("Lanjutkan"),(d,w) -> action.run()).show();
    }
    private void toast(String value) { Toast.makeText(this,value,Toast.LENGTH_SHORT).show(); }
    private long diskUsage(File file) {
        if(java.nio.file.Files.isSymbolicLink(file.toPath())) return 0;
        if(file.isFile()) return file.length();
        long total=0; File[] files=file.listFiles(); if(files!=null) for(File child:files) total+=diskUsage(child); return total;
    }
    private void deleteTree(File file) {
        if(java.nio.file.Files.isSymbolicLink(file.toPath())) return;
        File[] files=file.listFiles(); if(files!=null) for(File child:files) deleteTree(child); file.delete();
    }
    @Override protected void onResume() { super.onResume(); handler.post(ticker); }
    @Override protected void onPause() { handler.removeCallbacks(ticker); super.onPause(); }
    @Override protected void onDestroy() { handler.removeCallbacks(ticker); io.shutdownNow(); super.onDestroy(); }
}

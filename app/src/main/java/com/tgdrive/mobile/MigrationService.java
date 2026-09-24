package com.tgdrive.mobile;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

/** Foreground worker, separate from the established download queue. */
public class MigrationService extends Service {
    private static final AtomicReference<MigrationService> ACTIVE=new AtomicReference<>();
    private volatile boolean canceled;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private NotificationManager notifications;
    private boolean drive;
    public static boolean active() { return ACTIVE.get()!=null; }
    public static void cancel() { MigrationService service=ACTIVE.get(); if(service!=null) service.canceled=true; }
    static String status(Context context) {
        var p=context.getSharedPreferences("folder_migration_status",MODE_PRIVATE);
        String text=p.getString("status","Belum ada pemindahan folder.");
        if(p.getBoolean("running",false) && !active())
            return "Proses sebelumnya terhenti. Periksa folder lalu jalankan lagi untuk melanjutkan.\n"+text;
        return text;
    }
    @Override public void onCreate() {
        super.onCreate(); notifications=getSystemService(NotificationManager.class);
        notifications.createNotificationChannel(new NotificationChannel("folder_migration","Pemindahan folder",NotificationManager.IMPORTANCE_LOW));
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId) {
        if(intent==null) { stopSelf(startId); return START_NOT_STICKY; }
        if(!ACTIVE.compareAndSet(null,this)) {
            if(ACTIVE.get()!=this) stopSelf(startId);
            return START_NOT_STICKY;
        }
        canceled=false; drive=intent.getBooleanExtra("drive",false);
        boolean remove=intent.getBooleanExtra("remove",false);
        String token=intent.getStringExtra("token");
        String selected=intent.getStringExtra("parent");
        String parent=selected==null || selected.trim().isEmpty()?"root":selected.trim();
        startForeground(3002,notification("Memeriksa folder lama…",true));
        worker.execute(() -> {
            int succeeded=0,failed=0,total=0;
            StringBuilder issues=new StringBuilder();
            try {
                update("Memeriksa folder "+(drive?"Drive":"Lokal")+"…",true);
                if(drive && (token==null || token.isEmpty())) throw new IllegalStateException("Hubungkan Drive kembali melalui menu Berkas");
                List<FolderMigration.Entry> entries=drive?FolderMigration.scanDrive(token,parent,() -> canceled):FolderMigration.scanLocal(this);
                total=entries.size();
                for(FolderMigration.Entry entry:entries) {
                    VerifiedMigration.check(() -> canceled);
                    update((succeeded+failed)+"/"+total+" · "+entry.label()+"\n"+
                        (remove?"Hapus sumber setelah verifikasi":"Simpan file lama"),true);
                    try {
                        if(drive) FolderMigration.drive(this,token,parent,entry,remove,() -> canceled);
                        else FolderMigration.local(this,entry,remove,() -> canceled);
                        succeeded++;
                    } catch(java.io.InterruptedIOException interrupted) { throw interrupted; }
                    catch(Exception error) {
                        failed++;
                        ErrorLog.record(this,"Pemindahan: "+entry.label(),error);
                        if(failed<=20) issues.append("\n• ").append(entry.label()).append(": ").append(error.getMessage());
                    }
                }
                update((total==0?"Tidak ditemukan file lama yang bisa diakses.":"Selesai · "+succeeded+" berhasil · "+failed+" gagal dari "+total+" file.")+
                    (remove?"\nSumber yang gagal diproses tetap disimpan.":"\nFile lama tetap disimpan.")+issues,false);
            } catch(Exception error) {
                ErrorLog.record(this,"Pemindahan folder",error);
                update((canceled?"Dibatalkan":"Terhenti")+" · "+succeeded+" berhasil · "+failed+" gagal dari "+total+" file.\n"+
                    error.getMessage()+"\nFile yang belum diproses tetap disimpan."+issues,false);
            } finally {
                stopForeground(STOP_FOREGROUND_REMOVE); stopSelf(); ACTIVE.compareAndSet(this,null);
            }
        });
        return START_NOT_STICKY;
    }
    private void update(String text,boolean running) {
        getSharedPreferences("folder_migration_status",MODE_PRIVATE).edit().putString("status",text).putBoolean("running",running).commit();
        if(running) notifications.notify(3002,notification(text,true));
    }
    private Notification notification(String text,boolean running) {
        Intent open=new Intent(this,MigrationActivity.class).putExtra("drive",drive);
        PendingIntent action=PendingIntent.getActivity(this,3002,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this,"folder_migration").setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("The Great Drive · Pindahkan folder").setContentText(text).setStyle(new Notification.BigTextStyle().bigText(text))
            .setContentIntent(action).setOngoing(running).setOnlyAlertOnce(true).build();
    }
    @Override public void onTimeout(int startId,int fgsType) {
        canceled=true;
        update("Pemindahan dihentikan Android. Jalankan lagi untuk melanjutkan; sumber yang belum diproses tetap disimpan.",false);
        stopSelf();
    }
    @Override public void onDestroy() { canceled=true; worker.shutdown(); super.onDestroy(); }
    @Override public IBinder onBind(Intent intent) { return null; }
}

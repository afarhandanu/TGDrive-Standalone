package com.tgdrive.mobile;

import android.content.Context;
import android.content.res.Configuration;
import java.util.Locale;

/** Translates display text only. Stored job states, paths and URLs remain unchanged. */
public final class L10n {
    private L10n() { }
    public static String language(Context context) {
        return "en".equals(context.getSharedPreferences("app_language", Context.MODE_PRIVATE)
            .getString("language", "id")) ? "en" : "id";
    }
    public static void setLanguage(Context context, String language) {
        context.getSharedPreferences("app_language", Context.MODE_PRIVATE).edit()
            .putString("language", "en".equals(language) ? "en" : "id").apply();
    }
    public static Context wrap(Context context) {
        Configuration configuration = new Configuration(context.getResources().getConfiguration());
        configuration.setLocale(Locale.forLanguageTag(language(context)));
        return context.createConfigurationContext(configuration);
    }
    public static String text(Context context, String source) {
        Integer id = TranslationCatalog.IDS.get(source);
        return id == null ? source : wrap(context).getString(id);
    }
    public static String message(Context context, String source) {
        if (source == null) return "";
        return DisplayMessages.translate(source, key -> text(context, key));
    }
    static android.os.Bundle notificationText(String title, String body) {
        android.os.Bundle extras = new android.os.Bundle();
        extras.putString("tgd.canonical_title", title);
        extras.putString("tgd.canonical_body", body);
        return extras;
    }
    static void refreshNotifications(Context context) {
        android.app.NotificationManager manager = context.getSystemService(android.app.NotificationManager.class);
        if (manager == null) return;
        String[][] channels = {{"downloads", "Unduhan The Great Drive"}, {"folder_migration", "Pemindahan folder"}};
        for (String[] channel : channels) {
            android.app.NotificationChannel existing = manager.getNotificationChannel(channel[0]);
            if (existing != null) { existing.setName(text(context, channel[1])); manager.createNotificationChannel(existing); }
        }
        for (android.service.notification.StatusBarNotification posted : manager.getActiveNotifications()) {
            android.app.Notification original = posted.getNotification();
            String title = original.extras.getString("tgd.canonical_title");
            String body = original.extras.getString("tgd.canonical_body");
            if (title == null || body == null) continue;
            android.app.Notification.Builder update = android.app.Notification.Builder.recoverBuilder(context, original)
                .setContentTitle(state(context, title)).setContentText(message(context, body)).setOnlyAlertOnce(true);
            if (original.extras.containsKey(android.app.Notification.EXTRA_BIG_TEXT))
                update.setStyle(new android.app.Notification.BigTextStyle().bigText(message(context, body)));
            manager.notify(posted.getTag(), posted.getId(), update.build());
        }
    }
    public static String state(Context context, String state) {
        switch (state) {
            case "COMPLETED": return text(context, "SELESAI");
            case "FAILED": return text(context, "GAGAL");
            case "CANCELLED": return text(context, "DIBATALKAN");
            case "INTERRUPTED": return text(context, "TERHENTI");
            case "RUNNING": return text(context, "BERJALAN");
            case "SAVING": return text(context, "MENYIMPAN");
            case "UPLOADING": return text(context, "UNGGAH DRIVE");
            case "QUEUED": return text(context, "ANTREAN");
            default: return text(context, state);
        }
    }
}

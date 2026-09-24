package com.tgdrive.mobile;

import java.util.HashMap;
import java.util.Map;

public final class DisplayMessagesTest {
    private static int checks;
    private static final Map<String,String> EN = new HashMap<>();
    static {
        EN.put("Lokal: {0}","Local: {0}");
        EN.put("Drive sudah ada: {0}","Already in Drive: {0}");
        EN.put("URL media untuk Story ID {0} tidak tersedia","Media URL unavailable for Story ID {0}");
        EN.put("Selesai · {0} berhasil · {1} gagal dari {2} file.","Completed · {0} successful · {1} failed out of {2} files.");
        EN.put("Dibatalkan","Cancelled");
    }
    private static void check(String source,String expected) {
        String actual=DisplayMessages.translate(source,key -> EN.getOrDefault(key,key));
        if(!expected.equals(actual)) throw new AssertionError("Expected: "+expected+"\nActual: "+actual);
        checks++;
    }
    public static void main(String[] args) {
        check("Dibatalkan","Cancelled");
        check("RuntimeError: URL media untuk Story ID 3992573950408138445 tidak tersedia",
            "RuntimeError: Media URL unavailable for Story ID 3992573950408138445");
        check("Lokal: instagram/akun/Foto SELESAI {0} {1}.jpg","Local: instagram/akun/Foto SELESAI {0} {1}.jpg");
        check("Drive sudah ada: folder/Mengunggah berkas.jpg","Already in Drive: folder/Mengunggah berkas.jpg");
        check("Selesai · 2 berhasil · 1 gagal dari 3 file.","Completed · 2 successful · 1 failed out of 3 files.");
        check("Lokal: photo.jpg\nDrive: https://drive.google.com/file/d/id/view\n", "Local: photo.jpg\nDrive: https://drive.google.com/file/d/id/view\n");
        check("folder/Dibatalkan.jpg","folder/Dibatalkan.jpg");
        check("ERROR: provider-specific diagnostic","ERROR: provider-specific diagnostic");
        check("• path/URL media untuk Story ID 42 tidak tersedia", "• path/URL media untuk Story ID 42 tidak tersedia");
        String canonical="Lokal: sumber {0}.jpg";
        String indonesian=DisplayMessages.translate(canonical,key -> key);
        if(!canonical.equals(indonesian)) throw new AssertionError("Canonical path changed"); checks++;
        System.out.println("PASS: "+checks+" message localization and filename preservation checks");
    }
}

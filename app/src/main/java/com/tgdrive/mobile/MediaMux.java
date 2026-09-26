package com.tgdrive.mobile;

import com.arthenica.ffmpegkit.FFmpegKit;
import com.arthenica.ffmpegkit.ReturnCode;
import android.media.MediaMetadataRetriever;
import android.os.Build;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;

/** Local FFmpeg helpers for muxing video/audio and rendering image slideshows. */
public final class MediaMux {
    private MediaMux() { }

    private static void requireArm64() {
        boolean arm64 = false;
        for (String abi : Build.SUPPORTED_ABIS) if ("arm64-v8a".equals(abi)) arm64 = true;
        if (!arm64) throw new IllegalStateException("FFmpeg tersedia untuk perangkat Android arm64; unduhan lain tetap dapat digunakan");
    }

    public static void merge(File video, File audio) throws Exception {
        requireArm64();
        if (!video.isFile() || !audio.isFile() || video.length() == 0 || audio.length() == 0)
            throw new IllegalArgumentException("Video atau audio untuk digabung tidak tersedia");
        File output = new File(video.getParentFile(), video.getName() + ".muxed.mp4");
        try {
            var session = FFmpegKit.executeWithArguments(new String[]{"-y", "-i", video.getAbsolutePath(),
                "-i", audio.getAbsolutePath(), "-map", "0:v:0", "-map", "1:a:0",
                "-c:v", "copy", "-c:a", "aac", "-shortest", "-movflags", "+faststart",
                output.getAbsolutePath()});
            if (!ReturnCode.isSuccess(session.getReturnCode()) || !output.isFile() || output.length() == 0)
                throw new IllegalStateException("FFmpeg gagal menggabungkan audio dan video: " + session.getFailStackTrace());
            Files.move(output.toPath(), video.toPath(), StandardCopyOption.REPLACE_EXISTING);
            audio.delete();
        } finally { output.delete(); }
    }

    /** Render one or more still images over a single audio track into a vertical MP4. */
    public static void slideshow(List<File> images, File audio, File output) throws Exception {
        requireArm64();
        if (images == null || images.isEmpty()) throw new IllegalArgumentException("Gambar untuk slideshow tidak tersedia");
        if (!audio.isFile() || audio.length() == 0) throw new IllegalArgumentException("Audio untuk slideshow tidak tersedia");
        for (File image : images)
            if (image == null || !image.isFile() || image.length() == 0)
                throw new IllegalArgumentException("Salah satu gambar slideshow tidak dapat dibaca");
        File parent = output.getParentFile();
        if (parent == null || (!parent.mkdirs() && !parent.isDirectory()))
            throw new IllegalStateException("Folder keluaran slideshow tidak tersedia");

        double totalSeconds = audioDurationSeconds(audio);
        if (totalSeconds <= 0) totalSeconds = Math.max(3.0, images.size() * 3.0);
        double perImage = Math.max(0.25, totalSeconds / images.size());
        File concat = new File(parent, ".slideshow-" + System.nanoTime() + ".txt");
        StringBuilder list = new StringBuilder();
        for (File image : images) {
            list.append("file '").append(ffconcatPath(image)).append("'\n");
            list.append("duration ").append(String.format(Locale.US, "%.6f", perImage)).append("\n");
        }
        // concat demuxer needs the last image repeated for its duration to be honored.
        list.append("file '").append(ffconcatPath(images.get(images.size() - 1))).append("'\n");
        Files.write(concat.toPath(), list.toString().getBytes(StandardCharsets.UTF_8));

        String filter = "scale=1080:1920:force_original_aspect_ratio=decrease," +
            "pad=1080:1920:(ow-iw)/2:(oh-ih)/2,setsar=1,format=yuv420p";
        File temp = new File(parent, output.getName() + ".rendering.mp4");
        Exception last = null;
        try {
            String[][] codecs = {
                {"-c:v", "libx264", "-preset", "veryfast", "-crf", "20"},
                {"-c:v", "mpeg4", "-q:v", "3"}
            };
            for (String[] codec : codecs) {
                temp.delete();
                java.util.ArrayList<String> args = new java.util.ArrayList<>();
                java.util.Collections.addAll(args, "-y", "-f", "concat", "-safe", "0", "-i", concat.getAbsolutePath(),
                    "-i", audio.getAbsolutePath(), "-vf", filter, "-r", "30");
                java.util.Collections.addAll(args, codec);
                java.util.Collections.addAll(args, "-c:a", "aac", "-b:a", "192k", "-shortest", "-movflags", "+faststart", temp.getAbsolutePath());
                var session = FFmpegKit.executeWithArguments(args.toArray(new String[0]));
                if (ReturnCode.isSuccess(session.getReturnCode()) && temp.isFile() && temp.length() > 0) {
                    Files.move(temp.toPath(), output.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    for (File image : images) image.delete();
                    audio.delete();
                    return;
                }
                last = new IllegalStateException("FFmpeg gagal membuat slideshow: " + session.getFailStackTrace());
            }
            throw last == null ? new IllegalStateException("FFmpeg gagal membuat slideshow") : last;
        } finally {
            concat.delete();
            temp.delete();
        }
    }

    private static double audioDurationSeconds(File audio) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(audio.getAbsolutePath());
            String duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            return duration == null ? 0 : Long.parseLong(duration) / 1000.0;
        } catch (Exception ignored) { return 0; }
        finally { try { retriever.release(); } catch (Exception ignored) { } }
    }

    private static String ffconcatPath(File file) throws Exception {
        return file.getCanonicalPath().replace("'", "'\\''");
    }
}

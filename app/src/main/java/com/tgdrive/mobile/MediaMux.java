package com.tgdrive.mobile;

import com.arthenica.ffmpegkit.FFmpegKit;
import com.arthenica.ffmpegkit.ReturnCode;
import android.os.Build;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/** Combines an existing video stream and a separate audio track locally. */
public final class MediaMux {
    private MediaMux() { }
    public static void merge(File video, File audio) throws Exception {
        boolean arm64 = false;
        for (String abi : Build.SUPPORTED_ABIS) if ("arm64-v8a".equals(abi)) arm64 = true;
        if (!arm64) throw new IllegalStateException("FFmpeg tersedia untuk perangkat Android arm64; unduhan lain tetap dapat digunakan");
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
}

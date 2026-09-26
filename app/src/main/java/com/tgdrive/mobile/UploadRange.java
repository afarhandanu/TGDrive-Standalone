package com.tgdrive.mobile;

/** Parse the acknowledged range from Drive, never guess an upload offset. */
final class UploadRange {
    static long offset(String range, long size) {
        if (range == null || range.isEmpty()) return 0;
        if (!range.matches("bytes=0-[0-9]+")) throw new IllegalStateException("Rentang upload Drive tidak valid");
        try {
            long last = Long.parseLong(range.substring(8));
            if (last < 0 || last >= size) throw new IllegalStateException("Rentang upload Drive tidak valid");
            return last + 1;
        } catch (NumberFormatException error) { throw new IllegalStateException("Rentang upload Drive tidak valid", error); }
    }
}

package com.tgdrive.mobile;
public final class UploadRangeTest {
    public static void main(String[] args) {
        equal(0, UploadRange.offset(null, 100));
        equal(0, UploadRange.offset("", 100));
        equal(1, UploadRange.offset("bytes=0-0", 100));
        equal(48, UploadRange.offset("bytes=0-47", 100));
        equal(100, UploadRange.offset("bytes=0-99", 100));
        equal(4_294_967_296L, UploadRange.offset("bytes=0-4294967295", 5_000_000_000L));
        for (String bad : new String[]{"bytes=1-3", "bytes=0--1", "bytes=0-100", "bytes=0-9223372036854775807", "bytes=0-999999999999999999999", "garbage"}) {
            try { UploadRange.offset(bad, 100); throw new AssertionError("accepted invalid range: " + bad); }
            catch (IllegalStateException expected) { }
        }
        System.out.println("PASS: 12 upload range cases");
    }
    private static void equal(long expected, long actual) {
        if (actual != expected) throw new AssertionError(expected + " != " + actual);
    }
}

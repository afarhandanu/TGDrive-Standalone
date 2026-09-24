package com.tgdrive.mobile;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.function.BooleanSupplier;

/** Shared deletion gate for local and Drive migrations. */
final class VerifiedMigration {
    interface Item {
        void copyOrReuse() throws Exception;
        boolean verify() throws Exception;
        void removeSource() throws Exception;
    }
    static void check(BooleanSupplier canceled) throws InterruptedIOException {
        if (canceled.getAsBoolean() || Thread.currentThread().isInterrupted())
            throw new InterruptedIOException("Pemindahan dibatalkan; file sumber yang belum diproses tetap disimpan");
    }
    static void run(Item item, boolean remove, BooleanSupplier canceled) throws Exception {
        check(canceled);
        item.copyOrReuse();
        check(canceled);
        if (!item.verify()) throw new IOException("Verifikasi salinan tidak cocok. File lama dipertahankan.");
        check(canceled);
        if (remove) item.removeSource();
    }
}

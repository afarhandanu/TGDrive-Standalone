package com.tgdrive.mobile;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

/** Run without Android/JUnit: exercises the actual shared source-deletion gate. */
public final class VerifiedMigrationTest {
    private static int passed;
    private static void check(boolean value,String message) { if(!value) throw new AssertionError(message); }
    private static final class FileItem implements VerifiedMigration.Item {
        final Path source,target;
        final AtomicBoolean canceled;
        boolean failCopy,corrupt,cancelAfterCopy,cancelAfterVerify,failDelete;
        int deletes,copies;
        FileItem(Path root,AtomicBoolean cancel) throws Exception {
            Path dir=Files.createTempDirectory(root,"case-"); source=dir.resolve("source.jpg"); target=dir.resolve("target.jpg");
            Files.writeString(source,"original media content"); canceled=cancel;
        }
        public void copyOrReuse() throws Exception {
            copies++;
            if(failCopy) { Files.writeString(target,"partial"); throw new IOException("disk full"); }
            if(!Files.exists(target)) Files.copy(source,target);
            if(corrupt) Files.writeString(target,"corrupted content");
            if(cancelAfterCopy) canceled.set(true);
        }
        public boolean verify() throws Exception {
            boolean matches=java.util.Arrays.equals(Files.readAllBytes(source),Files.readAllBytes(target));
            if(cancelAfterVerify) canceled.set(true);
            return matches;
        }
        public void removeSource() throws Exception {
            deletes++;
            if(failDelete) throw new IOException("permission denied");
            Files.delete(source);
        }
    }
    private static void expectedFailure(FileItem item, boolean remove) throws Exception {
        try { VerifiedMigration.run(item,remove,item.canceled::get); throw new AssertionError("Expected failure"); }
        catch(IOException expected) { }
        check(Files.exists(item.source),"Source must survive failure");
    }
    public static void main(String[] args) throws Exception {
        Path root=Files.createTempDirectory("migration-safety-");
        try {
            AtomicBoolean canceled=new AtomicBoolean();
            FileItem keep=new FileItem(root,canceled);
            VerifiedMigration.run(keep,false,canceled::get);
            check(Files.exists(keep.source) && Files.exists(keep.target) && keep.deletes==0,"Keep retains source"); passed++;
            VerifiedMigration.run(keep,true,canceled::get);
            check(!Files.exists(keep.source) && Files.exists(keep.target) && keep.deletes==1,"Retry uses copy and removes verified source"); passed++;
            FileItem bad=new FileItem(root,canceled); bad.corrupt=true; expectedFailure(bad,true); check(bad.deletes==0,"Never delete corrupted copy source"); passed++;
            FileItem full=new FileItem(root,canceled); full.failCopy=true; expectedFailure(full,true); check(full.deletes==0,"Never delete on disk full"); passed++;
            FileItem stop=new FileItem(root,canceled); stop.cancelAfterCopy=true; expectedFailure(stop,true); check(stop.deletes==0,"Cancel after copy retains source"); canceled.set(false); passed++;
            FileItem stopVerify=new FileItem(root,canceled); stopVerify.cancelAfterVerify=true; expectedFailure(stopVerify,true); check(stopVerify.deletes==0,"Cancel after verify retains source"); canceled.set(false); passed++;
            FileItem blocked=new FileItem(root,canceled); blocked.failDelete=true; expectedFailure(blocked,true); check(Files.exists(blocked.target),"Delete failure retains copy too"); passed++;
            FileItem never=new FileItem(root,canceled); canceled.set(true); expectedFailure(never,true); check(never.copies==0,"Canceled job must not start copy"); passed++;
            System.out.println(passed+" migration safety cases passed");
        } finally {
            try(var paths=Files.walk(root)) { paths.sorted(java.util.Comparator.reverseOrder()).forEach(p -> { try { Files.deleteIfExists(p); } catch(IOException ignored) {} }); }
        }
    }
}

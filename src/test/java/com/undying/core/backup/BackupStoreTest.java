package com.undying.core.backup;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The index file and the folder it lives in.
 *
 * <p>Two properties matter more than the happy path: a damaged index must be <b>partially</b>
 * readable rather than all-or-nothing, and a crash during a write must never leave a truncated
 * index in place of a good one.
 */
class BackupStoreTest {

    private static BackupVersion v(String path, long sec, long size) {
        return new BackupVersion(path, sec, size, null);
    }

    @Test
    void roundTripsThroughTheDisk(@TempDir Path world) throws IOException {
        Path root = BackupStore.root(world);
        List<BackupVersion> versions = List.of(
                v("level.dat", 100, 10),
                v("region/r.0.0.mca", 200, 4_194_304),
                new BackupVersion("region/r.0.1.mca", 300, 99, "abc123"));

        BackupStore.saveIndex(root, versions);
        var decoded = BackupStore.loadIndex(root);

        assertEquals(3, decoded.versions().size());
        assertEquals(0, decoded.skipped());
        assertEquals("abc123", decoded.versions().get(2).hash());
    }

    @Test
    void theIndexLivesUnderTheWorldFolder(@TempDir Path world) {
        Path root = BackupStore.root(world);
        assertTrue(root.startsWith(world), "backups must travel with the world");
        assertTrue(root.toString().contains("_undying_backup"));
    }

    @Test
    void aMissingIndexMeansNoCopiesYetNotAnError(@TempDir Path world) {
        var decoded = BackupStore.loadIndex(BackupStore.root(world));
        assertTrue(decoded.versions().isEmpty());
        assertEquals(0, decoded.skipped());
    }

    @Test
    void aDamagedLineDoesNotHideTheOthers() {
        String text = BackupIndexCodec.HEADER + "\n"
                + "level.dat\t100\t10\t-\n"
                + "this line is garbage\n"
                + "region/r.0.0.mca\t200\t20\t-\n"
                + "region/broken.mca\tNOTANUMBER\t20\t-\n"
                + "../../escape\t300\t5\t-\n";

        var decoded = BackupIndexCodec.decode(text);

        assertEquals(2, decoded.versions().size());
        assertEquals(3, decoded.skipped());
    }

    @Test
    void aFutureFormatIsRefusedRatherThanMisread() {
        String text = "undying-backup-index v3\nlevel.dat\t100\t10\t-\t12-00-00\n";
        var decoded = BackupIndexCodec.decode(text);

        assertTrue(decoded.versions().isEmpty(), "a v3 document must not be read by a v2 reader");
        assertEquals(1, decoded.skipped(), "the document is refused as a whole, not line by line");
    }

    @Test
    void aV1DocumentIsStillReadable() {
        // Four columns, no copy name: written by 2.9.2 and earlier. Reading it must keep working,
        // with the name falling back to the timestamp.
        String text = "undying-backup-index v1\nlevel.dat\t100\t10\t-\n";
        var decoded = BackupIndexCodec.decode(text);

        assertEquals(1, decoded.versions().size());
        assertEquals("level.dat", decoded.versions().get(0).path());
        assertNull(decoded.versions().get(0).copyName());
    }

    @Test
    void theCopyNameSurvivesARoundTrip() {
        // The name is what makes the second copy in one second reachable; deriving it from the
        // timestamp was the bug this column exists to fix.
        var versions = List.of(new BackupVersion("level.dat", 100, 10, null, "12-00-00-1"));
        String text = BackupIndexCodec.encode(versions);
        var decoded = BackupIndexCodec.decode(text);

        assertEquals(1, decoded.versions().size());
        assertEquals("12-00-00-1", decoded.versions().get(0).copyName());
    }

    @Test
    void savingOverwritesInPlaceAndLeavesNoTempFile(@TempDir Path world) throws IOException {
        Path root = BackupStore.root(world);
        BackupStore.saveIndex(root, List.of(v("level.dat", 100, 10)));
        BackupStore.saveIndex(root, List.of(v("level.dat", 200, 10), v("region/a.mca", 200, 5)));

        assertFalse(Files.exists(root.resolve("index.txt.tmp")), "temp file must be renamed away");
        assertEquals(2, BackupStore.loadIndex(root).versions().size());
    }

    @Test
    void usedBytesAddsUpEverythingIncludingLeftoverTempFiles(@TempDir Path world) throws IOException {
        Path root = BackupStore.root(world);
        Files.createDirectories(root.resolve("files/level.dat"));
        Files.writeString(root.resolve("index.txt"), "12345");
        Files.writeString(root.resolve("index.txt.tmp"), "678");
        Files.writeString(root.resolve("files/level.dat/2026-11-18_22-30-05"), "0123456789");

        long used = BackupStore.usedBytes(root);
        assertEquals(5 + 3 + 10, used);
    }

    @Test
    void freeSpaceIsReportedForARealPath(@TempDir Path world) {
        assertTrue(BackupStore.freeBytes(world) > 0, "temp dir should have usable space");
    }

    @Test
    void anUnknownFreeSpaceIsTreatedAsNoRoom() {
        // Guessing "plenty of room" on a full disk is how half-written backups happen.
        assertFalse(BackupStore.hasRoomFor(1, Path.of("no-such-drive-xyz", "a", "b")));
        assertTrue(BackupStore.freeBytes(Path.of("no-such-drive-xyz")) < 0);
    }

    @Test
    void roomCheckAllowsASmallCopyOnThisDisk(@TempDir Path world) {
        assertTrue(BackupStore.hasRoomFor(1024, world));
    }
}

package com.undying.core.backup;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Restoring a world from stored copies.
 *
 * <p>This is the only code in the module that writes into a save folder, so the tests concentrate
 * on the ways a restore can go wrong rather than on the happy path: does the rehearsal really
 * write nothing, is the replaced content recoverable, and does a partial failure put everything
 * back.
 */
class WorldRestoreTest {

    private static void write(Path file, String content) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    private static void writeAt(Path file, String content, long mtimeSeconds) throws IOException {
        write(file, content);
        Files.setLastModifiedTime(file,
                java.nio.file.attribute.FileTime.fromMillis(mtimeSeconds * 1000L));
    }

    /** Builds a world with one file that has two stored versions: "old" then "new". */
    private static long prepareTwoVersions(Path world) throws IOException {
        long t1 = 1_800_000_000L;
        Path level = world.resolve("level.dat");

        writeAt(level, "OLD-CONTENT", t1);
        BackupService.copyChanged(world, t1, 7, 0);

        writeAt(level, "NEW-CONTENT-LONGER", t1 + 60);
        BackupService.copyChanged(world, t1 + 60, 7, 0);

        return t1;
    }

    @Test
    void aDryRunWritesNothingAtAll(@TempDir Path world) throws IOException {
        long t1 = prepareTwoVersions(world);
        String before = Files.readString(world.resolve("level.dat"));
        List<String> listing = Files.list(world).map(p -> p.getFileName().toString()).sorted().toList();

        var outcome = WorldRestore.restore(world, t1, true);

        assertTrue(outcome.dryRun());
        assertEquals(1, outcome.planned());
        assertEquals(0, outcome.written());
        assertEquals(before, Files.readString(world.resolve("level.dat")));
        assertEquals(listing, Files.list(world).map(p -> p.getFileName().toString()).sorted().toList());
        assertFalse(Files.exists(BackupStore.root(world).resolve(WorldRestore.PRE_RESTORE_DIR)),
                "a rehearsal must not create the aside folder either");
    }

    @Test
    void aRealRestorePutsTheOlderContentBack(@TempDir Path world) throws IOException {
        long t1 = prepareTwoVersions(world);
        assertEquals("NEW-CONTENT-LONGER", Files.readString(world.resolve("level.dat")));

        var outcome = WorldRestore.restore(world, t1, false);

        assertFalse(outcome.rolledBack());
        assertEquals(1, outcome.written());
        assertEquals("OLD-CONTENT", Files.readString(world.resolve("level.dat")));
    }

    @Test
    void theReplacedContentIsKeptSoTheRestoreCanBeUndone(@TempDir Path world) throws IOException {
        long t1 = prepareTwoVersions(world);

        var outcome = WorldRestore.restore(world, t1, false);
        assertNotNull(outcome.asideDir());
        assertTrue(Files.isDirectory(outcome.asideDir()));

        assertTrue(WorldRestore.undo(world, outcome.asideDir()));
        assertEquals("NEW-CONTENT-LONGER", Files.readString(world.resolve("level.dat")),
                "undo must bring back exactly what the restore replaced");
    }

    @Test
    void filesWithNoOldEnoughCopyKeepTheirCurrentContent(@TempDir Path world) throws IOException {
        long t1 = prepareTwoVersions(world);
        writeAt(world.resolve("region/r.9.9.mca"), "BRAND-NEW", t1 + 120);

        var outcome = WorldRestore.restore(world, t1, false);

        assertEquals(1, outcome.written());
        assertEquals(1, outcome.keptCurrent());
        assertEquals("BRAND-NEW", Files.readString(world.resolve("region/r.9.9.mca")));
    }

    @Test
    void aMomentBeforeEveryCopyRestoresNothingAndSaysSo(@TempDir Path world) throws IOException {
        long t1 = prepareTwoVersions(world);

        var outcome = WorldRestore.restore(world, t1 - 1000, false);

        assertEquals(0, outcome.written());
        assertEquals(1, outcome.keptCurrent());
        assertEquals("NEW-CONTENT-LONGER", Files.readString(world.resolve("level.dat")));
    }

    @Test
    void theRestoredFileIsWrittenThroughATempAndRenamed(@TempDir Path world) throws IOException {
        long t1 = prepareTwoVersions(world);
        WorldRestore.restore(world, t1, false);

        try (var listing = Files.list(world)) {
            assertTrue(listing.noneMatch(p -> p.getFileName().toString().endsWith(".undying-tmp")),
                    "no temporary file may be left where the game looks for a world");
        }
    }

    @Test
    void anIndexPointingOutsideTheWorldIsIgnored(@TempDir Path world) throws IOException {
        long t1 = prepareTwoVersions(world);

        // A hand-edited index is the only hostile input this module has; the restore must skip it
        // rather than write next to the world folder.
        Path root = BackupStore.root(world);
        var index = new java.util.ArrayList<>(BackupStore.loadIndex(root).versions());
        index.add(new BackupVersion("../../escaped.txt", t1, 10, null));
        BackupStore.saveIndex(root, index);

        var outcome = WorldRestore.restore(world, t1, false);

        assertFalse(outcome.rolledBack());
        assertFalse(Files.exists(world.getParent().resolve("escaped.txt")));
    }

    @Test
    void aMissingWorldFolderIsNotACrash(@TempDir Path base) {
        var outcome = WorldRestore.restore(base.resolve("nope"), 100, false);
        assertEquals(0, outcome.written());
        assertEquals(0, outcome.planned());
    }

    @Test
    void summarySaysWhichModeItWas(@TempDir Path world) throws IOException {
        long t1 = prepareTwoVersions(world);

        assertTrue(WorldRestore.restore(world, t1, true).summaryZh().startsWith("演练"));
        assertTrue(WorldRestore.restore(world, t1, false).summaryZh().startsWith("已恢复"));
    }
}

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
 * Copying changed files into the backup folder.
 *
 * <p>Runs on temp directories only. The properties that matter: the world is never written to,
 * the index never disagrees with the disk, and every refusal happens <b>before</b> anything is
 * deleted.
 */
class BackupServiceTest {

    private static void write(Path file, String content) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    private static long now() {
        return 1_800_000_000L;
    }

    /**
     * Writes a file and sets its modification time explicitly.
     *
     * <p>Needed because "changed" is decided by mtime first: a test that relies on the real clock
     * would be comparing it against a fixed {@code now()} in the future and silently exercise
     * nothing.
     */
    private static void writeAt(Path file, String content, long mtimeSeconds) throws IOException {
        write(file, content);
        Files.setLastModifiedTime(file,
                java.nio.file.attribute.FileTime.fromMillis(mtimeSeconds * 1000L));
    }

    @Test
    void copiesChangedFilesAndRecordsThem(@TempDir Path world) throws IOException {
        write(world.resolve("level.dat"), "nbt-data");
        write(world.resolve("region/r.0.0.mca"), "chunkdata");

        var result = BackupService.copyChanged(world, now(), 7, 0, 0);

        assertTrue(result.ran(), result.summaryZh());
        assertEquals(2, result.copied());

        var index = BackupStore.loadIndex(BackupStore.root(world)).versions();
        assertEquals(2, index.size());
        for (BackupVersion v : index) {
            assertEquals(now(), v.seconds());
        }
    }

    @Test
    void copiesActuallyLandOnDiskAtTheIndexedPath(@TempDir Path world) throws IOException {
        write(world.resolve("region/r.0.0.mca"), "chunkdata");
        BackupService.copyChanged(world, now(), 7, 0, 0);

        BackupVersion v = BackupStore.loadIndex(BackupStore.root(world)).versions().get(0);
        Path copy = BackupService.copyPath(BackupStore.root(world), v);

        assertTrue(Files.isRegularFile(copy), copy.toString());
        assertEquals("chunkdata", Files.readString(copy));
    }

    @Test
    void aSecondRunWithNoChangesDoesNothing(@TempDir Path world) throws IOException {
        write(world.resolve("level.dat"), "x");
        BackupService.copyChanged(world, now(), 7, 0, 0);

        var second = BackupService.copyChanged(world, now() + 60, 7, 0, 0);

        assertEquals(BackupService.Refusal.NOTHING_TO_DO, second.refusal());
        assertEquals(0, second.copied());
    }

    @Test
    void aChangedFileGetsANewVersionAndTheOldOneStays(@TempDir Path world) throws IOException {
        Path level = world.resolve("level.dat");
        write(level, "version-one");
        BackupService.copyChanged(world, now(), 7, 0, 0);

        write(level, "version-two-longer");
        BackupService.copyChanged(world, now() + 60, 7, 0, 0);

        var index = BackupStore.loadIndex(BackupStore.root(world)).versions();
        assertEquals(2, index.size(), "both versions must remain for a rollback");
    }

    @Test
    void theWorldItselfIsNeverWrittenTo(@TempDir Path world) throws IOException {
        Path level = world.resolve("level.dat");
        write(level, "original");
        long mtimeBefore = Files.getLastModifiedTime(level).toMillis();

        BackupService.copyChanged(world, now(), 7, 0, 0);

        assertEquals("original", Files.readString(level));
        assertEquals(mtimeBefore, Files.getLastModifiedTime(level).toMillis(),
                "a backup must not touch the file it copies");
    }

    @Test
    void theSessionLockIsNeverCopied(@TempDir Path world) throws IOException {
        write(world.resolve("session.lock"), "42");
        write(world.resolve("level.dat"), "x");

        BackupService.copyChanged(world, now(), 7, 0, 0);

        var index = BackupStore.loadIndex(BackupStore.root(world)).versions();
        assertEquals(1, index.size());
        assertEquals("level.dat", index.get(0).path());
    }

    @Test
    void depthIsEnforcedThroughTheService(@TempDir Path world) throws IOException {
        Path level = world.resolve("level.dat");
        for (int i = 0; i < 4; i++) {
            long at = now() + i * 60L;
            writeAt(level, "v" + i + "-padding", at);
            BackupService.copyChanged(world, at, 2, 0, 0);
        }

        var index = BackupStore.loadIndex(BackupStore.root(world)).versions();
        assertEquals(2, index.size(), "depth 2 must leave two copies");
    }

    @Test
    void aBudgetThatCannotBeMetWritesNothingAndDeletesNothing(@TempDir Path world) throws IOException {
        write(world.resolve("level.dat"), "x");
        BackupService.copyChanged(world, now(), 7, 0, 0);
        long usedAfterFirst = BackupStore.usedBytes(BackupStore.root(world));

        write(world.resolve("big.mca"), "y".repeat(4096));
        // A limit below what is already used: every copy is the last copy of its file, so the
        // budget cannot be met and nothing may be removed.
        var result = BackupService.copyChanged(world, now() + 60, 7, 10, 0);

        assertNotNull(result.refusal());
        assertEquals(usedAfterFirst, BackupStore.usedBytes(BackupStore.root(world)),
                "a refused run must leave the folder exactly as it was");
    }

    @Test
    void anEmptyWorldIsNotAnError(@TempDir Path world) {
        var result = BackupService.copyChanged(world, now(), 7, 0, 0);
        assertEquals(BackupService.Refusal.NOTHING_TO_DO, result.refusal());
        assertFalse(result.ran());
    }

    // ══════════ stableSince ══════════

    @Test
    void theServiceItselfSkipsFilesThatWereJustWritten(@TempDir Path world) throws IOException {
        // The quiet window is the difference between "a copy" and "half a region file with a
        // matching hash". Default is 10 s; a file written this second must wait.
        // mtime must be *at* now() for this to test the quiet window: writeAt() sets it,
        // plain write() would leave the real clock's timestamp, which is decades away from the
        // fixed now() used here and would therefore look "quiet".
        writeAt(world.resolve("level.dat"), "fresh", now());
        var result = BackupService.copyChanged(world, now(), 7, 0);
        assertEquals(BackupService.Refusal.NOTHING_TO_DO, result.refusal(),
                "a file touched this second must not be copied with the default quiet window");
    }

    @Test
    void filesTouchedJustNowAreLeftForTheNextRound() {
        List<WorldFile> files = List.of(
                new WorldFile("just-written.mca", 10, now()),
                new WorldFile("settled.mca", 10, now() - 40));

        List<WorldFile> stable = BackupService.stableSince(files, now(), 30);

        assertEquals(List.of("settled.mca"), stable.stream().map(WorldFile::path).toList());
    }

    @Test
    void theQuietWindowIsMeasuredFromTheModificationTime() {
        List<WorldFile> files = List.of(new WorldFile("edge.mca", 10, now() - 30));
        assertEquals(1, BackupService.stableSince(files, now(), 30).size());
        assertEquals(0, BackupService.stableSince(files, now(), 31).size());
    }
}

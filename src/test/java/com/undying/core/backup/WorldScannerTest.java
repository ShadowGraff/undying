package com.undying.core.backup;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Scanning a world directory.
 *
 * <p>Runs entirely on a temp directory this test creates, so it needs no game and touches nothing
 * of the player's. What it is really checking is the two rules that keep a scan from becoming a
 * disaster: it must skip live locks, and it must never descend into its own backup folder.
 */
class WorldScannerTest {

    private static void write(Path file, String content) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    @Test
    void findsWorldFilesAndSkipsTheLiveLock(@TempDir Path world) throws IOException {
        write(world.resolve("level.dat"), "not really nbt");
        write(world.resolve("region/r.0.0.mca"), "chunkdata");
        write(world.resolve("session.lock"), "42");

        List<WorldFile> files = WorldScanner.scan(world);

        assertEquals(List.of("level.dat", "region/r.0.0.mca"),
                files.stream().map(WorldFile::path).toList());
    }

    @Test
    void neverDescendsIntoItsOwnBackupFolder(@TempDir Path world) throws IOException {
        write(world.resolve("level.dat"), "x");
        write(world.resolve("_undying_backup/world/index.txt"), "undying-backup-index v1\n");
        write(world.resolve("_undying_backup/world/files/level.dat/2026-11-18_22-30-05"), "copy");

        List<WorldFile> files = WorldScanner.scan(world);

        assertEquals(1, files.size());
        assertEquals("level.dat", files.get(0).path());
    }

    @Test
    void emptyFilesAreIgnored(@TempDir Path world) throws IOException {
        write(world.resolve("region/empty.mca"), "");
        write(world.resolve("level.dat"), "x");

        assertEquals(List.of("level.dat"),
                WorldScanner.scan(world).stream().map(WorldFile::path).toList());
    }

    @Test
    void pathsUseForwardSlashesOnEveryPlatform(@TempDir Path world) throws IOException {
        write(world.resolve("DIM-1/region/r.0.0.mca"), "x");
        write(world.resolve("playerdata/0123.dat"), "x");

        List<String> paths = WorldScanner.scan(world).stream().map(WorldFile::path).toList();

        assertTrue(paths.contains("DIM-1/region/r.0.0.mca"), paths.toString());
        assertTrue(paths.stream().noneMatch(p -> p.contains("\\")), paths.toString());
    }

    @Test
    void aMissingDirectoryIsNotAnError() throws IOException {
        assertTrue(WorldScanner.scan(Path.of("does", "not", "exist", "at-all")).isEmpty());
    }

    @Test
    void resultsAreSortedSoTheReportIsDiffable(@TempDir Path world) throws IOException {
        write(world.resolve("region/r.1.0.mca"), "x");
        write(world.resolve("level.dat"), "x");
        write(world.resolve("data/raids.dat"), "x");

        List<String> paths = WorldScanner.scan(world).stream().map(WorldFile::path).toList();
        assertEquals(List.of("data/raids.dat", "level.dat", "region/r.1.0.mca"), paths);
    }

    // ══════════ changed() ══════════

    private static WorldFile f(String path, long size, long mtime) {
        return new WorldFile(path, size, mtime);
    }

    @Test
    void changeDetectionIsBySizeOrTime() {
        List<WorldFile> before = List.of(f("a.mca", 10, 100), f("b.mca", 20, 100), f("c.mca", 30, 100));
        List<WorldFile> after = List.of(
                f("a.mca", 11, 100),     // size changed
                f("b.mca", 20, 200),     // mtime changed
                f("c.mca", 30, 100));    // untouched

        assertEquals(List.of("a.mca", "b.mca"),
                WorldScanner.changed(before, after).stream().map(WorldFile::path).toList());
    }

    @Test
    void aNewFileCountsAsChanged() {
        List<WorldFile> after = List.of(f("new.mca", 5, 300));
        assertEquals(1, WorldScanner.changed(List.of(), after).size());
    }

    @Test
    void aDeletedFileIsNotReportedAsChanged() {
        // It has no content to copy. The index still holds its last copy, which is what a
        // rollback would restore it from.
        List<WorldFile> before = List.of(f("gone.mca", 10, 100));
        assertTrue(WorldScanner.changed(before, List.of()).isEmpty());
    }
}

package com.undying.core.backup;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Reads the shape of a world directory: what files exist, how big, how old.
 *
 * <h3>What this deliberately does not do</h3>
 * It does not open a single file. The whole cost of a scan is {@code stat} calls on the directory
 * entries, which is why a scan can run once a minute without the player ever feeling it. Reading
 * contents here would turn a millisecond operation into a 250 MB one, and it would do so on the
 * server thread.
 *
 * <h3>Two filesystem rules that are not optional</h3>
 * <ul>
 *   <li><b>Never follow symlinks.</b> A world containing a symlink to its parent would make the
 *       walk infinite, and a symlink to {@code C:\} would make the backup enormous. Both are
 *       possible by accident (a mod, a sync tool, a player tidying up).</li>
 *   <li><b>Never throw on one unreadable entry.</b> A file being written by another process can
 *       fail its {@code stat} on Windows. Losing the whole scan for one file would silently stop
 *       backups — the failure mode this project keeps having to design against.</li>
 * </ul>
 *
 * <p>{@code _undying_backup} inside the world directory is skipped by {@link BackupFilter}, which
 * also keeps the walk finite if a player ever points the backup root inside the world.
 */
public final class WorldScanner {

    private WorldScanner() {}

    /**
     * List every file worth backing up, sorted by path.
     *
     * @return the files; empty when the directory does not exist
     */
    public static List<WorldFile> scan(Path worldDir) throws IOException {
        if (worldDir == null || !Files.isDirectory(worldDir)) return List.of();

        List<WorldFile> out = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(worldDir)) {
            walk.forEach(p -> collect(worldDir, p, out));
        }

        // Sorted so that a scan report is diffable between runs and the index is stable.
        out.sort(Comparator.comparing(WorldFile::path));
        return List.copyOf(out);
    }

    private static void collect(Path worldDir, Path p, List<WorldFile> out) {
        try {
            if (Files.isDirectory(p, LinkOption.NOFOLLOW_LINKS)) return;

            String rel = relative(worldDir, p);
            if (rel == null || BackupFilter.isExcluded(rel)) return;

            BasicFileAttributes attrs = Files.readAttributes(p, BasicFileAttributes.class,
                    LinkOption.NOFOLLOW_LINKS);
            if (!attrs.isRegularFile()) return;     // symlink or special file: not ours to copy
            if (!BackupFilter.isWorthCopying(rel, attrs.size())) return;

            out.add(new WorldFile(rel, attrs.size(), attrs.lastModifiedTime().toMillis() / 1000L));
        } catch (Throwable ignored) {
            // One unreadable entry must not abort the scan. The next run will pick it up if it
            // becomes readable; if it never does, the report already shows the file missing.
        }
    }

    private static String relative(Path worldDir, Path file) {
        try {
            Path rel = worldDir.relativize(file);
            StringBuilder sb = new StringBuilder();
            for (Path part : rel) {
                if (sb.length() > 0) sb.append('/');
                sb.append(part.toString());
            }
            return sb.toString();
        } catch (Throwable t) {
            return null;   // different roots (should not happen): treat as unreadable
        }
    }

    /**
     * Files whose {@code mtime} or size differ from the previous scan.
     *
     * <p>This is the only "did anything change" rule in the module. It compares against
     * {@link WorldFile} rather than against the backup index because a file can be modified and
     * modified back — the version on disk is new even if its size is old, and the copy we want to
     * keep is the one that existed before the edit.
     */
    public static List<WorldFile> changed(List<WorldFile> previous, List<WorldFile> current) {
        if (current == null || current.isEmpty()) return List.of();
        if (previous == null || previous.isEmpty()) return List.copyOf(current);

        java.util.Map<String, WorldFile> before = new java.util.HashMap<>();
        for (WorldFile f : previous) before.put(f.path(), f);

        List<WorldFile> changed = new ArrayList<>();
        for (WorldFile now : current) {
            WorldFile then = before.get(now.path());
            if (then == null || then.size() != now.size() || then.mtimeSeconds() != now.mtimeSeconds()) {
                changed.add(now);
            }
        }
        changed.sort(Comparator.comparing(WorldFile::path));
        return List.copyOf(changed);
    }
}

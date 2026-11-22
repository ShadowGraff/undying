package com.undying.core.backup;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.undying.core.IntegrityGuard;

/**
 * Puts stored copies back into the world. <b>This is the only code in the mod that writes into a
 * save folder.</b>
 *
 * <h3>Shape of the operation</h3>
 * <pre>
 *   plan → (dry run stops here) → keep a copy of what will be replaced → write → report
 * </pre>
 *
 * <h3>Three rules that are not negotiable</h3>
 * <ol>
 *   <li><b>Dry run first.</b> {@code dryRun = true} computes and returns exactly what would be
 *       written, and touches nothing. The confirmation screen is built from that output, so the
 *       player reads the same numbers the writer will act on — not a separate guess that can
 *       drift.</li>
 *   <li><b>Everything replaced is copied aside first.</b> Only the files about to be overwritten
 *       — not the whole world. Undoing a restore needs exactly those files, and copying 250 MB
 *       to protect eight files is how a "safe" feature becomes a slow one nobody uses.</li>
 *   <li><b>A failure rolls back.</b> If any write fails, everything already written is restored
 *       from the aside copies before returning. A half-applied restore is a world that is neither
 *       moment, and unlike a missing backup it looks playable.</li>
 * </ol>
 *
 * <h3>What it refuses to do</h3>
 * It does not delete files that did not exist at the target moment, and it does not resurrect
 * files whose copies are all newer than that moment. Both are cases where "helpful" behaviour
 * invents or destroys data; the plan counts them and the report states them instead.
 *
 * <p>The caller must ensure the world is not open. There is no reliable in-process test for that
 * (the game can be at the title screen with a world loaded in another thread), so the entry point
 * lives on the main menu where the condition is true by construction.
 */
public final class WorldRestore {

    private WorldRestore() {}

    public static final String PRE_RESTORE_DIR = "pre-restore";

    /**
     * @param planned      files the plan wanted to write
     * @param written      files actually written (0 on a dry run)
     * @param keptCurrent  files with no copy old enough, left alone
     * @param failed       files that could not be written
     * @param rolledBack   whether a failure was undone from the aside copies
     * @param asideDir     where the replaced files were kept, or null on a dry run
     * @param hashMismatch copies whose recorded hash did not match the file written
     * @param dryRun       whether this was a rehearsal (nothing was written by design)
     */
    public record Outcome(int planned, int written, int keptCurrent, int failed,
                          boolean rolledBack, Path asideDir, int hashMismatch, boolean dryRun) {

        public String summaryZh() {
            StringBuilder sb = new StringBuilder();
            if (dryRun()) {
                sb.append("演练：将恢复 ").append(planned).append(" 个文件");
            } else if (rolledBack) {
                sb.append("恢复失败，已回滚到恢复前（写入 ").append(written).append(" 个，失败 ").append(failed).append(" 个）");
            } else {
                sb.append("已恢复 ").append(written).append(" 个文件");
            }
            if (keptCurrent > 0) sb.append("；").append(keptCurrent).append(" 个文件保持现状");
            if (hashMismatch > 0) sb.append("；").append(hashMismatch).append(" 个副本哈希不符");
            return sb.toString();
        }

        public String summaryEn() {
            StringBuilder sb = new StringBuilder();
            if (dryRun()) {
                sb.append("Dry run: would restore ").append(planned).append(" file(s)");
            } else if (rolledBack) {
                sb.append("Restore failed and was rolled back (").append(written)
                        .append(" written, ").append(failed).append(" failed)");
            } else {
                sb.append("Restored ").append(written).append(" file(s)");
            }
            if (keptCurrent > 0) sb.append("; ").append(keptCurrent).append(" left as they are");
            if (hashMismatch > 0) sb.append("; ").append(hashMismatch).append(" hash mismatch(es)");
            return sb.toString();
        }
    }

    /**
     * Roll the world back to {@code atSeconds}.
     *
     * @param dryRun when true, compute and report only — no file is created, modified or removed
     */
    public static Outcome restore(Path worldDir, long atSeconds, boolean dryRun) {
        if (worldDir == null || !Files.isDirectory(worldDir)) {
            return new Outcome(0, 0, 0, 0, false, null, 0, dryRun);
        }
        Path root = BackupStore.root(worldDir);

        try {
            Set<String> onDisk = new HashSet<>();
            for (WorldFile f : WorldScanner.scan(worldDir)) onDisk.add(f.path());

            List<BackupVersion> index = BackupStore.loadIndex(root).versions();
            RestorePlan.Plan plan = RestorePlan.plan(index, atSeconds, onDisk);

            if (plan.empty() || dryRun) {
                return new Outcome(plan.restored(), 0, plan.keptCurrent(), 0, false, null, 0, dryRun);
            }

            // ── Keep aside everything that is about to be replaced ──
            Path aside = root.resolve(PRE_RESTORE_DIR).resolve(HumanStamp.folder(
                    System.currentTimeMillis() / 1000L, java.time.ZoneId.systemDefault()));
            List<String> written = new ArrayList<>();
            int mismatch = 0;

            try {
                for (RestorePlan.Entry entry : plan.entries()) {
                    if (!entry.restored()) continue;

                    Path target = safeResolve(worldDir, entry.path());
                    if (target == null) continue;   // unsafe path: refuse this one, keep going

                    // Copy the current version aside before overwriting it.
                    if (Files.isRegularFile(target)) {
                        Path keep = safeResolve(aside, entry.path());
                        if (keep != null) {
                            Files.createDirectories(keep.getParent());
                            Files.copy(target, keep, StandardCopyOption.REPLACE_EXISTING);
                        }
                    }

                    Path copy = BackupService.copyPath(root, entry.version());
                    if (!Files.isRegularFile(copy)) continue;   // index says yes, disk says no

                    // Verify before writing. A copy that no longer matches its hash is either
                    // damaged or was replaced; writing it would restore corruption over data
                    // that might still be fine.
                    if (entry.version().hasHash()) {
                        String actual = BackupService.sha256(copy);
                        if (!entry.version().hash().equals(actual)) {
                            mismatch++;
                            IntegrityGuard.LOGGER.warn(
                                    "[restore] hash mismatch for {}, skipped (expected {}, got {})",
                                    entry.path(), entry.version().hash(), actual);
                            continue;
                        }
                    }

                    Files.createDirectories(target.getParent());
                    // Write beside the target and rename, so an interrupted write cannot leave a
                    // half-region file where the game will read it.
                    Path tmp = target.resolveSibling(target.getFileName() + ".undying-tmp");
                    Files.copy(copy, tmp, StandardCopyOption.REPLACE_EXISTING);
                    Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
                    written.add(entry.path());
                }
            } catch (Throwable failure) {
                // Roll back whatever landed; a half-applied restore is worse than none.
                undoFrom(worldDir, aside, written);
                return new Outcome(plan.restored(), written.size(), plan.keptCurrent(),
                        plan.restored() - written.size(), true, aside, 0, false);
            }

            return new Outcome(plan.restored(), written.size(), plan.keptCurrent(), 0, false,
                    aside, mismatch, false);

        } catch (Throwable t) {
            return new Outcome(0, 0, 0, 0, false, null, 0, false);
        }
    }

    /** Put the replaced files back the way they were. Used by the "undo" action on the screen. */
    public static boolean undo(Path worldDir, Path asideDir) {
        if (worldDir == null || asideDir == null || !Files.isDirectory(asideDir)) return false;
        try {
            List<String> paths = new ArrayList<>();
            try (var walk = Files.walk(asideDir)) {
                walk.filter(Files::isRegularFile).forEach(p -> {
                    Path rel = asideDir.relativize(p);
                    StringBuilder sb = new StringBuilder();
                    for (Path part : rel) {
                        if (sb.length() > 0) sb.append('/');
                        sb.append(part);
                    }
                    paths.add(sb.toString());
                });
            }
            return undoFrom(worldDir, asideDir, paths) == 0;
        } catch (Throwable t) {
            return false;
        }
    }

    /** @return how many files could not be put back */
    private static int undoFrom(Path worldDir, Path asideDir, List<String> paths) {
        int failed = 0;
        for (String rel : paths) {
            try {
                Path keep = safeResolve(asideDir, rel);
                Path target = safeResolve(worldDir, rel);
                if (keep == null || target == null || !Files.isRegularFile(keep)) continue;
                Files.createDirectories(target.getParent());
                Files.copy(keep, target, StandardCopyOption.REPLACE_EXISTING);
            } catch (Throwable t) {
                failed++;
            }
        }
        return failed;
    }

    /**
     * Join a relative path to a base, refusing anything {@link SafePath} rejects.
     *
     * <p>The index is a file on disk that a player can edit. This is the only place in the mod
     * whose input is a path from that file, so it is the only place where a rejected path matters.
     */
    private static Path safeResolve(Path base, String relativePath) {
        if (!SafePath.isSafe(relativePath)) return null;
        Path resolved = base.resolve(relativePath).normalize();
        if (!resolved.startsWith(base.normalize())) return null;   // belt and braces
        return resolved;
    }
}

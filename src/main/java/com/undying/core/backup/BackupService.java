package com.undying.core.backup;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Copies changed world files into the backup folder. <b>Never writes into the world.</b>
 *
 * <h3>What makes a file "changed"</h3>
 * The newest stored copy for that path is compared with the file on disk: a different size, or a
 * modification time later than the copy, means changed. That is deliberately generous — a file
 * that was touched without its content changing costs one wasted copy, while the opposite mistake
 * (missing a real change) costs the player their world. The generous direction is the only
 * affordable one.
 *
 * <h3>Order of operations, and why it is this order</h3>
 * <ol>
 *   <li>Scan, plan, and size the work — no writes yet.</li>
 *   <li>Ask the budget. If it does not fit, <b>stop before touching the disk</b>: an aborted
 *       backup that changed nothing is a clean state; one that deleted old copies and then failed
 *       to write new ones is a loss.</li>
 *   <li>Prune, then copy, then record each copy in the index as it lands.</li>
 * </ol>
 *
 * <h3>One copy at a time, index updated per copy</h3>
 * Writing the index only at the end would leave "copies on disk that the index does not know
 * about" whenever the game closes mid-run — orphans that take space and can never be restored
 * from. Updating per copy costs a few small writes and removes that whole class of mess.
 *
 * <h3>Does not hold a lock, does not try to be clever about the game writing</h3>
 * A region file being written while it is copied can produce a copy that is neither the old nor
 * the new content. That is handled upstream by only copying files whose mtime has been stable for
 * a while ({@link #stableSince}); this class assumes the caller did that and just copies.
 */
public final class BackupService {

    private BackupService() {}

    /**
     * @param copied      files successfully copied this run
     * @param copiedBytes bytes written
     * @param skipped     files that were changed but could not be copied (locked, unreadable)
     * @param pruned      old copies deleted to respect depth/budget
     * @param usedBytes   backup folder size after the run, or -1 when unknown
     * @param refusal     why the run did nothing, or null when it ran
     */
    public record Result(int copied, long copiedBytes, int skipped, int pruned, long usedBytes,
                         Refusal refusal) {

        public boolean ran() {
            return refusal == null;
        }

        public String summaryZh() {
            if (refusal != null) return refusal.zh;
            StringBuilder sb = new StringBuilder();
            sb.append("已备份 ").append(copied).append(" 个文件");
            if (pruned > 0) sb.append("，清理旧副本 ").append(pruned).append(" 份");
            if (skipped > 0) sb.append("，").append(skipped).append(" 个文件被占用跳过");
            return sb.toString();
        }

        public String summaryEn() {
            if (refusal != null) return refusal.en;
            StringBuilder sb = new StringBuilder();
            sb.append(copied).append(" file(s) backed up");
            if (pruned > 0) sb.append(", ").append(pruned).append(" old copy/copies pruned");
            if (skipped > 0) sb.append(", ").append(skipped).append(" locked file(s) skipped");
            return sb.toString();
        }
    }

    /** Why a run refused to start. Each one is a decision, not an error path. */
    public enum Refusal {
        NO_ROOM("磁盘剩余空间不足，本次不备份（已有的副本未动）",
                "Not enough free disk space; nothing was written and no copy was removed"),
        OVER_BUDGET("备份空间已达上限，本次不备份（已有的副本未动）",
                "Backup folder is at its limit; nothing was written and no copy was removed"),
        NOTHING_TO_DO("没有文件变化，无需备份", "Nothing changed, nothing to back up"),
        IO_ERROR("备份失败（详细信息见日志）", "Backup failed (see the log)");

        public final String zh;
        public final String en;

        Refusal(String zh, String en) {
            this.zh = zh;
            this.en = en;
        }
    }

    /**
     * Copy whatever changed.
     *
     * @param worldDir   the world folder to read from
     * @param nowSeconds timestamp to record for this run
     * @param depth      per-file retention
     * @param limitBytes total folder budget, {@code <= 0} for none
     */
    public static Result copyChanged(Path worldDir, long nowSeconds, int depth, long limitBytes) {
        Path root = BackupStore.root(worldDir);
        try {
            List<WorldFile> scanned = WorldScanner.scan(worldDir);
            List<BackupVersion> index = new ArrayList<>(BackupStore.loadIndex(root).versions());

            Map<String, BackupVersion> newest = newestPerPath(index);
            List<WorldFile> candidates = new ArrayList<>();
            long incoming = 0L;

            for (WorldFile f : scanned) {
                BackupVersion last = newest.get(f.path());
                if (last == null || last.size() != f.size() || f.mtimeSeconds() > last.seconds()) {
                    candidates.add(f);
                    incoming += f.size();
                }
            }

            if (candidates.isEmpty()) {
                return new Result(0, 0L, 0, 0, BackupStore.usedBytes(root), Refusal.NOTHING_TO_DO);
            }

            long used = BackupStore.usedBytes(root);
            if (used < 0) used = 0;

            if (!BackupStore.hasRoomFor(incoming, worldDir)) {
                return new Result(0, 0L, candidates.size(), 0, used, Refusal.NO_ROOM);
            }

            StorageBudget.Decision budget =
                    StorageBudget.plan(used, limitBytes, incoming, index, depth);
            if (!budget.fits()) {
                return new Result(0, 0L, candidates.size(), 0, used, Refusal.OVER_BUDGET);
            }

            // Prune first: freeing space before writing is what makes the budget meaningful.
            int pruned = 0;
            for (BackupVersion doomed : budget.delete()) {
                try {
                    Files.deleteIfExists(copyPath(root, doomed));
                    index.remove(doomed);
                    pruned++;
                } catch (Throwable ignored) {
                    // Leaving one old copy behind is harmless; failing the run is not.
                }
            }

            String stamp = stampOf(nowSeconds);
            int copied = 0;
            int skipped = 0;
            long copiedBytes = 0L;
            List<BackupVersion> added = new ArrayList<>();

            for (WorldFile f : candidates) {
                try {
                    Path source = worldDir.resolve(f.path());
                    Path target = uniqueTarget(root, f.path(), stamp);

                    Files.createDirectories(target.getParent());
                    Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);

                    BackupVersion v = new BackupVersion(f.path(), nowSeconds, f.size(), null);
                    added.add(v);
                    index.add(v);
                    copied++;
                    copiedBytes += f.size();

                    // Recorded immediately: a copy nobody knows about is space that can never
                    // be restored from, and it would survive every future prune.
                    BackupStore.saveIndex(root, index);
                } catch (Throwable t) {
                    skipped++;
                }
            }

            // Depth is applied again now that this run's copies exist. Doing it before the copy
            // was written cannot see them, and a run that adds one copy per minute would quietly
            // exceed the depth limit by exactly one every time.
            pruned += pruneToDepth(root, index, depth);

            if (added.isEmpty() && pruned == 0) {
                return new Result(0, 0L, skipped, 0, BackupStore.usedBytes(root), Refusal.IO_ERROR);
            }
            return new Result(copied, copiedBytes, skipped, pruned, BackupStore.usedBytes(root), null);

        } catch (Throwable t) {
            return new Result(0, 0L, 0, 0, -1L, Refusal.IO_ERROR);
        }
    }

    /**
     * Skip files that were modified very recently.
     *
     * <p>Copying a region file while the game is halfway through writing it produces a file that
     * is neither the old nor the new version — and unlike a missing copy, it looks valid. So the
     * caller waits until a file has not been touched for {@code quietSeconds} before copying it.
     * This is the "static" gate from the design, expressed as a function so it can be tested.
     */
    public static List<WorldFile> stableSince(List<WorldFile> files, long nowSeconds, int quietSeconds) {
        if (files == null || files.isEmpty()) return List.of();
        int quiet = Math.max(0, quietSeconds);
        List<WorldFile> out = new ArrayList<>();
        for (WorldFile f : files) {
            if (nowSeconds - f.mtimeSeconds() >= quiet) out.add(f);
        }
        out.sort(java.util.Comparator.comparing(WorldFile::path));
        return List.copyOf(out);
    }

    /**
     * Delete copies beyond the depth limit.
     *
     * @return how many were removed
     */
    private static int pruneToDepth(Path root, List<BackupVersion> index, int depth) {
        List<BackupVersion> doomed = Retention.toDelete(index, depth);
        int removed = 0;
        for (BackupVersion v : doomed) {
            try {
                Files.deleteIfExists(copyPath(root, v));
                index.remove(v);
                removed++;
            } catch (Throwable ignored) {
                // Keep going: one stubborn file must not stop the rest of the pruning.
            }
        }
        if (removed > 0) {
            try {
                BackupStore.saveIndex(root, index);
            } catch (Throwable ignored) {
                // The copies are already gone; a stale index line will be skipped next load.
            }
        }
        return removed;
    }

    private static Map<String, BackupVersion> newestPerPath(List<BackupVersion> index) {
        Map<String, BackupVersion> newest = new HashMap<>();
        for (BackupVersion v : index) {
            BackupVersion current = newest.get(v.path());
            if (current == null || v.seconds() > current.seconds()) newest.put(v.path(), v);
        }
        return newest;
    }

    static Path copyPath(Path root, BackupVersion version) {
        return root.resolve("files").resolve(version.path())
                .resolve(stampOf(version.seconds()));
    }

    /**
     * Folder name for a copy. Kept in one place so the writer and the pruner can never disagree
     * about where a copy lives — a pruner that deletes the wrong path is data loss.
     */
    private static String stampOf(long seconds) {
        return HumanStamp.folder(seconds, java.time.ZoneId.systemDefault());
    }

    /**
     * A target that does not exist yet.
     *
     * <p>Two copies of the same file inside one second would otherwise overwrite each other and
     * silently lose a version. Appending a counter keeps every copy addressable.
     */
    private static Path uniqueTarget(Path root, String relPath, String stamp) throws IOException {
        Path dir = root.resolve("files").resolve(relPath);
        Path candidate = dir.resolve(stamp);
        int n = 1;
        while (Files.exists(candidate)) {
            candidate = dir.resolve(stamp + "-" + n);
            n++;
            if (n > 99) throw new IOException("too many copies in one second: " + relPath);
        }
        return candidate;
    }
}

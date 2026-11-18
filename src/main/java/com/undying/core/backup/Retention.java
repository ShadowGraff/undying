package com.undying.core.backup;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * How many copies of one file to keep.
 *
 * <h3>Why per file, not per "generation"</h3>
 * A generation model (hourly snapshots that share unchanged files) breaks the moment the oldest
 * generation is deleted: everything after it depends on it. Per-file history is the only model
 * where "delete the oldest" stays safe, which is exactly what an unbounded backup needs.
 *
 * <p>The cost is uneven: a region file the player walks through constantly fills its slots while
 * a {@code level.dat} keeps one from last week. That is the correct trade — the file that changes
 * often is also the one worth having recent copies of.
 *
 * <h3>Ties</h3>
 * Two copies of the same file in the same second should not happen (the scanner runs once a
 * minute), but a hand-edited index could produce it. The ordering then falls back to size and
 * then to hash, so the result is at least deterministic instead of dependent on list order.
 */
public final class Retention {

    private Retention() {}

    /** Newest first; stable for identical timestamps. */
    private static final Comparator<BackupVersion> NEWEST_FIRST =
            Comparator.comparingLong(BackupVersion::seconds).reversed()
                    .thenComparing(Comparator.comparingLong(BackupVersion::size).reversed())
                    .thenComparing(v -> v.hash() == null ? "" : v.hash());

    /**
     * Copies that must be deleted to respect the depth limit.
     *
     * @param depth how many copies of each file to keep; {@code <= 0} means "keep none" and is
     *              treated as 1 — deleting every copy of a file would turn a backup tool into a
     *              data loss tool, which is not a depth setting anyone means to express
     * @return copies to delete, oldest first, so a partially completed prune always leaves the
     *         newest copies behind
     */
    public static List<BackupVersion> toDelete(List<BackupVersion> all, int depth) {
        int keep = Math.max(1, depth);
        if (all == null || all.isEmpty()) return List.of();

        Map<String, List<BackupVersion>> byPath = groupByPath(all);
        List<BackupVersion> doomed = new ArrayList<>();

        for (List<BackupVersion> versions : byPath.values()) {
            versions.sort(NEWEST_FIRST);
            for (int i = keep; i < versions.size(); i++) doomed.add(versions.get(i));
        }

        // Oldest first: the caller may be deleting to free space and can stop early, and stopping
        // early should always mean "we still have the recent ones".
        doomed.sort(Comparator.comparingLong(BackupVersion::seconds));
        return List.copyOf(doomed);
    }

    /** Copies left after applying {@link #toDelete}. Used by the storage report. */
    public static int countAfterPrune(List<BackupVersion> all, int depth) {
        return Math.max(0, (all == null ? 0 : all.size()) - toDelete(all, depth).size());
    }

    static Map<String, List<BackupVersion>> groupByPath(List<BackupVersion> all) {
        Map<String, List<BackupVersion>> byPath = new LinkedHashMap<>();
        for (BackupVersion v : all) {
            byPath.computeIfAbsent(v.path(), k -> new ArrayList<>()).add(v);
        }
        return byPath;
    }
}

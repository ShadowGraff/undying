package com.undying.core.backup;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Decides whether a new copy fits, and what to throw away to make it fit.
 *
 * <h3>Two rules, and they are not the same rule</h3>
 * <ol>
 *   <li><b>Depth</b> — never more than N copies of one file, whether or not space is short.
 *       This is what keeps the folder from growing forever on a long-lived world.</li>
 *   <li><b>Budget</b> — the whole backup folder must stay under the player's limit. This only
 *       bites on big worlds, and it may delete copies the depth rule was happy to keep.</li>
 * </ol>
 *
 * <h3>The last copy of a file is never deleted</h3>
 * If the budget cannot be met without emptying a file's entire history, the pruning stops there
 * and reports {@code fits == false}. The caller must then refuse to write and tell the player.
 * The alternative — quietly deleting the last copy to make room — would let the backup tool
 * destroy the very thing it exists to protect, which is the one failure this module must never
 * have. {@code fits == false} is a loud, recoverable outcome; deleting the last copy is not.
 *
 * <p>Deletion order is oldest-first across all files, so a partially applied prune always leaves
 * the most recent history intact.
 */
public final class StorageBudget {

    private StorageBudget() {}

    /**
     * @param delete        copies to remove, oldest first
     * @param freedBytes    how much space those deletions free
     * @param projectedBytes usage after the deletions and the new copy
     * @param fits          whether the new copy can be written
     */
    public record Decision(List<BackupVersion> delete, long freedBytes, long projectedBytes,
                           boolean fits) {

        public static Decision nothing(long used) {
            return new Decision(List.of(), 0L, used, true);
        }
    }

    /**
     * @param usedBytes     current size of the backup folder
     * @param limitBytes    the player's ceiling; {@code <= 0} means "no ceiling"
     * @param incomingBytes size of the copy about to be written
     * @param all           every known copy
     * @param depth         per-file retention depth
     */
    public static Decision plan(long usedBytes, long limitBytes, long incomingBytes,
                                List<BackupVersion> all, int depth) {
        long used = Math.max(0L, usedBytes);
        long incoming = Math.max(0L, incomingBytes);
        if (all == null || all.isEmpty()) {
            long projected = used + incoming;
            return new Decision(List.of(), 0L, projected, limitBytes <= 0 || projected <= limitBytes);
        }

        List<BackupVersion> deletes = new ArrayList<>();
        long projected = used + incoming;

        // ── Rule 1: depth ──
        for (BackupVersion v : Retention.toDelete(all, depth)) {
            deletes.add(v);
            projected -= v.size();
        }

        // ── Rule 2: budget, keeping at least one copy of each file ──
        if (limitBytes > 0 && projected > limitBytes) {
            Map<String, Integer> remaining = new HashMap<>();
            for (BackupVersion v : all) remaining.merge(v.path(), 1, Integer::sum);
            for (BackupVersion v : deletes) remaining.merge(v.path(), -1, Integer::sum);

            List<BackupVersion> candidates = new ArrayList<>();
            for (BackupVersion v : all) if (!deletes.contains(v)) candidates.add(v);
            candidates.sort(Comparator.comparingLong(BackupVersion::seconds));

            for (BackupVersion v : candidates) {
                if (projected <= limitBytes) break;
                int left = remaining.getOrDefault(v.path(), 0);
                if (left <= 1) continue;         // the last copy of this file stays
                remaining.put(v.path(), left - 1);
                deletes.add(v);
                projected -= v.size();
            }
        }

        deletes.sort(Comparator.comparingLong(BackupVersion::seconds));
        long freed = 0L;
        for (BackupVersion v : deletes) freed += v.size();

        boolean fits = limitBytes <= 0 || projected <= limitBytes;
        return new Decision(List.copyOf(deletes), freed, Math.max(0L, projected), fits);
    }
}

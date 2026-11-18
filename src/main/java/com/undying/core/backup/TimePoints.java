package com.undying.core.backup;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TreeSet;

/**
 * The list of moments a player can roll back to.
 *
 * <h3>Why not "one entry per backup run"</h3>
 * A run touches a handful of files at slightly different seconds, and a list of thirty entries
 * two seconds apart is not a choice, it is noise. Moments closer together than
 * {@code minGapSeconds} collapse into the newest one, which is also the one that restores the
 * most files.
 *
 * <h3>Why the newest is kept when collapsing</h3>
 * Rolling back to the later of two nearly-identical moments loses less and restores at least as
 * much — it dominates the earlier one on both counts, so offering both would only invite the
 * player to pick the worse option.
 */
public final class TimePoints {

    private TimePoints() {}

    /**
     * @param all           every known copy
     * @param max           cap on how many moments to offer
     * @param minGapSeconds moments closer than this collapse into the newer one
     * @return moments, newest first
     */
    public static List<Long> list(List<BackupVersion> all, int max, long minGapSeconds) {
        if (all == null || all.isEmpty() || max <= 0) return List.of();

        // Descending, distinct.
        TreeSet<Long> distinct = new TreeSet<>(Collections.reverseOrder());
        for (BackupVersion v : all) distinct.add(v.seconds());

        List<Long> out = new ArrayList<>(Math.min(max, distinct.size()));
        long gap = Math.max(0L, minGapSeconds);
        Long last = null;

        for (Long moment : distinct) {
            if (last != null && (last - moment) < gap) continue;   // collapse into the newer one
            out.add(moment);
            last = moment;
            if (out.size() >= max) break;
        }
        return List.copyOf(out);
    }
}

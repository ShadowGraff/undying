package com.undying.core.backup;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * "If I roll back to time T, which copy does each file get?"
 *
 * <h3>Why this is the most dangerous class in the module</h3>
 * Every other part of the save rescuer either copies bytes or shows a screen. This one decides
 * <b>which bytes</b>. A bug here does not fail loudly — it produces a world that loads, looks
 * plausible, and is silently a mixture of two different moments. That is worse than a failed
 * restore, because the player will keep playing on it.
 *
 * <p>So it is a pure function of (index, moment, files on disk), it has no file system access,
 * and it is tested against the awkward cases rather than the happy one.
 *
 * <h3>The three outcomes</h3>
 * <ul>
 *   <li><b>RESTORED</b> — a copy exists at or before T; that copy wins even if the file no longer
 *       exists on disk (deleting a region file is one way a world breaks).</li>
 *   <li><b>KEPT_CURRENT</b> — the file exists now but has no copy old enough. It either appeared
 *       after T or was never backed up. Keeping it is the only non-destructive choice, and the
 *       report says so instead of hiding it.</li>
 *   <li><b>IGNORED</b> — copies exist only <i>after</i> T and the file is gone now. Restoring T
 *       means that file did not exist yet, so resurrecting it would invent data. Nothing to do,
 *       but it is counted so the report can be exact.</li>
 * </ul>
 *
 * <p>Nothing is deleted by the restore. A file that a rollback "removes" is simply left alone.
 */
public final class RestorePlan {

    private RestorePlan() {}

    public enum Source {
        /** Take the stored copy. */
        RESTORED,
        /** No copy old enough; the file on disk stays as it is. */
        KEPT_CURRENT
    }

    public record Entry(String path, BackupVersion version, Source source) {
        public boolean restored() {
            return source == Source.RESTORED;
        }
    }

    /**
     * @param entries      one entry per file involved, sorted by path
     * @param restored     files that will be written from a copy
     * @param keptCurrent  files that will be left exactly as they are
     * @param ignored      files that only have later copies and no longer exist
     * @param regionRestored restored files under {@code region/}
     */
    public record Plan(List<Entry> entries, int restored, int keptCurrent, int ignored,
                       int regionRestored) {

        /** True when there is nothing to write — the moment predates every copy. */
        public boolean empty() {
            return restored == 0;
        }

        /**
         * One sentence for the confirmation screen.
         *
         * <p>Deliberately includes the number of files that will <i>not</i> be rolled back: a
         * restore that silently keeps half the world current is the failure mode players would
         * never think to check for.
         */
        public String describeZh() {
            StringBuilder sb = new StringBuilder();
            sb.append("将恢复 ").append(restored).append(" 个文件");
            if (regionRestored > 0) sb.append("（其中 ").append(regionRestored).append(" 个区块文件）");
            if (keptCurrent > 0) {
                sb.append("；").append(keptCurrent).append(" 个文件没有对应版本，保持现状");
            }
            if (ignored > 0) {
                sb.append("；").append(ignored).append(" 个文件在该时间点尚不存在，不做处理");
            }
            return sb.toString();
        }

        public String describeEn() {
            StringBuilder sb = new StringBuilder();
            sb.append(restored).append(" file(s) restored");
            if (regionRestored > 0) sb.append(" (").append(regionRestored).append(" region)");
            if (keptCurrent > 0) {
                sb.append("; ").append(keptCurrent).append(" kept as they are (no copy that old)");
            }
            if (ignored > 0) {
                sb.append("; ").append(ignored).append(" did not exist at that moment, left alone");
            }
            return sb.toString();
        }
    }

    /**
     * Build the plan.
     *
     * @param all         every known version, any order
     * @param atSeconds   the moment being restored to
     * @param filesOnDisk relative paths that currently exist, {@code /}-separated
     */
    public static Plan plan(List<BackupVersion> all, long atSeconds, Set<String> filesOnDisk) {
        Map<String, BackupVersion> best = new HashMap<>();
        Map<String, Boolean> hasLaterOnly = new HashMap<>();

        for (BackupVersion v : all) {
            if (v.atOrBefore(atSeconds)) {
                BackupVersion current = best.get(v.path());
                // Newest wins; ties cannot happen in practice (one file, one second, one write)
                // but the rule is stated so a hand-edited index cannot make it ambiguous.
                if (current == null || v.seconds() > current.seconds()) best.put(v.path(), v);
            } else {
                hasLaterOnly.put(v.path(), Boolean.TRUE);
            }
        }

        Set<String> paths = new TreeSet<>(best.keySet());
        paths.addAll(filesOnDisk == null ? Set.of() : filesOnDisk);
        paths.addAll(hasLaterOnly.keySet());

        List<Entry> entries = new ArrayList<>(paths.size());
        int restored = 0, kept = 0, ignored = 0, region = 0;

        for (String path : paths) {
            BackupVersion v = best.get(path);
            if (v != null) {
                entries.add(new Entry(path, v, Source.RESTORED));
                restored++;
                if (SafePath.isRegionFile(path)) region++;
            } else if (filesOnDisk != null && filesOnDisk.contains(path)) {
                entries.add(new Entry(path, null, Source.KEPT_CURRENT));
                kept++;
            } else {
                ignored++;   // only later copies, and the file is gone: nothing to do
            }
        }

        entries.sort(Comparator.comparing(Entry::path));
        return new Plan(List.copyOf(entries), restored, kept, ignored, region);
    }

    /**
     * How much progress a rollback to T would lose, in seconds.
     *
     * <p>Computed from the newest copy that exists rather than from "now", because the honest
     * answer to "how much will I lose" is "everything since the last thing we managed to save",
     * and those differ whenever backups have been failing.
     */
    public static long lossSeconds(List<BackupVersion> all, long atSeconds) {
        long newest = 0L;
        for (BackupVersion v : all) newest = Math.max(newest, v.seconds());
        if (newest == 0L) return 0L;
        return Math.max(0L, newest - atSeconds);
    }
}

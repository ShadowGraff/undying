package com.undying.core.backup;

/**
 * Which paths inside a world are not worth copying.
 *
 * <h3>Where this list comes from</h3>
 * Every backup tool that people actually run on live worlds has one, and the contents are always
 * the same. The two entries that matter here were found the hard way by other projects:
 *
 * <ul>
 *   <li><b>{@code session.lock}</b> — the running game holds an OS lock on it. On Windows,
 *       opening it for read while locked throws a sharing violation, so copying it is not merely
 *       useless, it is a guaranteed failure in the middle of a backup run. Its content is a
 *       number that vanilla rewrites on every open, so nothing is lost by skipping it.</li>
 *   <li><b>our own backup folder</b> — without this the scanner would happily back up the
 *       backups, and the folder would grow by a copy of itself every minute. A backup tool that
 *       eats the disk it was given is a classic way to lose a world.</li>
 * </ul>
 *
 * <p>{@code level.dat_old} is deliberately <b>kept</b>: it is vanilla's own previous copy, and it
 * is the first thing a player reaches for when {@code level.dat} is unreadable. Excluding it
 * would remove the cheapest rescue available.
 *
 * <h3>Why a whitelist-shaped check and not just these three strings</h3>
 * The rule is "copy the world, minus known-live or self-generated files". Writing it as a small
 * set of predicates keeps it testable and keeps the scanner from growing opinions inline.
 */
public final class BackupFilter {

    private BackupFilter() {}

    /** Files that are live handles or self-generated, checked by file name. */
    private static final String[] EXCLUDED_NAMES = {
            "session.lock"
    };

    /** Directories that must never be descended into. */
    private static final String[] EXCLUDED_DIRS = {
            "_undying_backup",
            ".ds_store"
    };

    /**
     * @param relativePath path relative to the world directory, {@code /}-separated
     * @return true when the scanner must skip this path
     */
    public static boolean isExcluded(String relativePath) {
        if (relativePath == null || relativePath.isEmpty()) return true;
        if (!SafePath.isSafe(relativePath)) return true;   // nonsense path: not ours to copy

        String lower = relativePath.toLowerCase(java.util.Locale.ROOT);
        String name = lower.substring(lower.lastIndexOf('/') + 1);

        for (String excluded : EXCLUDED_NAMES) {
            if (name.equals(excluded)) return true;
        }
        // Any lock file behaves the same way; there is no case where copying a live lock helps.
        if (name.endsWith(".lock")) return true;

        for (String dir : EXCLUDED_DIRS) {
            if (lower.equals(dir) || lower.startsWith(dir + "/")) return true;
        }
        return false;
    }

    /**
     * Whether this file is worth a storage slot at all.
     *
     * <p>Zero-byte files are the remains of an interrupted write. Backing them up preserves
     * nothing, and restoring one would recreate exactly the broken state the player is trying to
     * escape.
     */
    public static boolean isWorthCopying(String relativePath, long size) {
        return !isExcluded(relativePath) && size > 0L;
    }
}

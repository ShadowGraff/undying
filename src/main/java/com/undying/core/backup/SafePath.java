package com.undying.core.backup;

/**
 * Validation for the relative paths written into {@code index.json}.
 *
 * <h3>Why a restore path needs this at all</h3>
 * The restore step takes a path <b>from a file on disk</b> and joins it to the world directory.
 * That index is a plain JSON file a player (or another mod, or a half-written write) can put
 * anything into — including {@code ../../.minecraft/options.txt}. A restore is the one place in
 * this mod that writes to arbitrary files, so "the input is not hostile" has to be proven rather
 * than assumed.
 *
 * <p>The rules are deliberately a whitelist of the shape a Minecraft world actually uses, not a
 * blacklist of strings that look dangerous: {@code ..\..\x} is equally dangerous on Windows and
 * would slip past a check that only strips forward slashes.
 *
 * <h3>What a legal path looks like</h3>
 * <pre>
 *   level.dat
 *   region/r.0.0.mca
 *   DIM-1/region/r.0.0.mca
 *   data/raids.dat
 * </pre>
 * No leading separator, no drive letter, no backslash, no {@code .} or {@code ..} segment, no
 * empty segment, no NUL, and no segment longer than 255 characters (the Windows limit that would
 * otherwise fail the write halfway through a restore).
 */
public final class SafePath {

    private SafePath() {}

    /** Longest single path segment the common filesystems accept. */
    private static final int MAX_SEGMENT = 255;

    /** Longest total path this mod will write. */
    private static final int MAX_TOTAL = 1024;

    /**
     * @return true when the relative path is safe to join to the world directory
     */
    public static boolean isSafe(String path) {
        if (path == null || path.isEmpty()) return false;
        if (path.length() > MAX_TOTAL) return false;

        // Backslash is a separator on Windows: "..\\.." must be rejected, not treated as one
        // odd-looking segment. Normalising it away first would silently accept it.
        if (path.indexOf('\\') >= 0) return false;
        if (path.indexOf('\0') >= 0) return false;

        // Absolute in any of the three shapes that matter here.
        if (path.charAt(0) == '/') return false;
        if (path.length() >= 2 && path.charAt(1) == ':') return false;

        int start = 0;
        while (true) {
            int slash = path.indexOf('/', start);
            String segment = slash < 0 ? path.substring(start) : path.substring(start, slash);

            if (segment.isEmpty()) return false;                      // "a//b", trailing "/"
            if (segment.equals(".") || segment.equals("..")) return false;
            if (segment.length() > MAX_SEGMENT) return false;

            if (slash < 0) break;
            start = slash + 1;
        }
        return true;
    }

    /**
     * Whether the path points at a region file.
     *
     * <p>Used by the report to say "3 个区块文件会退回旧版" instead of listing eight paths; the
     * rule lives here so the report and the storage layer cannot disagree about what counts.
     */
    public static boolean isRegionFile(String path) {
        return isSafe(path) && path.startsWith("region/") && path.endsWith(".mca");
    }
}

package com.undying.core.backup;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Folder names a human can read.
 *
 * <h3>Why not just unix seconds</h3>
 * Every mature backup tool names its folders {@code 2026-11-18_22-30-05}. The reason is not
 * cosmetic: the folder ends up in a file manager, opened months later, by a player who is already
 * having a bad day. "1731953405" answers no question they are asking. The index keeps unix
 * seconds (sortable, unambiguous); the folder name is for the person.
 *
 * <h3>Why the zone is a parameter</h3>
 * A pure function that reads the system clock cannot be tested, and a backup tool whose naming
 * is untested is one that produces {@code 2026-11-18_25-61-00} once and then nobody trusts it.
 * Callers pass {@code ZoneId.systemDefault()}; tests pass a fixed one.
 *
 * <p>Colons are avoided on purpose — they are illegal in Windows file names, which is the kind
 * of detail that only shows up as "the backup silently did not happen".
 */
public final class HumanStamp {

    private HumanStamp() {}

    private static final DateTimeFormatter FOLDER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    /** e.g. {@code 2026-11-18_22-30-05}. */
    public static String folder(long unixSeconds, ZoneId zone) {
        return FOLDER.format(Instant.ofEpochSecond(unixSeconds).atZone(zone));
    }

    /** The same moment, for a chat line: {@code 2026-11-18 22:30}. */
    public static String shortLine(long unixSeconds, ZoneId zone) {
        return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                .format(Instant.ofEpochSecond(unixSeconds).atZone(zone));
    }

    /**
     * How long ago, in the words a player would use.
     *
     * <p>Seconds are dropped once there are minutes, and hours once there is a day: "3 天前" is
     * what someone wants to know before choosing a rollback point; "3 天 4 小时 12 分前" is what
     * makes them stop reading.
     */
    public static String ago(long secondsAgo) {
        long s = Math.max(0L, secondsAgo);
        if (s < 60) return s + " 秒前";
        long m = s / 60;
        if (m < 60) return m + " 分钟前";
        long h = m / 60;
        if (h < 24) return h + " 小时前";
        return (h / 24) + " 天前";
    }

    public static String agoEn(long secondsAgo) {
        long s = Math.max(0L, secondsAgo);
        if (s < 60) return s + "s ago";
        long m = s / 60;
        if (m < 60) return m + "m ago";
        long h = m / 60;
        if (h < 24) return h + "h ago";
        return (h / 24) + "d ago";
    }
}

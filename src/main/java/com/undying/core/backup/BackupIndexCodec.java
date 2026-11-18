package com.undying.core.backup;

import java.util.ArrayList;
import java.util.List;

/**
 * The index file: one line per copy, tab separated, plain text.
 *
 * <h3>Why not JSON</h3>
 * Three reasons, in order of weight:
 * <ol>
 *   <li><b>No parser dependency.</b> The test suite deliberately runs without Forge, so anything
 *       the index needs must be plain Java. A hand-written JSON parser would be a liability, and
 *       adding Gson to the test classpath would undo the "tests run with no game" property.</li>
 *   <li><b>Recoverable by hand.</b> Every file this mod writes stays something a player can open
 *       in Notepad and fix. A truncated line loses one record; a truncated JSON document loses
 *       all of them.</li>
 *   <li><b>Diffable.</b> Two runs produce a readable diff, which is how a bug in the scanner gets
 *       caught instead of argued about.</li>
 * </ol>
 *
 * <h3>Tolerance rules</h3>
 * A malformed line is skipped, not fatal: the index is written by an older version of this mod as
 * often as by the current one, and refusing to load it would turn a cosmetic version skew into
 * "all your backups are invisible". Skipped lines are counted by {@link Decoded#skipped()} so the
 * caller can report them rather than pretend the file was clean.
 *
 * <p>Tabs are the separator because a path may contain spaces; a path cannot contain a tab on any
 * filesystem this mod supports.
 */
public final class BackupIndexCodec {

    private BackupIndexCodec() {}

    /** Present so a future format change can be detected instead of misread. */
    public static final String HEADER = "undying-backup-index v1";

    private static final char SEP = '\t';
    private static final String NO_HASH = "-";

    public record Decoded(List<BackupVersion> versions, int skipped) {}

    public static String encode(List<BackupVersion> versions) {
        StringBuilder sb = new StringBuilder(HEADER).append('\n');
        if (versions != null) {
            for (BackupVersion v : versions) {
                sb.append(v.path()).append(SEP)
                        .append(v.seconds()).append(SEP)
                        .append(v.size()).append(SEP)
                        .append(v.hasHash() ? v.hash() : NO_HASH)
                        .append('\n');
            }
        }
        return sb.toString();
    }

    public static Decoded decode(String text) {
        if (text == null || text.isEmpty()) return new Decoded(List.of(), 0);

        List<BackupVersion> out = new ArrayList<>();
        int skipped = 0;
        boolean first = true;

        for (String raw : text.split("\n", -1)) {
            String line = raw.stripTrailing();
            if (line.isEmpty()) continue;

            if (first) {
                first = false;
                // A missing header is tolerated (an early build wrote none); a *different*
                // header is not, because the field order could have changed. Refusing the whole
                // document is the safe direction: reading v2 fields as v1 would put wrong
                // timestamps on copies, and a wrong timestamp silently restores the wrong moment.
                if (line.equals(HEADER)) continue;
                if (line.startsWith("undying-backup-index")) {
                    return new Decoded(List.of(), 1);
                }
            }

            BackupVersion parsed = parseLine(line);
            if (parsed == null) skipped++;
            else out.add(parsed);
        }
        return new Decoded(List.copyOf(out), skipped);
    }

    private static BackupVersion parseLine(String line) {
        int a = line.indexOf(SEP);
        if (a <= 0) return null;
        int b = line.indexOf(SEP, a + 1);
        if (b <= a) return null;
        int c = line.indexOf(SEP, b + 1);
        if (c <= b) return null;

        try {
            String path = line.substring(0, a);
            if (!SafePath.isSafe(path)) return null;

            long seconds = Long.parseLong(line.substring(a + 1, b));
            long size = Long.parseLong(line.substring(b + 1, c));

            String hash = line.substring(c + 1);
            if (NO_HASH.equals(hash) || hash.isEmpty()) hash = null;

            return new BackupVersion(path, seconds, size, hash);
        } catch (NumberFormatException | IndexOutOfBoundsException e) {
            return null;
        }
    }
}

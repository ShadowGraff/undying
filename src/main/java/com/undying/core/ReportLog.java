package com.undying.core;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * The mod's own rolling report log.
 *
 * <p>Why a separate file instead of the chat box or {@code debug.log}:
 * <ul>
 *   <li>The chat box has no history management — every world entry appended another block
 *       of lines until the whole screen was mod output.</li>
 *   <li>{@code debug.log} belongs to Log4j2 and to every other mod; writing our reports
 *       there means they scroll away, and changing its rolling policy would change it for
 *       everyone.</li>
 * </ul>
 *
 * <h3>Layout</h3>
 * <pre>
 *   logs/undying/report.log      newest
 *   logs/undying/report-1.log    previous
 *   ...
 *   logs/undying/report-6.log    oldest kept
 * </pre>
 * Seven files, and when the newest would exceed {@link #MAX_CHARS} characters it becomes
 * {@code report-1.log}, every older file shifts down one slot, and the oldest is deleted.
 * So there are always at most seven files and never an unbounded one.
 *
 * <p>Characters rather than bytes: the limit is meant to be "how much can a human read in
 * one file", and a Chinese character costs three bytes but reads like one character.
 *
 * <p>All methods are {@code synchronized}: the report is written from the server thread at
 * world join, and a second entry can happen while the first is still writing.
 */
public final class ReportLog {

    private ReportLog() {}

    /** Newest file name. */
    private static final String BASE = "report.log";

    /** report.log + report-1 .. report-6 */
    private static final int MAX_SLOTS = 7;

    /** Characters per file (the user's spec: 12000). */
    private static final int MAX_CHARS = 12_000;

    /** The directory holding the reports. Created on demand. */
    public static File directory() {
        return net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get()
                .resolve("logs").resolve("undying").toFile();
    }

    /** Append one line. */
    public static synchronized void write(String line) {
        writeBlock(line);
    }

    /**
     * Append a multi-line block, rolling first if it would push the file over the limit.
     *
     * <p>Never throws: a report is diagnostic output, and losing it must not break the
     * game. Failures are still recorded through {@link ModLog}.
     */
    public static synchronized void writeBlock(String block) {
        writeBlockTo(directory(), block, MAX_CHARS);
    }

    /**
     * The whole write path, with the directory and the limit passed in.
     *
     * <p>Split out so the rolling behaviour can be unit-tested against a temporary
     * directory: {@link #directory()} touches Forge's game directory, which does not exist
     * on a bare test classpath, and rolling order is exactly the kind of logic that is easy
     * to get backwards (which would silently lose the whole history).
     */
    static synchronized void writeBlockTo(File dir, String block, int maxChars) {
        if (block == null || block.isEmpty()) return;
        if (maxChars <= 0) return;
        try {
            if (dir == null) return;
            if (!dir.isDirectory() && !dir.mkdirs()) return;

            File current = new File(dir, BASE);
            String text = block.endsWith("\n") ? block : block + System.lineSeparator();

            // A single block larger than the whole file would otherwise loop forever.
            if (text.length() > maxChars) {
                text = text.substring(0, Math.max(0, maxChars - 24))
                        + System.lineSeparator() + "... (truncated)";
            }

            int existing = 0;
            if (current.isFile()) {
                try {
                    existing = Files.readString(current.toPath()).length();
                } catch (Throwable t) {
                    ModLog.swallowed("ReportLog.count", t);
                }
            }

            if (existing > 0 && existing + text.length() > maxChars) {
                roll(dir);
            }

            Files.writeString(current.toPath(), text,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Throwable t) {
            ModLog.swallowed("ReportLog.writeBlock", t);
        }
    }

    /**
     * Shift every file down one slot and delete the oldest.
     *
     * <p>Done oldest-first so a failure mid-way leaves the older history intact rather than
     * overwriting it with a newer file.
     */
    private static void roll(File dir) {
        try {
            File oldest = new File(dir, "report-" + (MAX_SLOTS - 1) + ".log");
            if (oldest.isFile()) Files.deleteIfExists(oldest.toPath());

            for (int i = MAX_SLOTS - 2; i >= 1; i--) {
                File from = new File(dir, "report-" + i + ".log");
                if (!from.isFile()) continue;
                File to = new File(dir, "report-" + (i + 1) + ".log");
                Files.move(from.toPath(), to.toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }

            File current = new File(dir, BASE);
            if (current.isFile()) {
                Files.move(current.toPath(), new File(dir, "report-1.log").toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Throwable t) {
            ModLog.swallowed("ReportLog.roll", t);
        }
    }

    /** Number of files currently on disk, for the self-check and tests. */
    public static synchronized int fileCount() {
        try {
            File dir = directory();
            if (!dir.isDirectory()) return 0;
            File[] files = dir.listFiles((d, n) -> n.startsWith("report") && n.endsWith(".log"));
            return files == null ? 0 : files.length;
        } catch (Throwable t) {
            ModLog.swallowed("ReportLog.fileCount", t);
            return 0;
        }
    }

    /** Character limit per file, exposed so the command can print it. */
    public static int maxChars() {
        return MAX_CHARS;
    }

    /** Path for display in chat. */
    public static String displayPath() {
        try {
            Path p = directory().toPath();
            return p.toAbsolutePath().toString();
        } catch (Throwable t) {
            return "logs/undying/";
        }
    }
}

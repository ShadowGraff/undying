package com.undying.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the rolling report log.
 *
 * <p>Rolling order is the dangerous part: a loop that shifts 1→2 before 2→3, or that deletes
 * before shifting, destroys the history it was supposed to keep. These tests pin the order.
 */
class ReportLogTest {

    @TempDir
    File dir;

    private String read(String name) throws Exception {
        File f = new File(dir, name);
        return f.isFile() ? Files.readString(f.toPath()) : null;
    }

    @Test
    void createsTheDirectoryOnDemand() {
        File nested = new File(dir, "a/b/c");
        ReportLog.writeBlockTo(nested, "hello", 1000);
        assertTrue(new File(nested, "report.log").isFile());
    }

    @Test
    void appendsWithinTheLimit() throws Exception {
        ReportLog.writeBlockTo(dir, "first", 1000);
        ReportLog.writeBlockTo(dir, "second", 1000);
        String content = read("report.log");
        assertTrue(content.contains("first"), content);
        assertTrue(content.contains("second"), content);
        assertFalse(new File(dir, "report-1.log").exists());
    }

    @Test
    void rollsWhenTheLimitWouldBeExceeded() throws Exception {
        ReportLog.writeBlockTo(dir, "aaaaaaaaaa", 20);   // 11 chars including newline
        ReportLog.writeBlockTo(dir, "bbbbbbbbbb", 20);   // 11 + 11 = 22 > 20 → roll first
        assertTrue(new File(dir, "report-1.log").isFile());
        assertTrue(read("report.log").contains("bbbbbbbbbb"));
        assertTrue(read("report-1.log").contains("aaaaaaaaaa"));
    }

    @Test
    void keepsAtMostSevenFilesAndDropsTheOldest() throws Exception {
        // Each block is 13 chars ("entry19xxxxx" + newline), and the limit is 16 so a single
        // block always fits — otherwise the truncation path would fire instead of the rolling
        // path and the test would be measuring the wrong thing.
        for (int i = 0; i < 20; i++) {
            ReportLog.writeBlockTo(dir, "entry" + i + "xxxxx", 16);
        }

        String[] logs = dir.list((d, n) -> n.startsWith("report") && n.endsWith(".log"));
        assertTrue(logs != null && logs.length <= 7, "too many files: " + Arrays.toString(logs));
        assertTrue(new File(dir, "report.log").isFile());
        // The very first entry must be gone by now, the newest must be present.
        assertFalse(read("report.log").contains("entry0"), "oldest survived in the newest file");
        assertTrue(read("report.log").contains("entry19"), read("report.log"));
    }

    @Test
    void shiftsOlderFilesDownInsteadOfOverwritingThem() throws Exception {
        ReportLog.writeBlockTo(dir, "gen-one", 12);     // 8 chars, stays as report.log
        ReportLog.writeBlockTo(dir, "gen-two", 12);     // 8 + 8 > 12 → gen-one to report-1
        ReportLog.writeBlockTo(dir, "gen-three", 12);   // rolls again → gen-one to report-2

        // gen-one must still exist somewhere in the chain, not be silently replaced.
        boolean found = false;
        for (int i = 1; i <= 6; i++) {
            String c = read("report-" + i + ".log");
            if (c != null && c.contains("gen-one")) found = true;
        }
        if (read("report.log").contains("gen-one")) found = true;
        assertTrue(found, "the oldest generation was lost during roll");
    }

    @Test
    void truncatesABlockLargerThanTheWholeFile() throws Exception {
        StringBuilder huge = new StringBuilder();
        for (int i = 0; i < 500; i++) huge.append('x');
        ReportLog.writeBlockTo(dir, huge.toString(), 100);

        String content = read("report.log");
        assertTrue(content.length() <= 100, "block was not truncated: " + content.length());
        assertTrue(content.contains("truncated") || content.length() == 100);
    }

    @Test
    void ignoresEmptyInputAndSillyLimits() {
        ReportLog.writeBlockTo(dir, "", 100);
        ReportLog.writeBlockTo(dir, null, 100);
        ReportLog.writeBlockTo(dir, "text", 0);
        assertFalse(new File(dir, "report.log").exists());
    }

    @Test
    void aBlockThatExactlyFitsDoesNotRoll() throws Exception {
        ReportLog.writeBlockTo(dir, "12345", 6);   // 5 chars + newline = 6
        assertTrue(new File(dir, "report.log").isFile());
        assertFalse(new File(dir, "report-1.log").exists());
    }

    @Test
    void maxCharsIsTwelveThousand() {
        assertEquals(12000, ReportLog.maxChars());
    }
}

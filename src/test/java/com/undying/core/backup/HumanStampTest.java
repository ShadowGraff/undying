package com.undying.core.backup;

import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** Folder naming and the "how long ago" line. */
class HumanStampTest {

    private static final ZoneId UTC = ZoneId.of("UTC");

    @Test
    void folderNamesAreReadableAndWindowsSafe() {
        long sec = ZonedDateTime.of(2026, 11, 18, 22, 30, 5, 0, UTC).toEpochSecond();
        String name = HumanStamp.folder(sec, UTC);

        assertEquals("2026-11-18_22-30-05", name);
        // Colons are illegal in Windows file names; that detail has silently stopped backups.
        assertFalse(name.contains(":"));
    }

    @Test
    void theZoneActuallyMatters() {
        long sec = ZonedDateTime.of(2026, 11, 18, 22, 30, 5, 0, UTC).toEpochSecond();
        assertEquals("2026-11-18_22-30-05", HumanStamp.folder(sec, UTC));
        assertEquals("2026-11-19_06-30-05", HumanStamp.folder(sec, ZoneId.of("Asia/Shanghai")));
    }

    @Test
    void shortLineDropsSeconds() {
        long sec = ZonedDateTime.of(2026, 11, 18, 22, 30, 5, 0, UTC).toEpochSecond();
        assertEquals("2026-11-18 22:30", HumanStamp.shortLine(sec, UTC));
    }

    @Test
    void agoUsesTheCoarsestUsefulUnit() {
        assertEquals("0 秒前", HumanStamp.ago(0));
        assertEquals("59 秒前", HumanStamp.ago(59));
        assertEquals("1 分钟前", HumanStamp.ago(60));
        assertEquals("59 分钟前", HumanStamp.ago(59 * 60));
        assertEquals("1 小时前", HumanStamp.ago(60 * 60));
        assertEquals("23 小时前", HumanStamp.ago(23 * 3600));
        assertEquals("1 天前", HumanStamp.ago(24 * 3600));
        assertEquals("3 天前", HumanStamp.ago(3 * 24 * 3600));
    }

    @Test
    void negativeTimeIsClampedNotPrintedAsMinus() {
        assertEquals("0 秒前", HumanStamp.ago(-500));
        assertEquals("0s ago", HumanStamp.agoEn(-500));
    }
}

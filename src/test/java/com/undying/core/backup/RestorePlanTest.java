package com.undying.core.backup;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which copy each file gets when rolling back to a moment.
 *
 * <p>The cases below are the ones that produce a <b>silently mixed</b> world if they are wrong,
 * which is the only failure mode in this module worse than failing outright.
 */
class RestorePlanTest {

    private static BackupVersion v(String path, long sec, long size) {
        return new BackupVersion(path, sec, size, null);
    }

    @Test
    void picksTheNewestCopyAtOrBeforeTheMoment() {
        List<BackupVersion> all = List.of(
                v("level.dat", 100, 10),
                v("level.dat", 200, 11),
                v("level.dat", 300, 12));

        RestorePlan.Plan plan = RestorePlan.plan(all, 250, Set.of("level.dat"));

        assertEquals(1, plan.restored());
        assertEquals(200, plan.entries().get(0).version().seconds());
    }

    @Test
    void aCopyExactlyAtTheMomentCounts() {
        List<BackupVersion> all = List.of(v("level.dat", 200, 11));
        assertEquals(200, RestorePlan.plan(all, 200, Set.of()).entries().get(0).version().seconds());
    }

    @Test
    void filesWithOnlyLaterCopiesKeepTheirCurrentContent() {
        List<BackupVersion> all = List.of(v("region/r.0.0.mca", 500, 100));
        RestorePlan.Plan plan = RestorePlan.plan(all, 300, Set.of("region/r.0.0.mca"));

        assertEquals(0, plan.restored());
        assertEquals(1, plan.keptCurrent());
        assertEquals(RestorePlan.Source.KEPT_CURRENT, plan.entries().get(0).source());
        assertNull(plan.entries().get(0).version());
        assertTrue(plan.empty());
    }

    @Test
    void aFileThatExistedAtTheMomentButIsGoneNowIsRestored() {
        // Deleting a region file is one of the ways a world breaks; the rollback must bring it
        // back rather than treat the absence as "nothing to do".
        List<BackupVersion> all = List.of(v("region/r.1.2.mca", 100, 4096));
        RestorePlan.Plan plan = RestorePlan.plan(all, 200, Set.of());

        assertEquals(1, plan.restored());
        assertEquals(1, plan.regionRestored());
    }

    @Test
    void aFileCreatedAfterTheMomentIsNotResurrected() {
        // It has copies, but only later ones, and it does not exist now: at that moment it was
        // not part of the world. Writing it back would invent data.
        List<BackupVersion> all = List.of(v("region/r.9.9.mca", 900, 4096));
        RestorePlan.Plan plan = RestorePlan.plan(all, 300, Set.of());

        assertEquals(0, plan.restored());
        assertEquals(0, plan.keptCurrent());
        assertEquals(1, plan.ignored());
        assertTrue(plan.entries().isEmpty());
    }

    @Test
    void aMomentBeforeEveryCopyRestoresNothing() {
        List<BackupVersion> all = List.of(v("level.dat", 500, 10), v("region/r.0.0.mca", 600, 10));
        RestorePlan.Plan plan = RestorePlan.plan(all, 100, Set.of("level.dat"));

        assertEquals(0, plan.restored());
        assertEquals(1, plan.keptCurrent());
        assertTrue(plan.empty());
    }

    @Test
    void theReportNamesWhatWillNotBeRolledBack() {
        List<BackupVersion> all = List.of(
                v("level.dat", 100, 10),
                v("region/r.0.0.mca", 900, 10));

        RestorePlan.Plan plan = RestorePlan.plan(all, 200,
                Set.of("level.dat", "region/r.0.0.mca"));

        String zh = plan.describeZh();
        assertTrue(zh.contains("1 个文件"), zh);
        assertTrue(zh.contains("保持现状"), zh);
    }

    @Test
    void lossIsMeasuredFromTheNewestCopyNotFromNow() {
        // If backups have been failing, the honest "you will lose" is measured against the last
        // thing that was actually saved.
        List<BackupVersion> all = List.of(v("level.dat", 1000, 10), v("level.dat", 8900, 10));
        assertEquals(7900L, RestorePlan.lossSeconds(all, 1000));
    }

    @Test
    void lossIsZeroWhenNothingWasEverBackedUp() {
        assertEquals(0L, RestorePlan.lossSeconds(List.of(), 123));
    }

    @Test
    void entriesAreSortedSoTheFileListingIsStable() {
        List<BackupVersion> all = List.of(
                v("region/r.0.0.mca", 100, 10),
                v("level.dat", 100, 10),
                v("data/raids.dat", 100, 10));

        RestorePlan.Plan plan = RestorePlan.plan(all, 200, Set.of());
        assertEquals("data/raids.dat", plan.entries().get(0).path());
        assertEquals("level.dat", plan.entries().get(1).path());
        assertEquals("region/r.0.0.mca", plan.entries().get(2).path());
        assertFalse(plan.empty());
    }
}

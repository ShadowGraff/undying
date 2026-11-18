package com.undying.core.backup;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Retention depth and the storage budget.
 *
 * <p>The rule that these tests exist to protect: <b>the pruning never empties a file's history
 * and never deletes the last copy</b>. A backup tool that deletes the thing it was protecting is
 * worse than no backup tool at all.
 */
class RetentionTest {

    private static BackupVersion v(String path, long sec, long size) {
        return new BackupVersion(path, sec, size, null);
    }

    @Test
    void keepsTheNewestCopiesAndDropsTheOlderOnes() {
        List<BackupVersion> all = List.of(
                v("level.dat", 100, 1), v("level.dat", 200, 1), v("level.dat", 300, 1),
                v("level.dat", 400, 1), v("level.dat", 500, 1));

        List<BackupVersion> doomed = Retention.toDelete(all, 3);

        assertEquals(2, doomed.size());
        assertEquals(100, doomed.get(0).seconds());
        assertEquals(200, doomed.get(1).seconds());
    }

    @Test
    void depthIsCountedPerFile() {
        List<BackupVersion> all = List.of(
                v("level.dat", 100, 1), v("level.dat", 200, 1),
                v("region/a.mca", 150, 1), v("region/a.mca", 160, 1));

        assertTrue(Retention.toDelete(all, 3).isEmpty(), "nothing exceeds depth 3");
    }

    @Test
    void deletionsComeOutOldestFirstSoAPartialPruneKeepsTheRecentHistory() {
        List<BackupVersion> all = new ArrayList<>();
        for (int i = 1; i <= 5; i++) all.add(v("region/a.mca", i * 100L, 10));

        List<BackupVersion> doomed = Retention.toDelete(all, 2);

        assertEquals(3, doomed.size());
        assertEquals(100, doomed.get(0).seconds());
        assertEquals(300, doomed.get(2).seconds());
    }

    @Test
    void aDepthOfZeroStillKeepsOneCopy() {
        // Deleting every copy would turn a depth setting into data loss.
        List<BackupVersion> all = List.of(v("level.dat", 100, 1), v("level.dat", 200, 1));
        assertEquals(1, Retention.toDelete(all, 0).size());
        assertEquals(1, Retention.toDelete(all, -5).size());
    }

    @Test
    void tiesFallBackToSomethingDeterministic() {
        List<BackupVersion> all = List.of(
                v("level.dat", 100, 5), v("level.dat", 100, 9), v("level.dat", 100, 7));
        // Same second: the largest is kept, the other two go. No dependence on input order.
        List<BackupVersion> doomed = Retention.toDelete(all, 1);
        assertEquals(2, doomed.size());
    }

    // ══════════ StorageBudget ══════════

    @Test
    void aSmallIncomingCopyFitsWithoutDeletingAnything() {
        var d = StorageBudget.plan(100L, 1000L, 50L, List.of(v("level.dat", 100, 20)), 7);
        assertTrue(d.fits());
        assertTrue(d.delete().isEmpty());
        assertEquals(150L, d.projectedBytes());
    }

    @Test
    void depthPruningHappensEvenWhenSpaceIsPlentiful() {
        List<BackupVersion> all = List.of(
                v("level.dat", 100, 10), v("level.dat", 200, 10), v("level.dat", 300, 10));
        var d = StorageBudget.plan(30L, 10_000L, 10L, all, 2);

        assertTrue(d.fits());
        assertEquals(1, d.delete().size());
        assertEquals(100, d.delete().get(0).seconds());
    }

    @Test
    void theBudgetDeletesMoreWhenTheDepthLimitWasNotEnough() {
        List<BackupVersion> all = List.of(
                v("region/a.mca", 100, 100), v("region/a.mca", 200, 100),
                v("region/a.mca", 300, 100));
        // Used 300, incoming 100, limit 250 → must free at least 150 → 2 deletions.
        var d = StorageBudget.plan(300L, 250L, 100L, all, 7);

        assertTrue(d.fits());
        assertEquals(2, d.delete().size());
        assertTrue(d.projectedBytes() <= 250L);
    }

    @Test
    void theLastCopyIsNeverDeletedEvenIfThatBreaksTheBudget() {
        List<BackupVersion> one = List.of(v("region/big.mca", 100, 5000));
        var d = StorageBudget.plan(5000L, 100L, 4000L, one, 7);

        assertTrue(d.delete().isEmpty(), "the only copy must survive");
        assertTrue(!d.fits(), "and the caller must be told it does not fit");
    }

    @Test
    void freeingSpaceStopsAsSoonAsTheBudgetIsMet() {
        List<BackupVersion> all = List.of(
                v("region/a.mca", 100, 100), v("region/a.mca", 200, 100),
                v("region/a.mca", 300, 100), v("region/a.mca", 400, 100));
        // Used 400, incoming 50, limit 400 → only 50 must be freed → exactly one deletion.
        var d = StorageBudget.plan(400L, 400L, 50L, all, 7);

        assertTrue(d.fits());
        assertEquals(1, d.delete().size());
        assertEquals(100, d.delete().get(0).seconds());
    }

    @Test
    void noLimitMeansNoBudgetPressure() {
        List<BackupVersion> all = List.of(v("level.dat", 100, 10));
        var d = StorageBudget.plan(999_999L, 0L, 50L, all, 7);
        assertTrue(d.fits());
    }
}

package com.undying.core.backup;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The list of moments a player can roll back to. */
class TimePointsTest {

    private static BackupVersion v(long sec) {
        return new BackupVersion("level.dat", sec, 1, null);
    }

    @Test
    void newestFirst() {
        List<Long> points = TimePoints.list(List.of(v(100), v(300), v(200)), 10, 0);
        assertEquals(List.of(300L, 200L, 100L), points);
    }

    @Test
    void nearbyMomentsCollapseIntoTheNewerOne() {
        // 100 / 130 / 160 with a 60 s gap: 160 wins over 130, and 100 is far enough to stand.
        List<Long> points = TimePoints.list(List.of(v(100), v(130), v(160)), 10, 60);
        assertEquals(List.of(160L, 100L), points);
    }

    @Test
    void theCapIsRespected() {
        List<BackupVersion> all = List.of(v(100), v(200), v(300), v(400));
        assertEquals(2, TimePoints.list(all, 2, 0).size());
        assertEquals(400L, TimePoints.list(all, 2, 0).get(0));
    }

    @Test
    void duplicatesCollapseEvenWithNoGap() {
        assertEquals(List.of(200L), TimePoints.list(List.of(v(200), v(200)), 10, 0));
    }

    @Test
    void anEmptyIndexHasNoMoments() {
        assertTrue(TimePoints.list(List.of(), 10, 60).isEmpty());
    }

    @Test
    void aNonPositiveCapAsksForNothing() {
        assertTrue(TimePoints.list(List.of(v(100)), 0, 60).isEmpty());
    }
}

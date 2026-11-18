package com.undying.core.backup;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Path validation for the one place in this mod that writes to arbitrary files.
 *
 * <p>The interesting cases are the ones a blacklist would miss: Windows separators, drive
 * letters, and segment tricks. Every rejection here is one restore that cannot be turned into
 * "write outside the save folder".
 */
class SafePathTest {

    @Test
    void ordinaryWorldPathsAreAccepted() {
        assertTrue(SafePath.isSafe("level.dat"));
        assertTrue(SafePath.isSafe("region/r.0.0.mca"));
        assertTrue(SafePath.isSafe("DIM-1/region/r.0.0.mca"));
        assertTrue(SafePath.isSafe("data/raids.dat"));
        assertTrue(SafePath.isSafe("playerdata/01234567-89ab-cdef-0123-456789abcdef.dat"));
    }

    @Test
    void traversalIsRejected() {
        assertFalse(SafePath.isSafe("../options.txt"));
        assertFalse(SafePath.isSafe("region/../../level.dat"));
        assertFalse(SafePath.isSafe(".."));
        assertFalse(SafePath.isSafe("region/.."));
    }

    @Test
    void windowsSeparatorsAreRejectedRatherThanNormalised() {
        // "..\\..\\x" must not slip through a check that only looks for forward slashes.
        assertFalse(SafePath.isSafe("..\\..\\options.txt"));
        assertFalse(SafePath.isSafe("region\\r.0.0.mca"));
    }

    @Test
    void absolutePathsAreRejected() {
        assertFalse(SafePath.isSafe("/etc/passwd"));
        assertFalse(SafePath.isSafe("C:/Windows/System32/drivers/etc/hosts"));
        assertFalse(SafePath.isSafe("d:level.dat"));
    }

    @Test
    void emptySegmentsAndNullsAreRejected() {
        assertFalse(SafePath.isSafe(""));
        assertFalse(SafePath.isSafe(null));
        assertFalse(SafePath.isSafe("region//r.0.0.mca"));
        assertFalse(SafePath.isSafe("region/"));
        assertFalse(SafePath.isSafe("a\0b"));
    }

    @Test
    void absurdlyLongPathsAreRejected() {
        StringBuilder longSegment = new StringBuilder();
        for (int i = 0; i < 300; i++) longSegment.append('a');
        assertFalse(SafePath.isSafe(longSegment.toString()));
    }

    @Test
    void regionDetectionUsesTheSameRules() {
        assertTrue(SafePath.isRegionFile("region/r.0.0.mca"));
        assertFalse(SafePath.isRegionFile("region/r.0.0.mca.bak"));
        assertFalse(SafePath.isRegionFile("DIM-1/region/r.0.0.mca"));
        assertFalse(SafePath.isRegionFile("../region/r.0.0.mca"));
    }
}

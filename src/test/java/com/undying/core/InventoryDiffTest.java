package com.undying.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The inventory guard's rule: which changes are worth a question.
 *
 * <p>These tests exist because the rule cannot be exercised any other way from a test — the
 * rest of the guard needs a live player, a container and a hostile mod, none of which a unit
 * test can provide. The threshold and the "explained changes are fine" branch are exactly the
 * parts that would otherwise be verified by reading them once.
 */
class InventoryDiffTest {

    @Test
    void identicalFingerprintsDifferInNothing() {
        assertEquals(0, InventoryDiff.countDiffering(
                new int[]{1, 2, 3, 0, 0},
                new int[]{1, 2, 3, 0, 0}));
    }

    @Test
    void countsEachChangedSlotOnce() {
        // Slots 0, 1 and 3 differ; 2 and 4 do not.
        assertEquals(3, InventoryDiff.countDiffering(
                new int[]{1, 2, 3, 4, 5},
                new int[]{0, 0, 3, 0, 5}));
    }

    @Test
    void aShorterInventoryStillCounts() {
        // A modded inventory can lose slots between ticks; that is a change even though the
        // remaining slots agree.
        assertEquals(2, InventoryDiff.countDiffering(
                new int[]{1, 2, 3},
                new int[]{1, 2, 3, 0, 0}));
    }

    @Test
    void nullsAreNotAChange() {
        // Called before the first baseline exists; must not throw on a hot path.
        assertEquals(0, InventoryDiff.countDiffering(null, new int[]{1}));
        assertEquals(0, InventoryDiff.countDiffering(new int[]{1}, null));
    }

    @Test
    void fiveSlotsWithNoCauseIsAQuestion() {
        assertTrue(InventoryDiff.deservesQuestion(5, false));
        assertTrue(InventoryDiff.deservesQuestion(41, false));
    }

    @Test
    void fourSlotsIsBelowTheThreshold() {
        assertFalse(InventoryDiff.deservesQuestion(4, false));
        assertFalse(InventoryDiff.deservesQuestion(0, false));
    }

    @Test
    void anExplainedChangeIsNeverAQuestion() {
        // Thirty slots moving while a chest is open is a shift-click, not an attack.
        assertFalse(InventoryDiff.deservesQuestion(30, true));
    }

    @Test
    void theThresholdMatchesTheDocumentedNumber() {
        // The javadoc and the README both say five; this fails if one is edited alone.
        assertEquals(5, InventoryDiff.SUSPICIOUS_SLOTS);
    }
}

package com.undying.core.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The cascade rule. This is the one place where a wrong result silently changes what the
 * protection does, and the player would have no way to tell: a switch that looks off but
 * leaves the death lock running is exactly the "silent failure" this project refuses.
 */
class CascadeRuleTest {

    @Test
    void turningOneSwitchOffTurnsEverythingOff() {
        boolean[] all = {true, true, true, true, true};
        boolean[] after = CascadeRule.afterFlip(all, 2);
        assertArrayEquals(new boolean[]{false, false, false, false, false}, after);
    }

    @Test
    void theMasterSwitchGoesOffWithAnyChild() {
        // Index 0 is immortal mode; clicking child 3 must take the master down too.
        boolean[] all = {true, true, true, true};
        assertFalse(CascadeRule.afterFlip(all, 3)[0]);
    }

    @Test
    void turningSomethingOnTouchesOnlyThatSwitch() {
        boolean[] some = {false, false, false, false};
        assertArrayEquals(new boolean[]{false, true, false, false}, CascadeRule.afterFlip(some, 1));
    }

    @Test
    void turningSomethingOnNeverEnablesTheMasterSwitch() {
        boolean[] off = {false, false, false};
        assertFalse(CascadeRule.afterFlip(off, 2)[0]);
    }

    @Test
    void theInputArrayIsNotModified() {
        boolean[] original = {true, true};
        boolean[] after = CascadeRule.afterFlip(original, 0);
        assertNotSame(original, after);
        assertTrue(original[0]);
        assertTrue(original[1]);
    }

    @Test
    void anOutOfRangeIndexChangesNothing() {
        boolean[] a = {true, false};
        assertArrayEquals(a, CascadeRule.afterFlip(a, 5));
        assertArrayEquals(a, CascadeRule.afterFlip(a, -1));
        assertNull(CascadeRule.afterFlip(null, 0));
    }

    @Test
    void onceEverythingIsOffItStaysOff() {
        boolean[] off = {false, false, false};
        boolean[] after = CascadeRule.afterFlip(off, 1);   // "off" switch clicked → turns on
        assertTrue(after[1]);
        boolean[] back = CascadeRule.afterFlip(after, 1);  // clicked again → all off
        assertArrayEquals(new boolean[]{false, false, false}, back);
    }
}

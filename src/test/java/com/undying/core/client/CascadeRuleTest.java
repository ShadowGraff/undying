package com.undying.core.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The switch rules.
 *
 * <p>These changed once already: the first version took the whole set down when <i>any</i>
 * switch was turned off, which meant silencing the aura silently disabled the death lock. The
 * tests below pin the current rule — master off means everything off, a child off means that
 * child — because a rule this easy to "improve" back into the old behaviour needs to fail
 * loudly when someone does.
 */
class CascadeRuleTest {

    private static final int MASTER = 0;

    @Test
    void turningAChildOffLeavesTheRestAlone() {
        boolean[] all = {true, true, true, true};
        assertArrayEquals(new boolean[]{true, true, false, true},
                CascadeRule.afterFlip(all, 2, MASTER));
    }

    @Test
    void theMasterSwitchIsNotTouchedByAChild() {
        boolean[] all = {true, true, true};
        assertTrue(CascadeRule.afterFlip(all, 2, MASTER)[0]);
    }

    @Test
    void turningTheMasterOffTurnsEverythingOff() {
        boolean[] all = {true, true, true, true, true};
        assertArrayEquals(new boolean[]{false, false, false, false, false},
                CascadeRule.afterFlip(all, MASTER, MASTER));
    }

    @Test
    void turningSomethingOnTouchesOnlyThatSwitch() {
        boolean[] some = {false, false, false, false};
        assertArrayEquals(new boolean[]{false, true, false, false},
                CascadeRule.afterFlip(some, 1, MASTER));
    }

    @Test
    void turningAChildOnNeverEnablesTheMaster() {
        boolean[] off = {false, false, false};
        assertFalse(CascadeRule.afterFlip(off, 2, MASTER)[0]);
    }

    @Test
    void theMasterIsWhicheverIndexTheCallerNames() {
        // Guards against the index being hardcoded: index 2 is the master here.
        boolean[] all = {true, true, true};
        assertArrayEquals(new boolean[]{false, false, false},
                CascadeRule.afterFlip(all, 2, 2));
        assertArrayEquals(new boolean[]{true, false, true},
                CascadeRule.afterFlip(all, 1, 2));
    }

    @Test
    void theInputArrayIsNotModified() {
        boolean[] original = {true, true};
        boolean[] after = CascadeRule.afterFlip(original, 0, MASTER);
        assertNotSame(original, after);
        assertTrue(original[0]);
        assertTrue(original[1]);
    }

    @Test
    void anOutOfRangeIndexChangesNothing() {
        boolean[] a = {true, false};
        assertArrayEquals(a, CascadeRule.afterFlip(a, 5, MASTER));
        assertArrayEquals(a, CascadeRule.afterFlip(a, -1, MASTER));
        assertNull(CascadeRule.afterFlip(null, 0, MASTER));
    }

    @Test
    void aChildCyclesOffAndOnAgain() {
        boolean[] all = {true, true, true};
        boolean[] off = CascadeRule.afterFlip(all, 1, MASTER);
        assertFalse(off[1]);
        boolean[] back = CascadeRule.afterFlip(off, 1, MASTER);
        assertTrue(back[1]);
        assertTrue(back[0], "turning a child back on must not disturb the master");
    }
}

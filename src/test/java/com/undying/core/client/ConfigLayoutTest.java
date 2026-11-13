package com.undying.core.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The config screen's layout maths.
 *
 * <h3>The bug these tests exist for</h3>
 * With 25 switches in two columns, a fixed 24 px pitch put the thirteenth row exactly on top
 * of the bottom navigation row. Both drew, one over the other, and two labels became
 * unreadable overlap. The numbers below are the real ones from that window (roughly 470x376
 * GUI pixels) plus a sweep of other sizes: the invariant has to hold everywhere, not just at
 * the size the bug was reported at.
 */
class ConfigLayoutTest {

    /** Every size where the grid must stay clear of the bottom row. */
    private static final int[][] SIZES = {
            {320, 240}, {427, 240}, {470, 376}, {480, 270}, {640, 360}, {854, 480}, {1920, 1080}, {2560, 1440},
    };

    @Test
    void gridNeverOverlapsTheBottomRow() {
        for (int[] size : SIZES) {
            int width = size[0];
            int height = size[1];
            ConfigLayout.Plan plan = ConfigLayout.plan(25, width, height);

            assertTrue(plan.bottomEdge() <= ConfigLayout.bottomRowY(height),
                    "overlap at " + width + "x" + height
                            + ": last row ends at " + plan.bottomEdge()
                            + ", bottom row starts at " + ConfigLayout.bottomRowY(height));
        }
    }

    @Test
    void everySwitchGetsASlot() {
        for (int[] size : SIZES) {
            ConfigLayout.Plan plan = ConfigLayout.plan(25, size[0], size[1]);
            assertTrue(plan.columns() * plan.perColumn() >= 25,
                    "not enough slots at " + size[0] + "x" + size[1]);
        }
    }

    @Test
    void columnsStayWideEnoughWhereTheWindowAllowsIt() {
        for (int[] size : SIZES) {
            ConfigLayout.Plan plan = ConfigLayout.plan(25, size[0], size[1]);
            if (size[0] >= 640) {
                assertTrue(ConfigLayout.columnWidth(size[0], plan.columns())
                                >= ConfigLayout.MIN_COLUMN_WIDTH,
                        "column too narrow at " + size[0] + "x" + size[1]);
            }
        }
    }

    @Test
    void theReportedWindowUsesTwoReadableColumns() {
        // 25 switches, ordinary window: the familiar two-column look must be kept.
        ConfigLayout.Plan plan = ConfigLayout.plan(25, 470, 376);
        assertEquals(2, plan.columns());
        assertEquals(13, plan.perColumn());
        assertTrue(plan.buttonHeight() >= ConfigLayout.MIN_BUTTON_HEIGHT,
                "buttons shrank to " + plan.buttonHeight() + " px, text would be clipped");
    }

    @Test
    void aShortWindowAddsColumnsInsteadOfSqueezingRows() {
        // 300 px tall: two columns of 13 rows cannot hold readable buttons, so a third
        // column has to appear.
        ConfigLayout.Plan plan = ConfigLayout.plan(25, 640, 300);
        assertEquals(3, plan.columns());
        assertTrue(plan.buttonHeight() >= ConfigLayout.MIN_BUTTON_HEIGHT);
    }

    @Test
    void aTinyWindowStillDoesNotOverlap() {
        ConfigLayout.Plan plan = ConfigLayout.plan(25, 260, 170);
        assertTrue(plan.bottomEdge() <= ConfigLayout.bottomRowY(170));
    }
}

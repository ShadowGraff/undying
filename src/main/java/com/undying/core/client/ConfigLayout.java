package com.undying.core.client;

/**
 * Layout maths for {@link UndyingConfigScreen}, kept out of the screen so it can be tested.
 *
 * <h3>Why this exists at all</h3>
 * The screen used to place its buttons with a fixed pitch of 24 px starting at y = 48 and
 * assume the window was tall enough. It is not: with the GUI scale and window size the player
 * actually used, the thirteenth row landed exactly on top of the bottom button row — the two
 * drew into the same 20 pixels and the labels overlapped into unreadable mush. Nothing threw,
 * so it looked like a font problem rather than a layout problem.
 *
 * <p>The fix is not "move the bottom row down" (there is nowhere to move it): the grid has to
 * fit whatever height it is given. This computes the pitch from the available space, adds
 * columns when a column would become too cramped, and shrinks the button height only as far
 * as the text still fits.
 *
 * <h3>Invariants (asserted by the tests)</h3>
 * <ul>
 *   <li>{@code startY + (perColumn - 1) * pitch + buttonHeight <= bottomRowY} — the grid never
 *       overlaps the bottom row;</li>
 *   <li>every column is at least {@link #MIN_COLUMN_WIDTH} wide, or the plan falls back to
 *       fewer columns;</li>
 *   <li>{@code columns * perColumn >= count} — every switch gets a slot.</li>
 * </ul>
 */
public final class ConfigLayout {

    /** Text is nine pixels tall; below this a button is unreadable, so stop shrinking. */
    public static final int MIN_BUTTON_HEIGHT = 12;

    /** Below this the label has no room beside the value, so a column is not worth adding. */
    public static final int MIN_COLUMN_WIDTH = 110;

    /** Vertical gap between two rows. */
    public static final int GAP_Y = 4;

    /** Horizontal gap between two columns. */
    public static final int GAP_X = 8;

    /** Room reserved above the grid: title at 16, hint at 30. */
    public static final int START_Y = 56;

    /** Room reserved below the grid for the four navigation buttons. */
    public static final int BOTTOM_RESERVE = 56;

    private ConfigLayout() {}

    /**
     * @param columns     how many columns the grid uses
     * @param perColumn   how many rows a column holds
     * @param pitch       row-to-row distance in pixels (button height + gap)
     * @param buttonHeight height of one switch button
     * @param startY      y of the first row
     */
    public record Plan(int columns, int perColumn, int pitch, int buttonHeight, int startY) {

        /** y of the last switch row (top edge). */
        public int lastRowY() {
            return startY + (perColumn - 1) * pitch;
        }

        /** Bottom edge of the last switch row. */
        public int bottomEdge() {
            return lastRowY() + buttonHeight;
        }
    }

    /** Width of one column for the given screen width. */
    public static int columnWidth(int width, int columns) {
        int cols = Math.max(1, columns);
        int usable = Math.max(0, width - 16 - (cols - 1) * GAP_X);
        return usable / cols;
    }

    /** y of the bottom button row. */
    public static int bottomRowY(int height) {
        return height - 40;
    }

    /**
     * Choose the layout.
     *
     * <p>Tries the fewest columns first, so the familiar two-column look is kept whenever it
     * fits, and only adds columns when the rows would otherwise be squeezed thinner than
     * readable.
     */
    public static Plan plan(int count, int width, int height) {
        int n = Math.max(1, count);
        int available = Math.max(24, bottomRowY(height) - 4 - START_Y);

        for (int cols = 1; cols <= 4; cols++) {
            int perColumn = (n + cols - 1) / cols;
            if (columnWidth(width, cols) < MIN_COLUMN_WIDTH) break;   // wider layouts only get narrower
            int pitch = available / perColumn;
            if (pitch >= MIN_BUTTON_HEIGHT + GAP_Y) {
                return new Plan(cols, perColumn, pitch, Math.min(20, pitch - GAP_Y), START_Y);
            }
        }

        // Nothing fits comfortably (a very short window): use as many columns as the width
        // allows and accept the squeeze. Overlapping the bottom row is the one outcome that is
        // never acceptable, so the pitch is derived from the space rather than fixed.
        int cols = 1;
        for (int c = 2; c <= 4; c++) {
            if (columnWidth(width, c) >= MIN_COLUMN_WIDTH) cols = c;
        }
        int perColumn = (n + cols - 1) / cols;
        // Integer division keeps pitch * perColumn <= available, so the grid still cannot run
        // into the bottom row even here.
        int pitch = Math.max(4, available / perColumn);
        int buttonHeight = Math.max(4, Math.min(20, pitch - 2));
        return new Plan(cols, perColumn, pitch, buttonHeight, START_Y);
    }
}

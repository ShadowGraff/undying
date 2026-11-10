package com.undying.core.client;

/**
 * The "three presses" rule for the inventory prompt, with no graphics attached.
 *
 * <h3>Why it is not inline in the HUD</h3>
 * Y and N are ordinary letters. One press is what happens when the player types in chat, or
 * sneezes on the keyboard. Counting them — and resetting the other side — is the part that
 * decides whether an inventory gets rolled back, so it deserves a test rather than a
 * "looks right" review. Everything here is plain integers.
 */
public final class AnswerKeys {

    /** Presses needed before an answer counts. */
    public static final int PRESSES_REQUIRED = 3;

    public enum Decision { NONE, CONFIRM, DECLINE }

    private int yes;
    private int no;

    /**
     * Record one press.
     *
     * @param confirm true for Y, false for N
     * @return the decision this press completed, or {@link Decision#NONE}
     */
    public Decision press(boolean confirm) {
        if (confirm) {
            yes++;
            no = 0;
            if (yes >= PRESSES_REQUIRED) {
                reset();
                return Decision.CONFIRM;
            }
        } else {
            no++;
            yes = 0;
            if (no >= PRESSES_REQUIRED) {
                reset();
                return Decision.DECLINE;
            }
        }
        return Decision.NONE;
    }

    public int yesCount() {
        return yes;
    }

    public int noCount() {
        return no;
    }

    public void reset() {
        yes = 0;
        no = 0;
    }
}

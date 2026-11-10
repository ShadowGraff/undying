package com.undying.core.client;

/**
 * The switch cascade, as arithmetic on a boolean array.
 *
 * <h3>The rule (the user's decision)</h3>
 * Turning <b>any</b> switch off turns off immortal mode and everything under it. Immortal mode
 * is one contract, not a menu of extras: a player who disables the totem protection but keeps
 * the death lock ends up in a configuration nobody has ever tested. Turning something
 * <i>on</i> affects only that switch — the master switch is never enabled implicitly, because
 * "I turned on the aura" must not quietly mean "I handed out invulnerability".
 *
 * <p>Kept here, away from {@code Screen}, so the rule can be tested without a client. The GUI
 * is where it is applied, not where it is defined.
 */
public final class CascadeRule {

    private CascadeRule() {}

    /**
     * Apply one flip.
     *
     * @param current current values, one per switch
     * @param index   the switch that was clicked
     * @return the values after the click; never the input array itself
     */
    public static boolean[] afterFlip(boolean[] current, int index) {
        if (current == null || index < 0 || index >= current.length) return current;
        boolean[] out = current.clone();

        if (current[index]) {
            // Was on, now off: everything goes with it, including the master switch.
            java.util.Arrays.fill(out, false);
        } else {
            out[index] = true;
        }
        return out;
    }
}

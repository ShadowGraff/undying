package com.undying.core.client;

/**
 * The switch rules, as arithmetic on a boolean array.
 *
 * <h3>Rule (current)</h3>
 * Immortal mode is a <b>total switch</b>, not a cascade trigger:
 * <ul>
 *   <li>Turning the master switch <b>off</b> switches everything off — that is what a total
 *       switch means.</li>
 *   <li>Turning any <b>child</b> off affects <b>only that child</b>. The master switch and the
 *       other children keep their state.</li>
 *   <li>Turning anything <b>on</b> affects only that one switch; the master is never enabled
 *       implicitly, because "I turned the aura on" must not quietly mean "I handed out
 *       invulnerability".</li>
 * </ul>
 *
 * <h3>Why it changed</h3>
 * The earlier rule made any switch turning off take the whole set down, so a player who wanted
 * to silence the aura lost the death lock with it. That is a bigger decision than the click
 * they made. Independence is the safer reading of the same intent: the master switch exists to
 * mean "all of it, or none of it", and single switches exist to mean themselves.
 *
 * <p>Kept away from {@code Screen} so the rules can be tested without a client. The GUI applies
 * them; it does not define them.
 */
public final class CascadeRule {

    private CascadeRule() {}

    /**
     * Apply one flip.
     *
     * @param current     current values, one per switch
     * @param index       the switch that was clicked
     * @param masterIndex the switch that acts as the total switch
     * @return the values after the click; never the input array itself
     */
    public static boolean[] afterFlip(boolean[] current, int index, int masterIndex) {
        if (current == null || index < 0 || index >= current.length) return current;
        boolean[] out = current.clone();

        if (!current[index]) {
            // Off → on: that switch only.
            out[index] = true;
        } else if (index == masterIndex) {
            // The total switch going off takes everything with it.
            java.util.Arrays.fill(out, false);
        } else {
            // A child going off is about that child.
            out[index] = false;
        }
        return out;
    }
}

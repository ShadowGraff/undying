package com.undying.core;

/**
 * The inventory guard's decision, separated from everything that needs a live player.
 *
 * <h3>Why this is its own class</h3>
 * The rule — "five or more slots changed at once, with no action that could explain it" — is
 * the whole feature. Everything else in {@link InventoryGuard} is plumbing: fingerprints,
 * ticks, event bookkeeping. Keeping the rule here means it can be tested with two int arrays
 * instead of a running server, which matters because the rest of this file cannot be.
 *
 * <p>Note the distinction from {@code SaveSnapshot.fingerprint}: that one guards durability of
 * a periodic file, this one answers a yes/no question about a single tick.
 */
public final class InventoryDiff {

    private InventoryDiff() {}

    /** Slots that must differ at once before the change is worth asking about. */
    public static final int SUSPICIOUS_SLOTS = 5;

    /**
     * How many slots differ between two fingerprints.
     *
     * <p>A length mismatch counts as one difference per missing slot: a modded inventory can
     * grow or shrink between ticks, and that is a change even though no slot's contents
     * disagree.
     */
    public static int countDiffering(int[] a, int[] b) {
        if (a == null || b == null) return 0;
        int n = 0;
        int len = Math.min(a.length, b.length);
        for (int i = 0; i < len; i++) {
            if (a[i] != b[i]) n++;
        }
        n += Math.abs(a.length - b.length);
        return n;
    }

    /**
     * @param changedSlots how many slots differ from the last accepted state
     * @param explained    whether an action in the last two seconds could account for it
     * @return true when the player should be asked
     */
    public static boolean deservesQuestion(int changedSlots, boolean explained) {
        return !explained && changedSlots >= SUSPICIOUS_SLOTS;
    }
}

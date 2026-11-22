package com.undying.core;

import net.minecraft.server.level.ServerPlayer;

/**
 * The single decision "may this death happen", callable from more than one place.
 *
 * <h3>Why the decision moved up one class</h3>
 * Cancelling in {@code LivingEntity.die} is too late: {@code ServerPlayer.die} sends
 * {@code ClientboundPlayerCombatKillPacket} <b>before</b> calling {@code super.die()}. The player
 * therefore saw the death screen, and the game had already run the pre-super work (totem checks,
 * stats, drops), even though the death itself was cancelled a moment later. Repeated hits produced
 * a player who "keeps dying and never dies".
 *
 * <p>So the hook that actually decides now sits on {@code ServerPlayer.die}, ahead of the packet.
 * The {@code LivingEntity.die} hook stays as a backstop for deaths that reach it another way; both
 * call this method, and this method is written to be safe to call twice:
 * <ul>
 *   <li>the suicide permit is <b>consumed</b>, so a second call sees nothing to permit;</li>
 *   <li>everything else is idempotent (rewinding an already-rewound death does nothing).</li>
 * </ul>
 */
public final class DeathIntercept {

    private DeathIntercept() {}

    /**
     * @return true when the caller must cancel the death
     */
    public static boolean shouldCancel(ServerPlayer p) {
        if (p == null) return false;
        if (!ProtectionBaseline.master() || !ProtectionBaseline.deathLock()) return false;

        if (!Environment.isAllowed(p)) return false;

        // Everything else: rewind the death flow and refuse it.
        //
        // If the rewind itself fails, the death is allowed through. A bug in here must not be
        // able to make the player permanently unkillable - "the protection broke, so you cannot
        // die" is the worst possible failure mode for a mod whose whole promise is control.
        try {
            DeathLock.exitState(p);
            Notify.removalBlocked(p, "die()");
            return true;
        } catch (Throwable t) {
            ModLog.swallowed("DeathIntercept.exitState", t);
            return false;
        }
    }
}

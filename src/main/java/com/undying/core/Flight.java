package com.undying.core;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Abilities;

/**
 * Survival flight for immortal mode, granted by double-tapping jump.
 *
 * <h3>Why abilities and not a movement hack</h3>
 * Setting {@code mayfly} and letting vanilla handle the rest means the client, the server's
 * own flight checks and every other mod see an ordinary flying player. Pushing the entity
 * upward every tick instead would fight vanilla's gravity, trip the "flying is not enabled"
 * kick, and look wrong to anything that inspects {@code getAbilities()}.
 *
 * <h3>Whose flight is it</h3>
 * The first version had no answer to that question, and it broke a real setup: a player wearing
 * gear from another mod that grants flight could not fly at all once this feature was on,
 * because the toggle read {@code flying == true} (set by the other mod) as "the player wants to
 * land", and the revoke path cleared {@code mayfly} without knowing who had set it.
 *
 * <p>So the grant is now <b>owned</b>: {@code PlayerState.flightOwned} records that this mod
 * handed out the permission, and {@code flightMayflyBefore} records what the value was before.
 * Landing and revoking both restore that original value instead of assuming {@code false} —
 * taking away a permission another mod needs, because our own feature is finished with it, is
 * how one mod breaks another's gameplay.
 */
public final class Flight {

    private Flight() {}

    /** Double-tap jump: take over and fly, or land if this mod is the one flying. */
    public static void toggle(ServerPlayer p) {
        try {
            if (p == null) return;
            if (!ModConfig.FLIGHT.get() || !ModConfig.MASTER_TOGGLE.get()) return;
            if (!Environment.isAllowed(p)) return;
            if (p.isSpectator() || p.isCreative()) return;

            PlayerState st = PlayerState.of(p);
            Abilities a = p.getAbilities();

            // "Landing" only applies to flight this mod granted. If the player is airborne
            // because of someone else's gear, the first double-tap takes over instead of
            // dropping them out of the sky.
            boolean landing = st.flightOwned && a.flying;

            if (landing) {
                a.flying = false;
                st.flightOwned = false;
                a.mayfly = st.flightMayflyBefore;   // hand it back as we found it
            } else {
                st.flightMayflyBefore = a.mayfly;
                a.mayfly = true;
                a.flying = true;
                st.flightOwned = true;

                // A small nudge so the take-off reads as a jump followed by flight rather than
                // the player popping up from a standstill like a block being placed.
                if (p.onGround()) {
                    p.setDeltaMovement(p.getDeltaMovement().x, 0.42, p.getDeltaMovement().z);
                }
                p.hasImpulse = true;
            }

            p.onUpdateAbilities();

            Msg.info(p, UndyingMod.TAG)
                    .bi(landing ? "飞行：关闭" : "飞行：开启",
                            landing ? "Flight: off" : "Flight: on")
                    .actionBar().send();
        } catch (Throwable t) {
            ModLog.swallowed("Flight.toggle", t);
        }
    }

    /**
     * Take the grant back when it is no longer justified.
     *
     * <p>Only touches a grant this mod made. Doing nothing here would leave a player flying
     * after the switch was turned off — the "it says off but it is still on" behaviour that
     * makes a settings screen untrustworthy. Doing it unconditionally is worse: it would clear
     * another mod's {@code mayfly} and break a feature that has nothing to do with us.
     */
    public static void enforce(ServerPlayer p) {
        try {
            if (p == null) return;
            if (p.isSpectator() || p.isCreative()) return;

            PlayerState st = PlayerState.of(p);
            if (!st.flightOwned) return;   // not ours: leave it alone

            boolean allowed = ModConfig.FLIGHT.get()
                    && ModConfig.MASTER_TOGGLE.get()
                    && Environment.isAllowed(p);
            if (allowed) return;

            Abilities a = p.getAbilities();
            a.flying = false;
            a.mayfly = st.flightMayflyBefore;
            st.flightOwned = false;
            p.onUpdateAbilities();

            IntegrityGuard.LOGGER.info("[flight] grant revoked for {} (switch off or gate closed)",
                    p.getName().getString());
        } catch (Throwable t) {
            ModLog.swallowed("Flight.enforce", t);
        }
    }
}

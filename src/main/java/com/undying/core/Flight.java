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
 * <p>The one part that is not automatic: {@code mayfly} is what stops
 * {@code ServerGamePacketListenerImpl} from kicking a survival player who is not falling, so it
 * stays granted while the flight switch is on rather than being handed out per take-off. That
 * also means it has to be taken back — see {@link #enforce}, which exists because "turned the
 * switch off but kept flying" is the silent failure this project refuses to ship.
 *
 * <h3>Deliberately not applied</h3>
 * Creative players already fly; spectators fly and have no abilities to grant. Both are left
 * alone so the mod never fights vanilla for a state it already provides.
 */
public final class Flight {

    private Flight() {}

    /** Double-tap jump: take off if grounded, land if flying. */
    public static void toggle(ServerPlayer p) {
        try {
            if (p == null) return;
            if (!ModConfig.FLIGHT.get() || !ModConfig.MASTER_TOGGLE.get()) return;
            if (!Environment.isAllowed(p)) return;
            if (p.isSpectator() || p.isCreative()) return;

            Abilities a = p.getAbilities();
            boolean takeOff = !a.flying;

            if (takeOff) {
                a.mayfly = true;
                a.flying = true;
                // A small nudge so the take-off reads as a jump followed by flight rather than
                // the player popping up from a standstill like a block being placed.
                if (p.onGround()) p.setDeltaMovement(p.getDeltaMovement().x, 0.42, p.getDeltaMovement().z);
                p.hasImpulse = true;
            } else {
                a.flying = false;
            }

            p.onUpdateAbilities();

            Msg.info(p, UndyingMod.TAG)
                    .bi(takeOff ? "飞行：开启" : "飞行：关闭",
                            takeOff ? "Flight: on" : "Flight: off")
                    .actionBar().send();
        } catch (Throwable t) {
            ModLog.swallowed("Flight.toggle", t);
        }
    }

    /**
     * Take the grant back when it is no longer justified.
     *
     * <p>Called from the periodic tick. Doing nothing here would leave a player flying after the
     * switch was turned off, which is exactly the kind of "it says off but it is still on"
     * behaviour that makes a settings screen untrustworthy.
     */
    public static void enforce(ServerPlayer p) {
        try {
            if (p == null) return;
            if (p.isSpectator() || p.isCreative()) return;

            boolean allowed = ModConfig.FLIGHT.get()
                    && ModConfig.MASTER_TOGGLE.get()
                    && Environment.isAllowed(p);

            Abilities a = p.getAbilities();
            if (!allowed && (a.mayfly || a.flying)) {
                a.mayfly = false;
                a.flying = false;
                p.onUpdateAbilities();
                IntegrityGuard.LOGGER.info("[flight] grant revoked for {} (switch off or gate closed)",
                        p.getName().getString());
            }
        } catch (Throwable t) {
            ModLog.swallowed("Flight.enforce", t);
        }
    }
}

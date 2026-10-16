package com.undying.core;

import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * Public facade for the death lock.
 *
 * <p>Centralises "who is allowed to let one death through", for the mixins to call.
 * The state itself lives on {@link PlayerState}; this class only offers semantic operations.
 *
 * <h3>Why an authorisation mechanism</h3>
 * {@code MixinEntity} originally had {@code if (p.isDeadOrDying()) return;} -
 * once the player was flagged "dead", every subsequent removal interception stepped aside.
 * The intent was "do not interfere with normal death and respawn", but this check <b>can be forged from the outside</b>:
 * an external implementation only has to drive health to 0 first and all of our blocking stops
 * working (observed: with this mod installed {@code /kill} still kills the player).
 *
 * <p>=> It now looks only at two signals that <b>cannot be forged from the outside</b>:
 * <ol>
 *   <li>{@link #markSuicide} - written by our own {@code /antigrief die}</li>
 *   <li>{@link #grant} - written only on a real death path we recognise, expiring after 60 seconds</li>
 * </ol>
 * If neither holds -> block everything. Authorisation is only used to let "the normal removal in
 * the respawn flow" through; it does not change the death lock itself.
 */
public final class DeathLock {

    private DeathLock() {}

    /** Authorisation lifetime: 60 seconds. It expires, so no permanent backdoor is left behind. */
    private static final long GRANT_TTL_MS = 60_000L;

    /** Record that "this player really did die", so MixinEntity lets the later death cleanup and respawn through */
    public static void grant(UUID id) {
        if (id == null) return;
        PlayerState.of(id).deathGrantAt = System.currentTimeMillis();
    }

    /** Whether this player is currently inside the death authorisation window */
    public static boolean isGranted(ServerPlayer p) {
        if (p == null) return false;
        PlayerState s = PlayerState.find(p.getUUID());
        if (s == null || s.deathGrantAt == 0L) return false;
        if (System.currentTimeMillis() - s.deathGrantAt > GRANT_TTL_MS) {
            s.deathGrantAt = 0L;
            return false;
        }
        return true;
    }

    /** Mark that "this player is going through our own suicide channel" */
    public static void markSuicide(UUID id) {
        if (id != null) PlayerState.of(id).pendingSuicide = true;
    }

    /**
     * Read and clear the suicide flag (one shot).
     *
     * <p>Read-only, never creates: returns false when the player is not registered, so an entry is
     * not created for a player who has already left and would never be cleaned up.
     */
    public static boolean consumeSuicide(UUID id) {
        PlayerState s = PlayerState.find(id);
        if (s == null || !s.pendingSuicide) return false;
        s.pendingSuicide = false;
        return true;
    }

    /** Query the suicide flag without consuming it */
    public static boolean isSuicidePending(UUID id) {
        PlayerState s = PlayerState.find(id);
        return s != null && s.pendingSuicide;
    }

    /**
     * Pull the player all the way back out of the "dying" state.
     *
     * <p>Called when die() is cancelled. Three things must happen together, none optional:
     * <ol>
     *   <li>Reset the death timer - otherwise tickDeath keeps collecting the corpse on a 20-tick rhythm</li>
     *   <li>Raise health back to the fallback - otherwise isDeadOrDying() stays true and the death flow restarts repeatedly</li>
     *   <li>Replay the totem effect once - so the player clearly sees that the last hit was blocked</li>
     * </ol>
     */
    public static void exitState(ServerPlayer p) {
        if (p == null) return;
        try {
            ((com.undying.core.mixin.LivingEntityDeathTimerAccess) (Object) p)
                    .undying$setDeathTime(0);
        } catch (Throwable t) {
            ModLog.swallowed("DeathLock.exitState.deathTime", t);
        }
        try {
            if (p.getHealth() <= 0.0F) p.setHealth(Const.DEATH_LOCK_HEALTH);
        } catch (Throwable t) {
            ModLog.swallowed("DeathLock.exitState.health", t);
        }
        try {
            p.level().broadcastEntityEvent(p, (byte) 35);
        } catch (Throwable t) {
            ModLog.swallowed("DeathLock.exitState.totemFx", t);
        }
    }
}

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
 * <p>=> (Historical note, kept because it explains the shape of this class.) It once looked
 * at two signals believed to be unforgeable:
 * <ol>
 *   <li>a suicide flag written by our own command (removed in 2.9.1)</li>
 *   <li>a 60-second grant written when a real death was recognised (removed in 2.9.1)</li>
 * </ol>
 * If neither holds -> block everything. Authorisation is only used to let "the normal removal in
 * the respawn flow" through; it does not change the death lock itself.
 */
public final class DeathLock {

    private DeathLock() {}

    // ────────────────────────────────────────────────────────────────────────────
    // There is no "authorised death" any more.
    //
    // Until 2.9.1 this class handed out two permits: a suicide flag written by
    // /antigrief die, and a 60-second grant written when a real death was recognised.
    // Both existed so that one specific flow — the player asking to die — could pass
    // through five separate gates. Measured consequence: the permits were the cheapest
    // way to kill the player, because a permit is just a boolean that any code in the
    // process can drive, and every gate agreed to stand aside for it.
    //
    // 2.9.1 removes the command and both permits. A death now either happens with the
    // death lock off, or does not happen at all. That is a smaller promise than "you can
    // always choose to die" and a much larger one than "a stranger cannot fabricate your
    // consent".
    // ────────────────────────────────────────────────────────────────────────────

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

package com.undying.core;

import net.minecraft.server.level.ServerPlayer;

/**
 * Reads the result, not the call chain.
 *
 * <h3>Why a third layer exists at all</h3>
 * Every other protection hooks a method that something has to call: {@code hurt}, {@code die},
 * {@code kill}, {@code remove}, {@code SynchedEntityData.set}. Two rounds of testing showed the
 * weakness of that assumption:
 *
 * <ul>
 *   <li>{@code DataItem.setValue} writes the real value without passing either {@code set} hook;</li>
 *   <li>a mixin with a lower priority can cancel at the same injection point, and Mixin skips the
 *       remaining handlers once a callback is cancelled.</li>
 * </ul>
 *
 * Neither is fixable by adding another hook — there is always a lower-level write and always a
 * smaller priority. What cannot be forged is the <b>outcome</b>: after every tick, the player's
 * health either is or is not above the floor. This checks that, and puts it back if it is not.
 *
 * <h3>Cost</h3>
 * One float read, once every four ticks. No allocation, no logging unless something is wrong.
 * That is the entire budget for a layer that does not depend on any call chain surviving.
 */
public final class PostTickInvariant {

    private PostTickInvariant() {}

    /** Every four ticks: fast enough that a ghost cannot act for long, cheap enough to forget. */
    private static final int CHECK_EVERY = 4;

    public static void check(ServerPlayer p) {
        try {
            if (p == null || p.tickCount % CHECK_EVERY != 0) return;
            if (!ProtectionBaseline.master() || !ProtectionBaseline.deathLock()) return;
            if (!Environment.isAllowed(p)) return;
            if (p.isDeadOrDying()) return;                       // a real teardown is in progress

            float hp = p.getHealth();

            // NaN deserves its own branch: every comparison against it is false, so a "health <
            // floor" test alone would let it through forever — an unkillable, unhealable player.
            if (Float.isNaN(hp)) {
                p.setHealth(Const.DEATH_LOCK_HEALTH);
                warn(p, "health was NaN");
                return;
            }
            if (hp < Const.DEATH_LOCK_HEALTH) {
                p.setHealth(Const.DEATH_LOCK_HEALTH);
                warn(p, "health " + hp + " below the floor");
            }
        } catch (Throwable t) {
            ModLog.swallowed("PostTickInvariant.check", t);
        }
    }

    /**
     * Log at most once every five seconds per player.
     *
     * <p>This layer is expected to fire when something is actively fighting us; logging every
     * four ticks would produce 300 lines a minute and bury the one line that matters.
     */
    private static void warn(ServerPlayer p, String why) {
        try {
            var at = PlayerState.of(p).notifyAt;
            long now = System.currentTimeMillis();
            Long last = at.get("invariant");
            if (last != null && now - last < 5000L) return;
            at.put("invariant", now);
            IntegrityGuard.LOGGER.warn("[invariant] raised health for {} ({})",
                    p.getName().getString(), why);
        } catch (Throwable ignored) {
            // losing a log line is acceptable; throwing from a safety net is not
        }
    }
}

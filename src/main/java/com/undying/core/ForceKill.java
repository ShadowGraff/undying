package com.undying.core;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Forced termination: actually kill <b>a single target</b>.
 *
 * <p>The reason this is separate from {@link Execution} (the state machine of the
 * execution chain): that one manages "which layer and when to advance", this one
 * manages "did this attempt actually kill it". The two have completely different
 * failure modes.
 *
 * <p><b>4 layers tried in order, with a post-mortem after each</b>; the escalation is
 * necessary because different environments block different things: some block
 * damage, some block {@code setHealth}, some block {@code remove}.
 *
 * <p><b>Layer 1 must come first</b>: the first statement of vanilla {@code hurt()}
 * is {@code if (getHealth() <= 0) return false;} —— once health has hit zero,
 * further calls to {@code hurt()} are bounced straight back and {@code die()} is
 * never invoked.
 *
 * <p>Why a plain {@code remove()} is not good enough:
 */
public final class ForceKill {

    private ForceKill() {}

    /** Never force-kill players —— players have their own death lock and suicide channel */
    public static void kill(Entity target) {
        if (target == null) return;
        if (target instanceof ServerPlayer) return;

        if (target.isRemoved() || !isStillAlive(target)) return;

        String name;
        try {
            name = target.getType().toString();
        } catch (Throwable t) {
            ModLog.swallowed("ForceKill.name", t);
            name = "?";
        }

        float hpBefore = Float.NaN;
        if (target instanceof LivingEntity le0) {
            try {
                hpBefore = le0.getHealth();
            } catch (Throwable t) {
                ModLog.swallowed("ForceKill.hp", t);
            }
        }

        // ── Layer 1: 2.1 billion damage (must be done first) ──
        if (target instanceof LivingEntity le) {
            try {
                le.hurt(le.damageSources().genericKill(), Const.VOID_DAMAGE);
            } catch (Throwable t) {
                ModLog.swallowed("ForceKill.hurt", t);
            }
        }
        if (deadNow(target)) {
            report(name, hpBefore, 1);
            return;
        }

        // ── Layer 2: health set straight to zero ──
        if (target instanceof LivingEntity le2) {
            try {
                le2.setHealth(0.0F);
            } catch (Throwable t) {
                ModLog.swallowed("ForceKill.setHealth", t);
            }
        }
        if (deadNow(target)) {
            report(name, hpBefore, 2);
            return;
        }

        // ── Layer 3: Entity.kill() ──
        try {
            target.kill();
        } catch (Throwable t) {
            ModLog.swallowed("ForceKill.kill", t);
        }
        if (deadNow(target)) {
            report(name, hpBefore, 3);
            return;
        }

        // ── Layer 4: fallback ──
        try {
            clearPersistentData(target);
            if (target instanceof LivingEntity le4) {
                new ArrayList<>(le4.getActiveEffects())
                        .forEach(e -> le4.removeEffect(e.getEffect()));
            }
            target.remove(Entity.RemovalReason.KILLED);
            target.remove(Entity.RemovalReason.DISCARDED);
        } catch (Throwable t) {
            ModLog.swallowed("ForceKill.fallback", t);
        }
        report(name, hpBefore, 4);
    }

    /** Whether the target already "counts as dead" (OR of three checks, deliberately lenient) */
    public static boolean deadNow(Entity t) {
        try {
            if (t.isRemoved()) return true;
            if (t instanceof LivingEntity le) {
                if (le.isDeadOrDying()) return true;
                if (le.getHealth() <= 0.0F) return true;
            }
        } catch (Throwable ex) {
            ModLog.swallowed("ForceKill.deadNow", ex);
        }
        return false;
    }

    /**
     * Clear an entity's persistent NBT.
     *
     * <p>⚠️ The keys must be copied before removal: {@code getAllKeys()} returns a
     * <b>live view</b>, not a copy, so removing while iterating necessarily throws
     * {@code ConcurrentModificationException}.
     */
    public static void clearPersistentData(Entity target) {
        var tag = target.getPersistentData();
        for (String k : new ArrayList<>(tag.getAllKeys())) {
            tag.remove(k);
        }
    }

    /** Whether the target is still alive (tolerates isAlive throwing; if it throws, treat it as alive and keep going) */
    private static boolean isStillAlive(Entity t) {
        try {
            return t.isAlive();
        } catch (Throwable ex) {
            ModLog.swallowed("ForceKill.isStillAlive", ex);
            return true;
        }
    }

    /** Post-mortem report: only the first 20 are logged to avoid flooding. This is the only evidence of "which layer did the job". */
    private static final AtomicInteger REPORT_COUNT = new AtomicInteger();

    private static void report(String type, float hpBefore, int reached) {
        int n = REPORT_COUNT.incrementAndGet();
        if (n > 20) return;
        try {
            UndyingMod.LOGGER.info("[ForceKill#{}] {} health before kill={} reached layer {}",
                    n, type, Float.isNaN(hpBefore) ? "?" : hpBefore, reached);
        } catch (Throwable t) {
            ModLog.swallowed("ForceKill.report", t);
        }
    }
}

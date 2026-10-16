package com.undying.core;

import net.minecraft.server.level.ServerPlayer;

/**
 * Spawn protection: grants a brief window of complete damage immunity when a player
 * (re-)enters the world, so they are not hurt by mob spawns, terrain, etc. right after landing.
 *
 * <p>During the window immunity is implemented by {@code MixinLivingEntity.guardWardShield}
 * intercepting hurt — it shares the same channel as the hidden ward shield, and the two
 * do not interfere with each other.
 *
 * <p>⚠️ The time base must be the <b>server-wide tick</b> ({@code server.getTickCount()}),
 * <b>not</b> {@code p.tickCount}.
 *
 * <p>State lives on {@link PlayerState#joinTick}.
 */
public final class SpawnProtection {

    private SpawnProtection() {}

    /** Recorded when the player enters the world (server-wide tick, not p.tickCount) */
    public static void onJoin(ServerPlayer p) {
        if (p.server == null) return;
        PlayerState.of(p).joinTick = p.server.getTickCount();
    }

    /** Whether the player is currently inside the spawn protection window */
    public static boolean isProtected(ServerPlayer p) {
        if (!ModConfig.SPAWN_PROTECT.get()) return false;
        PlayerState s = PlayerState.find(p.getUUID());
        if (s == null || s.joinTick < 0) return false;
        return withinWindow(p, s);
    }

    private static boolean withinWindow(ServerPlayer p, PlayerState s) {
        if (p.server == null) return false;
        int ticks = ModConfig.SPAWN_PROTECT_TICKS.get();
        if (ticks <= 0) return false;
        int elapsed = p.server.getTickCount() - s.joinTick;
        // Defensive: if joinTick ever comes from the "future" (server tick wraparound,
        // save switch, etc.), treat the window as already elapsed — never let a negative
        // delta turn into "permanent protection"
        if (elapsed < 0) {
            s.joinTick = -1;
            return false;
        }
        return elapsed < ticks;
    }
}

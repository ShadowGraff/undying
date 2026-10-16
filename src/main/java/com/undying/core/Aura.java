package com.undying.core;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.monster.Enemy;

/**
 * Aura.
 *
 * <p>When triggered manually (chat trigger word) or left permanently on via config
 * ({@code deathAura}), hostile mobs within a certain radius of the player are cleared
 * every tick.
 *
 * <p>Two modes: {@code TIMED} (time-limited, with countdown announcements) and
 * {@code INFINITE} (always on). INFINITE takes priority over TIMED —— issuing a timed
 * command while the aura is already permanently on will not downgrade it.
 *
 * <p>The state lives in {@link PlayerState#auraMode} / {@link PlayerState#auraTimer}.
 */
public final class Aura {

    private Aura() {}

    public enum Mode { OFF, TIMED, INFINITE }

    /** Set the mode (INFINITE is not overwritten by TIMED) */
    public static void set(ServerPlayer p, Mode mode) {
        PlayerState s = PlayerState.of(p);
        if (s.auraMode == Mode.INFINITE && mode == Mode.TIMED) return;
        s.auraMode = mode;
        s.auraTimer = (mode == Mode.TIMED) ? Const.AURA_DURATION_TICKS : 0;
    }

    /** Manual stop */
    public static void stop(ServerPlayer p) {
        PlayerState s = PlayerState.of(p);
        s.auraMode = Mode.OFF;
        s.auraTimer = 0;
    }

    /**
     * Advance one step per tick.
     *
     * @return the mode <i>before this call was entered</i>. Callers rely on it to decide
     *         "whether the automatic aura should take over" —— on the very tick the
     *         timer expires, the returned value is still TIMED, matching the behaviour
     *         before the refactor.
     */
    public static Mode tick(ServerPlayer p) {
        PlayerState s = PlayerState.of(p);
        Mode mode = s.auraMode;
        if (mode == Mode.OFF) return Mode.OFF;

        if (mode == Mode.TIMED) {
            s.auraTimer--;
            if (s.auraTimer <= 0) {
                s.auraMode = Mode.OFF;
                s.auraTimer = 0;
                Msg.info(p, UndyingMod.TAG).text("Aura: OFF").actionBar().send();
            } else if (s.auraTimer % Const.TICKS_PER_SECOND == 0) {
                Msg.info(p, UndyingMod.TAG).text("Aura ").dim("remaining ")
                        .num(s.auraTimer / Const.TICKS_PER_SECOND).text("s").actionBar().send();
            }
        }

        if (s.auraMode != Mode.OFF) killHostilesNear(p);
        return mode;
    }

    /** Clear hostile mobs within {@code Const.AURA_RANGE} of the player */
    public static void killHostilesNear(ServerPlayer p) {
        for (Mob m : p.level().getEntitiesOfClass(Mob.class,
                p.getBoundingBox().inflate(Const.AURA_RANGE),
                m -> m.isAlive() && m.getId() != p.getId() && isHostile(m))) {
            ForceKill.kill(m);
        }
    }

    /**
     * Whether this counts as "hostile".
     *
     * <p>Vanilla classifies the ender dragon / wither / warden under MONSTER, but they
     * are bosses, and sweeping them away in passing would break game progression, so
     * they are excluded explicitly.
     */
    private static boolean isHostile(Mob m) {
        EntityType<?> t = m.getType();
        return (m instanceof Enemy || t.getCategory() == MobCategory.MONSTER)
                && t != EntityType.ENDER_DRAGON
                && t != EntityType.WITHER
                && t != EntityType.WARDEN;
    }
}

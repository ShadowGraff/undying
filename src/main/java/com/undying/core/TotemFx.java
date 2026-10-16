package com.undying.core;

import net.minecraft.server.level.ServerPlayer;

/**
 * Totem effect cooldown.
 *
 * <p>Background: the hidden totem is a "real totem" —— on death a totem of undying is
 * temporarily placed into the offhand so vanilla runs the complete flow. The cost is
 * that vanilla's <i>sound + particles</i> blast out every single time, and the
 * particles also smother the view (the user complained about this explicitly).
 *
 * <p>The sound is already muted at the client-side SoundEngine layer
 * ({@code MixinTotemSoundMute}). The particles are written inline in vanilla's
 * {@code checkTotemDeathProtection} and cannot be reduced without rewriting vanilla
 * logic —— so the approach taken is a <i>cooldown</i>:
 * <i>no totem injected during the cooldown → that vanilla branch is never taken →
 * naturally no particles.</i>
 *
 * <p>⚠️ Visual only. The death lock, health gate and death-recovery checks all keep
 * working; the player still cannot die.
 *
 * <p>The state lives in {@link PlayerState#lastTotemVisualTick}.
 */
public final class TotemFx {

    private TotemFx() {}

    /** Cooldown 40 ticks = 2 seconds */
    private static final int COOLDOWN_TICKS = 40;

    /**
     * Whether this player is currently in the totem visual cooldown.
     *
     * <p>When true, the caller should skip totem injection and the related visual
     * effects, but <b>must not</b> skip the life-saving logic.
     *
     * @param p    the player
     * @param mark whether to also refresh the cooldown (pass true on the first check, false on repeated queries)
     */
    public static boolean inCooldown(ServerPlayer p, boolean mark) {
        if (p == null) return false;
        try {
            PlayerState s = PlayerState.of(p);
            int now = p.tickCount;
            // ⚠️ The initial value of lastTotemVisualTick is Integer.MIN_VALUE.
            //    Subtracting directly overflows into a negative number (< COOLDOWN_TICKS
            //    holds true), so "never triggered" would be misread as "cooling down".
            //    The initial value must be checked first.
            if (s.lastTotemVisualTick != Integer.MIN_VALUE
                    && now - s.lastTotemVisualTick < COOLDOWN_TICKS) {
                return true;
            }
            if (mark) s.lastTotemVisualTick = now;
        } catch (Throwable t) {
            ModLog.swallowed("TotemFx.inCooldown", t);
        }
        return false;
    }
}

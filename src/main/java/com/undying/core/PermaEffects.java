package com.undying.core;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/**
 * Permanent buffs: Absorption / Regeneration / Resistance / Fire Resistance.
 *
 * <p>⚠️ <b>Night Vision and Saturation are deliberately excluded.</b>
 * Night Vision changes the brightness and color tone of the entire screen; Saturation
 * hides the hunger bar and covers part of the HUD. Both "affect the view / obscure the
 * interface" — <b>defense should not come at the cost of the interface.</b>
 *
 * <p>The four that are kept are purely numeric and do not change how the screen looks.
 */
public final class PermaEffects {

    private PermaEffects() {}

    public static void tick(ServerPlayer p, boolean master) {
        if (!master || !ModConfig.PERMA_EFFECTS.get()) return;

        // All four are present → return early. This path runs every tick, so do the cheapest check first
        if (p.getEffect(MobEffects.ABSORPTION) != null
                && p.getEffect(MobEffects.REGENERATION) != null
                && p.getEffect(MobEffects.DAMAGE_RESISTANCE) != null
                && p.getEffect(MobEffects.FIRE_RESISTANCE) != null) {
            return;
        }

        p.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, Const.EFFECT_INFINITE, 4, false, false));
        p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, Const.EFFECT_INFINITE, 4, false, false));
        p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, Const.EFFECT_INFINITE, 4, false, false));
        p.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, Const.EFFECT_INFINITE, 0, false, false));
    }
}

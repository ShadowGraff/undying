package com.undying.core.mixin;

import com.undying.core.Environment;
import com.undying.core.ModConfig;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Anti-tamper mode: stops other mods/commands from inflicting hostile effects on the player.
 *
 * Covers:
 *  1. Negative potion effects (Poison / Wither / Slowness / Weakness and other HARMFUL categories) → rejected
 *  2. Attribute tampering (max health, movement speed, etc. being maliciously reduced) → corrected periodically by UndyingMod
 *  3. Experience being drained → intercepts negative values in giveExperiencePoints
 *
 * Whitelist design: only active while immortal mode is on, and only for ServerPlayer.
 */
@Mixin(LivingEntity.class)
public abstract class MixinAntiTamper {

    // ① Immunity to harmful potion effects
    @Unique
    @Inject(method = "m_7292_", at = @At("HEAD"), cancellable = true, remap = false)
    private void undying$guardHarmfulEffect(MobEffectInstance effect, CallbackInfoReturnable<Boolean> cir) {
        if (!ModConfig.MASTER_TOGGLE.get() || !ModConfig.ANTI_TAMPER.get()) return;
        if (!((Object) this instanceof ServerPlayer p)) return;
        if (!com.undying.core.Environment.isAllowed(p)) return;   // single-player only
        if (effect == null) return;
        MobEffect type = effect.getEffect();
        if (type != null && type.getCategory() == MobEffectCategory.HARMFUL) {
            cir.setReturnValue(false); // reject applying the harmful effect
        }
    }
}

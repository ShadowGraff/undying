package com.undying.core;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

/**
 * Damage resolution ({@code LivingHurtEvent}).
 *
 * <p>Three branches; <b>the order is the priority order</b>:
 * <ol>
 *   <li>The Void Sword is swung → ignore everything, deal full damage, stop here</li>
 *   <li>Damage cap enabled → likewise deal full damage</li>
 *   <li>The player themselves is hurt → resolve <b>hidden gear</b> enchantments</li>
 * </ol>
 *
 * <p>Why branch 3 exists: hidden gear consists of purely logical items that sit
 * <b>outside the real armor slots</b>, so vanilla never reads their enchantments —
 * which meant the "Protection IV" applied by {@code HiddenGear} was dead code.
 * Here it is resolved manually using the vanilla formula, and only when the player
 * themselves takes damage.
 */
public final class DamageHandling {

    private DamageHandling() {}

    /** Value used when the damage cap option is enabled */
    private static final float MAX_DAMAGE = Float.MAX_VALUE;

    public static void onHurt(LivingHurtEvent event) {
        // ① Void Sword: ignore everything
        if (event.getSource().getEntity() instanceof ServerPlayer p && VoidSword.isHeld(p)) {
            event.setAmount(Const.VOID_DAMAGE);
            return;
        }

        // ② Damage cap
        if (event.getSource().getEntity() instanceof ServerPlayer
                && ImmortalMode.isOn() && ModConfig.DAMAGE_BOOST.get()) {
            event.setAmount(MAX_DAMAGE);
            return;
        }

        // ③ The player themselves is hurt: resolve hidden gear enchantments
        if (event.getEntity() instanceof ServerPlayer victim) {
            event.setAmount(applyHiddenEnchants(victim, event));
        }
    }

    private static float applyHiddenEnchants(ServerPlayer victim, LivingHurtEvent event) {
        float amount = event.getAmount();

        // Feather Falling: vanilla formula grants 12% per level, capped at 80%
        if (event.getSource().is(DamageTypeTags.IS_FALL)) {
            amount = HiddenEnchants.applyFeatherFalling(victim.getUUID(), amount);
        }

        // General Protection: vanilla formula grants 4% per level, capped at 80%
        return HiddenEnchants.applyProtection(victim.getUUID(), amount);
    }
}

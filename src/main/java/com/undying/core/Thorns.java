package com.undying.core;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ThornsEnchantment;

/**
 * Thorns retaliation.
 *
 * <p>It has to be run <b>manually</b>: the hidden ward shield provides "total immunity
 * to hurt", and Thorns is implemented inside the {@code hurt} flow — once the whole
 * hurt call is intercepted, Thorns would never trigger. So it is run manually before
 * the interception.
 *
 * <p>The two sources are handled separately:
 * <ol>
 *   <li>Thorns from real armor: goes through the vanilla entry point {@code doPostHurtEffects} (including the chance roll and the sound)</li>
 *   <li>Thorns from hidden armor: vanilla cannot read hidden slots, so it is resolved manually with the vanilla {@code ThornsEnchantment} formula</li>
 * </ol>
 */
public final class Thorns {

    private Thorns() {}

    public static void apply(ServerPlayer p, DamageSource src) {
        if (!(src.getEntity() instanceof LivingEntity attacker)) return;
        if (attacker.level().isClientSide) return;

        // ① Real armor: the vanilla entry point handles both the chance roll and the sound
        EnchantmentHelper.doPostHurtEffects(p, attacker);

        // ② Hidden armor: take the highest Thorns level among the four pieces
        ItemStack[] hidden = HiddenSlots.getArmor(p.getUUID());
        if (hidden == null) return;

        int level = 0;
        for (ItemStack s : hidden) {
            level = Math.max(level, EnchantmentHelper.getItemEnchantmentLevel(Enchantments.THORNS, s));
        }
        if (level <= 0) return;

        if (!ThornsEnchantment.shouldHit(level, attacker.getRandom())) return;
        attacker.hurt(p.damageSources().thorns(p),
                (float) ThornsEnchantment.getDamage(level, attacker.getRandom()));
    }
}

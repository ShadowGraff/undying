package com.undying.core;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;

import java.util.UUID;

/**
 * Enchantment executor for the hidden gear.
 *
 * ═══════════════════════════════════════════════════════════
 *  Why it is needed (the core problem identified on 2026-10-07)
 * ═══════════════════════════════════════════════════════════
 *
 * The hidden gear (HiddenSlots.armor) consists of purely logical items that are [not in the
 * real armour slots]. The project has always enchanted them - preBuildGear() gave every piece
 *     Protection IV / Unbreaking III / Mending I
 * plus their own extra enchantments (Thorns, Respiration, Feather Falling, ...).
 *
 * Note: vanilla never reads the enchantments of items that are not in the armour slots.
 *    => **From the day they were added, all of these enchantments were dead; not one of them
 *    ever took effect.**
 *
 * Only [Thorns] has been wired up separately so far (see {@code Thorns.apply}:
 * it reads THORNS from the hidden armour by hand and applies the vanilla formula). This class
 * adds the remaining ones that are worth making effective.
 *
 * [Design principle: copy the vanilla formula, do not invent values]
 *
 * [Scope]
 *   Only the few entries that "have real survival value" are handled. Movement-related ones
 *   (Swift Sneak / Depth Strider / Soul Speed) affect feel and rendering and are left out per
 *   the user's consistent request to "not affect the view, and not be too conspicuous".
 *
 * [Parameter convention]
 *   Everything takes a UUID rather than a ServerPlayer - this class then depends on no entity
 *   class at all, avoiding class loading where it is not needed (this project has been burned before).
 */
public final class HiddenEnchants {

    private HiddenEnchants() {}

    /** Highest level of a given enchantment on the hidden armour (0 if absent) */
    private static int level(UUID id, Enchantment ench) {
        ItemStack[] hidden = HiddenSlots.getArmor(id);
        if (hidden == null) return 0;
        int max = 0;
        for (ItemStack s : hidden) {
            if (s == null) continue;
            int lv = EnchantmentHelper.getItemEnchantmentLevel(ench, s);
            if (lv > max) max = lv;
        }
        return max;
    }

    /** Sum of the Protection levels across the four hidden armour pieces (may be 0) */
    private static int totalProtectionLevels(UUID id) {
        ItemStack[] hidden = HiddenSlots.getArmor(id);
        if (hidden == null) return 0;
        int total = 0;
        for (ItemStack s : hidden) {
            if (s == null) continue;
            total += EnchantmentHelper.getItemEnchantmentLevel(
                    Enchantments.ALL_DAMAGE_PROTECTION, s);
        }
        return total;
    }

    /**
     * Protection (general damage reduction).
     *
     * Vanilla formula (ProtectionEnchantment.getDamageProtection):
     *     reduction = level x 4%, capped at 80%
     *
     * Fully equipped, all four hidden armour pieces carry Protection IV, 16 levels in total -> 64% reduction.
     * Note: this is what the vanilla formula yields, not extra power we added.
     *
     * @param amount raw damage
     * @return the damage after reduction (never below 0)
     */
    public static float applyProtection(UUID id, float amount) {
        try {
            if (amount <= 0.0F) return amount;
            int total = totalProtectionLevels(id);
            if (total <= 0) return amount;

            float reduction = Math.min(total * 0.04F, 0.80F);
            return Math.max(amount * (1.0F - reduction), 0.0F);
        } catch (Throwable t) {
            return amount;  // on error, apply no reduction - never let an "enchantment" harm the player
        }
    }

    /**
     * Feather Falling.
     *
     * Vanilla formula (EnchantmentHelper.calculateFallDamageReduction):
     *     reduction = level x 12%, capped at 80%
     */
    public static float applyFeatherFalling(UUID id, float fallDamage) {
        try {
            if (fallDamage <= 0.0F) return fallDamage;
            int lv = level(id, Enchantments.FALL_PROTECTION);
            if (lv <= 0) return fallDamage;

            float reduction = Math.min(lv * 0.12F, 0.80F);
            return Math.max(fallDamage * (1.0F - reduction), 0.0F);
        } catch (Throwable t) {
            return fallDamage;
        }
    }

    /** Respiration level on the hidden helmet (0 = none) */
    public static int respirationLevel(UUID id) {
        return level(id, Enchantments.RESPIRATION);
    }

    /** Aqua Affinity level on the hidden helmet */
    public static int aquaAffinityLevel(UUID id) {
        return level(id, Enchantments.AQUA_AFFINITY);
    }
}

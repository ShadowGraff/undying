package com.undying.core;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;

/**
 * Ward Shield: an unbreakable shield sharing its origin with the Void Sword.
 * - Durability -1 (infinite), cannot be damaged
 * - Always has the enchantment glint (isFoil)
 * - Damage immunity is implemented by MixinLivingEntity intercepting hurt():
 *   immunity regardless of whether it is raised, regardless of damage type
 *   (including magic), and regardless of direction
 */
public class WardShieldItem extends ShieldItem {
    public WardShieldItem() {
        super(new Item.Properties().stacksTo(1).fireResistant());
    }

    @Override
    public boolean isDamageable(ItemStack stack) { return false; }

    @Override
    public int getMaxDamage(ItemStack stack) { return -1; }

    @Override
    public boolean isFoil(ItemStack stack) { return true; }
}

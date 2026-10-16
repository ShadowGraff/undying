package com.undying.core;

import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;

/**
 * The Void Sword.
 *
 * <p>An unbreakable sword whose attack attributes are pushed to the integer limit,
 * so a single hit is always lethal through the vanilla damage chain.
 *
 * <p>It is marked as fire resistant and always renders with the enchanted glint.
 * Combine it with {@link VoidSword} for the right-click area sweep and the
 * attack-triggered execution chain.
 */
public class VoidSwordItem extends SwordItem {
    public VoidSwordItem() {
        super(Tiers.WOOD, 0, -2.4F, new Item.Properties().stacksTo(1).fireResistant());
    }

    @Override
    public boolean isDamageable(ItemStack stack) { return false; }

    @Override
    public int getMaxDamage(ItemStack stack) { return -1; }

    @Override
    public Multimap<Attribute, AttributeModifier> getDefaultAttributeModifiers(EquipmentSlot slot) {
        if (slot == EquipmentSlot.MAINHAND) {
            return ImmutableMultimap.<Attribute, AttributeModifier>builder()
                    .put(Attributes.ATTACK_DAMAGE, new AttributeModifier(BASE_ATTACK_DAMAGE_UUID, "Void damage", Integer.MAX_VALUE - 1, AttributeModifier.Operation.ADDITION))
                    .put(Attributes.ATTACK_SPEED, new AttributeModifier(BASE_ATTACK_SPEED_UUID, "Void speed", 100.0, AttributeModifier.Operation.ADDITION))
                    .build();
        }
        return super.getDefaultAttributeModifiers(slot);
    }

    @Override
    public boolean isFoil(ItemStack stack) { return true; } // glint like enchanted
}

package com.undying.core;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Construction and distribution of the god-gear templates.
 *
 * <p>Moved out of UndyingMod (2026-10-11 skeleton refactor, Pass A).
 * These methods used to be crammed into the corner of a god class, mixed in with
 * the command tree and the event handlers.
 *
 * <p>Two things live here:
 * <ul>
 *   <li>{@link #preBuild()} —— build the templates. Must be called after
 *       FMLCommonSetupEvent, otherwise RegistryObject.get() throws an NPE
 *       because the registries are not populated yet.</li>
 *   <li>{@link #giveTo(ServerPlayer)} —— handed out once, the first time the
 *       player enters the world of a given save.</li>
 * </ul>
 *
 * <p>Note: the god gear <i>can be taken off and dropped</i>. This only handles the
 * initial handout; there is no per-tick re-equipping. The real damage immunity
 * comes from the hidden slots in HiddenSlots, and is unrelated to the visible gear
 * the player is wearing.
 */
public final class HiddenGear {

    private HiddenGear() {}

    /** Armor templates: helmet / chestplate / leggings / boots */
    private static final Map<EquipmentSlot, ItemStack> ARMOR = new EnumMap<>(EquipmentSlot.class);

    /** Tool templates: sword / pickaxe / axe / shovel */
    private static ItemStack[] TOOLS = new ItemStack[0];

    /** Pre-build every template (the templates themselves are immutable; copies are made when handing out) */
    public static void preBuild() {
        ARMOR.put(EquipmentSlot.HEAD, armor(Items.NETHERITE_HELMET,
                Enchantments.AQUA_AFFINITY, 1, Enchantments.RESPIRATION, 3, Enchantments.THORNS, 3));
        ARMOR.put(EquipmentSlot.CHEST, armor(Items.NETHERITE_CHESTPLATE,
                Enchantments.THORNS, 3));
        ARMOR.put(EquipmentSlot.LEGS, armor(Items.NETHERITE_LEGGINGS,
                Enchantments.THORNS, 3, Enchantments.SWIFT_SNEAK, 3));
        ARMOR.put(EquipmentSlot.FEET, armor(Items.NETHERITE_BOOTS,
                Enchantments.THORNS, 3, Enchantments.DEPTH_STRIDER, 3,
                Enchantments.FALL_PROTECTION, 4, Enchantments.SOUL_SPEED, 3));
        // The ward shield is no longer placed in the visible offhand —— it became a
        // hidden third offhand (HiddenSlots.shield), where MixinLivingEntity.guardWardShield
        // provides 360° immunity to all damage

        TOOLS = new ItemStack[]{
                tool(Items.NETHERITE_SWORD, Enchantments.SHARPNESS, 5, Enchantments.MOB_LOOTING, 3,
                        Enchantments.FIRE_ASPECT, 2, Enchantments.SWEEPING_EDGE, 3, Enchantments.KNOCKBACK, 2),
                tool(Items.NETHERITE_PICKAXE, Enchantments.BLOCK_EFFICIENCY, 5, Enchantments.BLOCK_FORTUNE, 3),
                tool(Items.NETHERITE_AXE, Enchantments.BLOCK_EFFICIENCY, 5, Enchantments.BLOCK_FORTUNE, 3),
                tool(Items.NETHERITE_SHOVEL, Enchantments.BLOCK_EFFICIENCY, 5, Enchantments.BLOCK_FORTUNE, 3),
        };
    }

    /**
     * Armor templates (read-only).
     *
     * <p>Used by HiddenSlots: the hidden armor consists of independent copies of these
     * templates. The unmodifiable view is deliberate —— this used to be a public
     * mutable static Map that anything could stuff entries into, a textbook case of
     * "globally mutable state" hazard.
     */
    public static Map<EquipmentSlot, ItemStack> armorTemplates() {
        return Collections.unmodifiableMap(ARMOR);
    }

    /** Give the player the full god gear: armor into the equipment slots, tools into empty inventory slots */
    public static void giveTo(ServerPlayer p) {
        for (Map.Entry<EquipmentSlot, ItemStack> e : ARMOR.entrySet()) {
            p.setItemSlot(e.getKey(), e.getValue().copy());
        }
        for (ItemStack t : TOOLS) {
            int slot = emptySlot(p);
            if (slot >= 0) p.getInventory().setItem(slot, t.copy());
        }
        p.getInventory().setChanged();
    }

    private static int emptySlot(ServerPlayer p) {
        for (int i = 0; i < p.getInventory().items.size(); i++) {
            if (p.getInventory().items.get(i).isEmpty()) return i;
        }
        return -1;
    }

    /** Armor template: Protection IV + Unbreaking III + Mending I for all pieces, plus the caller-supplied enchantments */
    private static ItemStack armor(Item item, Object... extra) {
        Map<Enchantment, Integer> m = new LinkedHashMap<>();
        m.put(Enchantments.ALL_DAMAGE_PROTECTION, 4);
        m.put(Enchantments.UNBREAKING, 3);
        m.put(Enchantments.MENDING, 1);
        // Note: no Curse of Binding anymore —— the god gear can be taken off and dropped
        applyExtra(m, extra);
        ItemStack s = new ItemStack(item);
        EnchantmentHelper.setEnchantments(m, s);
        return s;
    }

    /** Tool template: Unbreaking III + Mending I, plus the caller-supplied enchantments */
    private static ItemStack tool(Item item, Object... extra) {
        Map<Enchantment, Integer> m = new LinkedHashMap<>();
        m.put(Enchantments.UNBREAKING, 3);
        m.put(Enchantments.MENDING, 1);
        applyExtra(m, extra);
        ItemStack s = new ItemStack(item);
        EnchantmentHelper.setEnchantments(m, s);
        return s;
    }

    /** extra is a varargs list of (Enchantment, Integer) pairs */
    private static void applyExtra(Map<Enchantment, Integer> target, Object... extra) {
        for (int i = 0; i + 1 < extra.length; i += 2) {
            target.put((Enchantment) extra[i], (Integer) extra[i + 1]);
        }
    }
}

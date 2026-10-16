package com.undying.core;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Hidden slot system: permanent equipment the player cannot see, touch, drop, or have
 * stripped away by commands.
 *
 * <p>It is implemented as <i>purely logical slots</i> —— the items never enter the real
 * inventory, so "cannot be dropped / cannot be cleared by commands" follows naturally:
 * the items simply do not exist in the inventory.
 *
 * <ul>
 *   <li>{@code totem} —— third offhand: hidden totem of undying. Combined with totem
 *       injection, it lets the vanilla mechanics fire the event/sound/particles
 *       normally on death.</li>
 *   <li>{@code shield} —— second offhand: hidden ward shield. Combined with hurt
 *       interception, it provides 360° immunity to all damage, permanent uptime and
 *       infinite durability.</li>
 *   <li>{@code armor} —— hidden armor (a second set of god gear). It does not enter the
 *       real armor slots; via {@code getArmorValue} injection the full netherite set's
 *       armor value (20) is added to the player.</li>
 * </ul>
 *
 * <p>The state lives in {@link PlayerState#hiddenSlots}.
 */
public final class HiddenSlots {

    private HiddenSlots() {}

    public static final class Slot {
        public final ItemStack totem = new ItemStack(Items.TOTEM_OF_UNDYING);
        /** Third offhand: hidden ward shield (can only be obtained after item registration completes) */
        public ItemStack shield;
        /** Hidden armor: copies of the HiddenGear armor templates (4 pieces) */
        public ItemStack[] armor;
    }

    /** Initialize the hidden slots on joining the server / entering the world */
    public static void init(ServerPlayer p) {
        Slot s = new Slot();
        s.shield = new ItemStack(UndyingMod.WARD_SHIELD.get());
        var templates = HiddenGear.armorTemplates();
        s.armor = new ItemStack[templates.size()];
        int i = 0;
        for (var e : templates.entrySet()) {
            s.armor[i++] = e.getValue().copy();
        }
        PlayerState.of(p).hiddenSlots = s;
    }

    // ⚠️ The three checks below are currently identical in implementation (all are
    //    "have the hidden slots been initialized").
    //    All three names are kept because they read more clearly at the call sites
    //    ("is there a shield" vs "is there armor").
    //    If they ever genuinely need to be distinguished, Slot must first support
    //    independent initialization ——
    //    otherwise this becomes a trap where the names lie.

    public static boolean hasTotem(java.util.UUID id) {
        return initialized(id);
    }

    public static boolean hasShield(java.util.UUID id) {
        return initialized(id);
    }

    public static boolean hasArmor(java.util.UUID id) {
        return initialized(id);
    }

    private static boolean initialized(java.util.UUID id) {
        PlayerState s = PlayerState.find(id);
        return s != null && s.hiddenSlots != null;
    }

    /** Returns the hidden armor array (null when not initialized) */
    public static ItemStack[] getArmor(java.util.UUID id) {
        PlayerState s = PlayerState.find(id);
        return (s == null || s.hiddenSlots == null) ? null : s.hiddenSlots.armor;
    }
}

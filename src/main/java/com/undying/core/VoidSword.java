package com.undying.core;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * The Void Sword.
 *
 * <p>Three responsibilities: granting, right-click area sweep, and starting the execution
 * chain on attack.
 *
 * <p>⚠️ Attacks do <b>not treat Player-type targets specially</b>. This code used to
 * "hide each other" to avoid PvP, which mods that register mobs as player types then
 * exploited as a death-immunity trick.
 */
public final class VoidSword {

    private VoidSword() {}

    /** Whether the player is holding the Void Sword in their main hand */
    public static boolean isHeld(ServerPlayer p) {
        return p.getMainHandItem().getItem() == UndyingMod.VOID_SWORD.get();
    }

    /** Grant it: prefer an empty inventory slot, never displace what is already in hand */
    public static void give(ServerPlayer p) {
        Item swordItem = UndyingMod.VOID_SWORD.get();
        if (hasItem(p, swordItem)) {
            Msg.warn(p, UndyingMod.TAG).bi("已经拥有虚空剑", "Already have the Void Sword").send();
            return;
        }
        ItemStack sword = new ItemStack(swordItem);
        sword.setHoverName(Component.literal("§5Void Sword"));
        if (!p.getInventory().add(sword)) {
            Msg.warn(p, UndyingMod.TAG).bi("背包已满", "Inventory full").send();
            return;
        }
        p.getInventory().setChanged();
        Msg.ok(p, UndyingMod.TAG).bi("已获得虚空剑", "Void Sword acquired").send();
    }

    /** Right-click: clears every mob within {@code Const.SWORD_SWEEP_RANGE} (animals/neutrals included) */
    public static void sweep(ServerPlayer p) {
        int n = 0;
        for (Mob m : p.level().getEntitiesOfClass(Mob.class,
                p.getBoundingBox().inflate(Const.SWORD_SWEEP_RANGE),
                m -> m.isAlive() && m.getId() != p.getId())) {
            ForceKill.kill(m);
            n++;
        }
        Msg.ok(p, UndyingMod.TAG)
                .bi("已清除 " + n + " 个实体", "Cleared " + n + " entities").send();
    }

    /** Attack: starts the execution chain (does nothing if one is already running) */
    public static void startExecution(ServerPlayer p, Entity target) {
        if (target == null || !target.isAlive()) return;
        if (!Execution.start(p, target)) return;
        Msg.info(p, "exec").bi("目标：", "target: ").dim(target.getName().getString()).send();
    }

    private static boolean hasItem(ServerPlayer p, Item item) {
        for (ItemStack s : p.getInventory().items) if (s.is(item)) return true;
        for (ItemStack s : p.getInventory().armor) if (s.is(item)) return true;
        return p.getInventory().offhand.get(0).is(item);
    }
}

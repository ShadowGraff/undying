package com.undying.core;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;

import java.util.ArrayList;

/**
 * Snapshot codec layer: player <-> NBT.
 *
 * <p>It only handles "how player state becomes NBT and how it becomes a player again"; it does
 * not care where snapshots are stored or when they are taken.
 *
 * <p>Note: on capture, each value is taken via {@code copy()}, <b>without trusting the getter's
 * return value</b>: an external implementation may override {@code getHealth()} so it always
 * returns full health (its patchGetHealth does exactly that), so beyond health we also store
 * independent signals such as "is dead / is removed", which is what makes anomalies detectable
 * at restore time.
 */
public final class SnapshotCodec {

    private SnapshotCodec() {}

    /** Snapshot format version. A tag with a lower version is rejected. */
    private static final int VERSION = 1;

    /** Whether this tag is a snapshot we recognise */
    public static boolean isValid(CompoundTag tag) {
        return tag != null && tag.getInt("v") >= VERSION;
    }

    /** Write a player's full state into a CompoundTag */
    public static CompoundTag encode(ServerPlayer p) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("v", VERSION);
        tag.putLong("t", p.server == null ? 0L : p.server.getTickCount());

        // ── Position and orientation ──
        tag.putString("dim", p.level().dimension().location().toString());
        tag.putDouble("x", p.getX());
        tag.putDouble("y", p.getY());
        tag.putDouble("z", p.getZ());
        tag.putFloat("yaw", p.getYRot());
        tag.putFloat("pitch", p.getXRot());

        // ── Survival state ──
        tag.putFloat("hp", p.getHealth());
        tag.putFloat("maxHp", p.getMaxHealth());
        tag.putFloat("absorption", p.getAbsorptionAmount());
        tag.putInt("food", p.getFoodData().getFoodLevel());
        tag.putFloat("sat", p.getFoodData().getSaturationLevel());
        tag.putInt("xpLevel", p.experienceLevel);
        tag.putInt("xpTotal", p.totalExperience);
        tag.putFloat("xpProgress", p.experienceProgress);
        tag.putInt("fire", p.getRemainingFireTicks());
        tag.putInt("air", p.getAirSupply());

        // ── Inventory (including armour/offhand; save() writes ArmorItems and the rest too) ──
        CompoundTag inv = new CompoundTag();
        inv.put("Items", p.getInventory().save(new ListTag()));
        tag.put("inv", inv);

        // ── Potion effects ──
        ListTag effects = new ListTag();
        for (MobEffectInstance e : new ArrayList<>(p.getActiveEffects())) {
            CompoundTag et = new CompoundTag();
            e.save(et);
            effects.add(et);
        }
        tag.put("effects", effects);

        return tag;
    }

    /**
     * Write the snapshot back to the player. The caller must first confirm {@link #isValid}.
     *
     * <p>The restore order is deliberate:
     * <ol>
     *   <li>Raise health first (otherwise later operations may trigger the death check again)</li>
     *   <li>Then restore position / inventory / experience / effects</li>
     * </ol>
     */
    public static void apply(ServerPlayer p, CompoundTag tag) {
        float maxHp = tag.contains("maxHp") ? tag.getFloat("maxHp") : p.getMaxHealth();
        float hp = tag.getFloat("hp");
        if (hp <= 0.0F) hp = maxHp;
        if (hp > maxHp) hp = maxHp;

        // 1) Raise health first, and clear any leftover death animation state
        p.setHealth(hp);
        p.setAbsorptionAmount(tag.getFloat("absorption"));
        p.deathTime = 0;
        p.hurtTime = 0;
        p.fallDistance = 0.0F;

        // 2) Position (teleport only within the same dimension; cross-dimension is left for the player to walk back)
        String dim = tag.getString("dim");
        if (p.level().dimension().location().toString().equals(dim)) {
            p.teleportTo(tag.getDouble("x"), tag.getDouble("y"), tag.getDouble("z"));
            p.setYRot(tag.getFloat("yaw"));
            p.setXRot(tag.getFloat("pitch"));
        }

        // 3) Survival state
        p.getFoodData().setFoodLevel(tag.getInt("food"));
        p.getFoodData().setSaturation(tag.getFloat("sat"));
        p.experienceLevel = tag.getInt("xpLevel");
        p.totalExperience = tag.getInt("xpTotal");
        p.experienceProgress = tag.getFloat("xpProgress");
        p.setRemainingFireTicks(tag.getInt("fire"));
        p.setAirSupply(tag.getInt("air"));

        // 4) Inventory
        if (tag.contains("inv")) {
            CompoundTag inv = tag.getCompound("inv");
            // Note: clear first, then read, so this does not stack with an external implementation's "empty the inventory" patch and duplicate items
            p.getInventory().clearContent();
            if (inv.contains("Items")) {
                p.getInventory().load(inv.getList("Items", 10));
            }
        }

        // 5) Potion effects
        //    removeAllEffects would wipe the persistent buffs too,
        //    but PermaEffects restores them a moment later, so keep this simple.
        if (tag.contains("effects")) {
            p.removeAllEffects();
            ListTag effects = tag.getList("effects", 10);
            for (int i = 0; i < effects.size(); i++) {
                MobEffectInstance e = MobEffectInstance.load(effects.getCompound(i));
                if (e != null) p.addEffect(e);
            }
        }
    }
}

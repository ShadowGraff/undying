package com.undying.core.mixin;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Accessor: exposes LivingEntity's private death timer f_20919_.
 *
 * Vanilla tickDeath() increments it every tick, and once it reaches >= 20 (1 second)
 * it calls remove(KILLED). It starts accumulating when health is brought to 0; when our
 * death lock sets health back to 1.0 it does not reset on its own, so remove is re-issued
 * every second → we intercept it → the player is stuck "half alive, half dead" forever.
 * Clearing it lets a stuck entity exit the death animation immediately.
 *
 * The field name was verified against the bytecode of the 1.20.1 release LivingEntity
 * (javap output):
 *   public int f_20919_;
 */
@Mixin(LivingEntity.class)
public interface LivingEntityDeathTimerAccess {

    @Accessor(value = "f_20919_", remap = false)
    void undying$setDeathTime(int value);
}

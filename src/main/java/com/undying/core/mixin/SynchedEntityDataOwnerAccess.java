package com.undying.core.mixin;

import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Accessor: retrieves the entity a SynchedEntityData belongs to.
 *
 * MixinSynchedEntityDataHealth needs to know whose synced data this is in order to
 * decide whether its health should be protected.
 *
 * The field name f_135344_ was verified against the bytecode of the 1.20.1 release
 * SynchedEntityData (javap output:
 * `private final net.minecraft.world.entity.Entity f_135344_;`).
 */
@Mixin(SynchedEntityData.class)
public interface SynchedEntityDataOwnerAccess {

    @Accessor(value = "f_135344_", remap = false)
    Entity undying$getOwner();
}

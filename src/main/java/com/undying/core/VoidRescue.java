package com.undying.core;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Void rescue: falling below {@code Const.VOID_Y_THRESHOLD} teleports the player back to a safe point.
 *
 * <p>Preferably to the <b>bed / respawn anchor</b>; if none was set, to the world spawn.
 * The dimension follows along — a bed set in the Nether teleports back to the Nether.
 */
public final class VoidRescue {

    private VoidRescue() {}

    /**
     * @return true = the player has already been rescued, and the caller should abort the
     *         rest of this tick's processing (continuing makes no sense, the player has
     *         already been teleported away)
     */
    public static boolean tick(ServerPlayer p, boolean master) {
        if (!master || !ModConfig.VOID_RESCUE.get()) return false;
        if (p.getY() >= Const.VOID_Y_THRESHOLD) return false;

        ServerLevel here = p.serverLevel();
        BlockPos respawn = p.getRespawnPosition();
        ServerLevel dim = respawn != null ? p.server.getLevel(p.getRespawnDimension()) : null;
        boolean hasBed = respawn != null && dim != null;

        BlockPos dest = hasBed ? respawn : here.getSharedSpawnPos();
        ServerLevel target = hasBed ? dim : here;

        p.teleportTo(target, dest.getX() + 0.5, dest.getY() + 1, dest.getZ() + 0.5,
                p.getYRot(), p.getXRot());
        return true;
    }
}

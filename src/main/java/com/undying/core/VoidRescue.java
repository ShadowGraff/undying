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
        if (!master || !ProtectionBaseline.voidRescue()) return false;
        if (p.getY() >= Const.VOID_Y_THRESHOLD) return false;

        // One rescue per second. Without this, a rescue that fails to move the player (blocked,
        // wrong dimension) re-fires every tick and floods both the log and the chat line.
        PlayerState st = PlayerState.of(p);
        // Server tick, not the player's own counter: tickCount restarts at 0 on respawn and on
        // dimension change, which turns a 20-tick cooldown into "never again" (negative delta).
        int now = p.server != null ? p.server.getTickCount() : p.tickCount;
        if (now - st.lastVoidRescueTick < 20) return true;
        st.lastVoidRescueTick = now;

        ServerLevel here = p.serverLevel();
        BlockPos respawn = p.getRespawnPosition();
        ServerLevel dim = respawn != null ? p.server.getLevel(p.getRespawnDimension()) : null;
        boolean hasBed = respawn != null && dim != null;

        BlockPos dest = hasBed ? respawn : here.getSharedSpawnPos();
        ServerLevel target = hasBed ? dim : here;

        // Internal rescue: never asks the player, because answering "no" to being saved from
        // the void is not a choice anyone means to make. See TeleportGuard.rescueTeleport.
        TeleportGuard.rescueTeleport(p, target, dest.getX() + 0.5, dest.getY() + 1, dest.getZ() + 0.5,
                p.getYRot(), p.getXRot());
        return true;
    }
}

package com.undying.core;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.File;
import java.io.IOException;

/**
 * Storage layer for snapshots: NBT ↔ disk.
 *
 * <p>It only cares about "where the file is and how to read/write it"; it does not
 * care what is inside, nor when a snapshot should be taken. That keeps it separate
 * from the "policy", so changing the file format does not require touching the
 * scheduling logic.
 *
 * <p><b>Deliberately not routed through {@code SavedData} / the NBT sync
 * channel</b>: external implementations rewrite the methods on that chain, so
 * writing files directly under the world directory is the more reliable option.
 *
 * <p>File location: {@code <world directory>\_undying_backup\<player UUID>.dat}
 * (standard compressed NBT).
 */
public final class SnapshotStore {

    private SnapshotStore() {}

    /** Name of the backup subdirectory under the world directory */
    private static final String DIR_NAME = "_undying_backup";

    /**
     * Backup file path.
     *
     * @param mustExist when true, only returns a file that already exists (for restores);
     *                  when false, returns the target path (for writes)
     */
    private static File fileFor(ServerPlayer p, boolean mustExist) {
        ServerLevel level = p.serverLevel();
        File worldDir = level.getServer().getWorldPath(LevelResource.ROOT).toFile();
        File dir = new File(worldDir, DIR_NAME);
        if (mustExist && !dir.isDirectory()) return null;
        return new File(dir, p.getUUID() + ".dat");
    }

    /**
     * Whether the backup directory is usable (used by the self-check).
     *
     * <p>The decision is based on <b>the server's current world directory</b> and does
     * not depend on a player instance, so it can safely be called where there is no
     * player context (for example in the self-check loop).
     * It only determines "can the directory be created / written to" and does not
     * modify any existing file.
     */
    public static boolean dirUsable() {
        try {
            net.minecraft.server.MinecraftServer server =
                    net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
            if (server == null) return false;

            File worldDir = server.getWorldPath(LevelResource.ROOT).toFile();
            File dir = new File(worldDir, DIR_NAME);
            if (!dir.isDirectory() && !dir.mkdirs()) return false;
            return dir.canWrite();
        } catch (Throwable t) {
            ModLog.swallowed("SnapshotStore.dirUsable", t);
            return false;
        }
    }

    /**
     * Write one NBT tag to disk.
     *
     * <p>⚠️ <b>The IOException is deliberately propagated</b> rather than swallowed
     * here: the caller ({@code SaveSnapshot}) needs to know that "this write did not
     * succeed" in order to decide whether to update the fingerprint and whether to
     * report an error. The storage layer should not make that decision for the layer
     * above it.
     *
     * @return true = actually written out; false = path unusable (caller must not update the fingerprint)
     */
    public static boolean save(ServerPlayer p, CompoundTag tag) throws IOException {
        File f = fileFor(p, false);
        if (f == null) return false;
        File dir = f.getParentFile();
        if (dir != null && !dir.exists() && !dir.mkdirs()) return false;
        NbtIo.writeCompressed(tag, f);
        return true;
    }

    /**
     * Read one NBT tag from disk.
     *
     * @return the tag that was read; null when the file does not exist
     */
    public static CompoundTag read(ServerPlayer p) throws IOException {
        File f = fileFor(p, true);
        if (f == null || !f.exists()) return null;
        // In 1.20.1 NbtIo.readCompressed only has the single-argument (File) overload;
        // the overload taking an NbtAccounter was added in 1.20.2+, so do not copy the
        // newer version's style here.
        return NbtIo.readCompressed(f);
    }
}

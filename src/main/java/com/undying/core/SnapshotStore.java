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

        // Write-then-rename, never write in place.
        // A crash inside writeCompressed used to truncate the ONLY copy on disk, and
        // restore() then had nothing valid left to read: the file that was supposed to
        // survive a broken game was the least robust file in the mod. The previous good
        // snapshot is kept as .bak and read() falls back to it.
        // Still plain compressed NBT in all three files (D-016: players must be able to
        // open them with off-the-shelf NBT tools and roll back by hand).
        File tmp = new File(f.getPath() + ".tmp");
        File bak = new File(f.getPath() + ".bak");
        NbtIo.writeCompressed(tag, tmp);

        if (f.isFile()) {
            move(f, bak);   // the previous snapshot becomes the fallback copy
        }
        move(tmp, f);
        return true;
    }

    /** Rename within the snapshot directory, atomically where the filesystem allows it. */
    private static void move(File from, File to) throws IOException {
        try {
            java.nio.file.Files.move(from.toPath(), to.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            // Same-volume renames on NTFS are atomic. Where a filesystem refuses, losing
            // atomicity is not a reason to fail the whole snapshot — but it stays logged.
            ModLog.swallowed("SnapshotStore.move.atomic", e);
            java.nio.file.Files.move(from.toPath(), to.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * Read one NBT tag from disk.
     *
     * @return the tag that was read; null when the file does not exist
     */
    public static CompoundTag read(ServerPlayer p) throws IOException {
        File f = fileFor(p, true);
        if (f == null) return null;

        if (f.isFile()) {
            try {
                // In 1.20.1 NbtIo.readCompressed only has the single-argument (File)
                // overload; the NbtAccounter overload arrived in 1.20.2+, so do not copy
                // the newer version's style here.
                return NbtIo.readCompressed(f);
            } catch (Throwable t) {
                // A truncated primary means the game died mid-write. Falling back to the
                // previous snapshot loses at most one interval; giving up loses the player.
                ModLog.swallowed("SnapshotStore.read.primary", t);
                IntegrityGuard.LOGGER.warn(
                        "[snapshot] primary snapshot is unreadable; falling back to .bak");
            }
        }

        File bak = new File(f.getPath() + ".bak");
        if (bak.isFile()) {
            return NbtIo.readCompressed(bak);
        }
        return null;
    }

    /**
     * Read the previous snapshot ({@code .bak}) directly.
     *
     * <p>{@link #read} uses it only when the primary cannot be parsed. The caller also needs
     * it when the primary parses fine but is not a valid snapshot — a file that was replaced
     * rather than truncated — and that judgement belongs to the policy layer, not here.
     */
    public static CompoundTag readPrevious(ServerPlayer p) throws IOException {
        File f = fileFor(p, true);
        if (f == null) return null;
        File bak = new File(f.getPath() + ".bak");
        if (!bak.isFile()) return null;
        return NbtIo.readCompressed(bak);
    }
}

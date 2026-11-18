package com.undying.core.backup;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.stream.Stream;

/**
 * Where the copies live, and how the index gets in and out of the disk safely.
 *
 * <h3>Location, and why inside the world folder</h3>
 * {@code <world>/_undying_backup/world/}. Inside the world, because a player who copies their
 * world folder to another machine expects their backups to come along; a global folder elsewhere
 * would silently orphan them. {@link BackupFilter} excludes this directory from the scan, so the
 * recursion that would otherwise happen cannot.
 *
 * <h3>Every write is write-then-rename</h3>
 * The same rule as the player snapshots, for the same measured reason: a crash inside a direct
 * write leaves a truncated file, and a truncated index is worse than no index — it would make
 * every existing copy look like it belongs to nothing, and the next prune would happily delete
 * them.
 *
 * <p>{@code ATOMIC_MOVE} is attempted first and silently downgraded when the filesystem refuses
 * (some network shares do). Losing atomicity is not a reason to lose the backup; it is a reason
 * to keep the old file until the new one is complete, which the rename still provides.
 *
 * <h3>Failures are returned, not swallowed</h3>
 * {@code freeBytes} answers {@code -1} when it cannot tell. The caller must treat that as "do not
 * write" rather than "plenty of room", because guessing in the generous direction is how a
 * half-written backup gets created on a full disk.
 */
public final class BackupStore {

    private BackupStore() {}

    public static final String DIR_NAME = "_undying_backup";
    public static final String SUB_DIR = "world";
    private static final String INDEX_NAME = "index.txt";

    /** {@code <world>/_undying_backup/world}. */
    public static Path root(Path worldDir) {
        return worldDir.resolve(DIR_NAME).resolve(SUB_DIR);
    }

    public static Path indexFile(Path root) {
        return root.resolve(INDEX_NAME);
    }

    /** Read the index; a missing or unreadable file means "no copies yet", not an error. */
    public static BackupIndexCodec.Decoded loadIndex(Path root) {
        try {
            Path file = indexFile(root);
            if (!Files.isRegularFile(file)) return new BackupIndexCodec.Decoded(List.of(), 0);
            String text = Files.readString(file, StandardCharsets.UTF_8);
            return BackupIndexCodec.decode(text);
        } catch (Throwable t) {
            return new BackupIndexCodec.Decoded(List.of(), 0);
        }
    }

    /** Write the index atomically. Throws: the caller must know the write failed. */
    public static void saveIndex(Path root, List<BackupVersion> versions) throws IOException {
        Files.createDirectories(root);
        Path target = indexFile(root);
        Path tmp = root.resolve(INDEX_NAME + ".tmp");

        Files.writeString(tmp, BackupIndexCodec.encode(versions), StandardCharsets.UTF_8);

        try {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * Total bytes used by the backup folder.
     *
     * <p>Counts every regular file, including a {@code .tmp} left by a killed run: it occupies
     * space, so pretending it does not would make the budget wrong in the direction that fills
     * the disk.
     */
    public static long usedBytes(Path root) {
        if (root == null || !Files.isDirectory(root)) return 0L;
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(p -> Files.isRegularFile(p))
                    .mapToLong(p -> {
                        try {
                            return Files.size(p);
                        } catch (Throwable t) {
                            return 0L;
                        }
                    })
                    .sum();
        } catch (Throwable t) {
            return -1L;   // caller treats negative as "unknown", same as free space
        }
    }

    /**
     * Usable space on the volume that holds this path.
     *
     * @return bytes, or -1 when it cannot be determined
     */
    public static long freeBytes(Path anyExistingPath) {
        try {
            Path probe = anyExistingPath;
            while (probe != null && !Files.exists(probe)) probe = probe.getParent();
            if (probe == null) return -1L;
            return Files.getFileStore(probe).getUsableSpace();
        } catch (Throwable t) {
            return -1L;
        }
    }

    /**
     * Whether there is room for a copy of {@code incomingBytes}.
     *
     * <p>The margin is not paranoia: region files are copied while the game keeps writing them,
     * so the real cost creeps upward during the operation, and the filesystem needs headroom of
     * its own to do the rename. 20% plus 256 MB covers both and is small enough that a nearly
     * empty disk still gets a backup.
     */
    public static boolean hasRoomFor(long incomingBytes, Path probe) {
        long free = freeBytes(probe);
        if (free < 0) return false;                       // unknown: do not guess generously
        long needed = Math.max(0L, incomingBytes);
        long margin = (long) (needed * 0.2) + 256L * 1024 * 1024;
        return free > needed + margin;
    }
}

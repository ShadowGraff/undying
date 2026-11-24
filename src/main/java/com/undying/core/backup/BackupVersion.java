package com.undying.core.backup;

/**
 * One stored copy of one world file, as recorded in {@code index.json}.
 *
 * <h3>Why a record with a path and a timestamp, and nothing else</h3>
 * Every question the restore path asks is answerable from these four fields: "which version do I
 * use for this file at that moment" (path + seconds), "is it big enough to matter" (size), and
 * "is the copy still intact" (hash). Anything else — who triggered it, which mode produced it —
 * belongs to the index entry, not to the file version, because it does not change which copy
 * wins.
 *
 * <p>{@code hash} is nullable on purpose: backups taken with {@code verifyHashes = false} are
 * still valid backups. A missing hash means "unverified", not "broken", and the restore report
 * says so instead of pretending.
 *
 * @param path    path relative to the world directory, always {@code /}-separated
 * @param seconds unix seconds the copy was taken
 * @param size    size in bytes, used for the storage budget
 * @param hash    lowercase hex SHA-256, or null when hashing was off
 */
public record BackupVersion(String path, long seconds, long size, String hash, String copyName) {

    /**
     * Four-argument form for callers that do not care where the copy landed (tests, and any
     * index written before 2.9.3).
     */
    public BackupVersion(String path, long seconds, long size, String hash) {
        this(path, seconds, size, hash, null);
    }

    public BackupVersion {
        if (path == null || path.isEmpty()) throw new IllegalArgumentException("path");
        if (seconds < 0) throw new IllegalArgumentException("seconds");
        if (size < 0) throw new IllegalArgumentException("size");
    }

    /**
     * Folder name of this copy under {@code files/<path>/}.
     *
     * <h3>Why this is stored instead of derived</h3>
     * Two copies of the same file inside one second used to be distinguished on disk by a
     * {@code -1}, {@code -2} suffix while the index only kept the second — so the second copy was
     * written, listed, and then unreachable: the restore resolved both records to the same folder
     * and the pruner deleted the wrong one. Storing the actual name makes the record point at the
     * file it describes.
     *
     * <p>Nullable for indexes written before this field existed; those fall back to the timestamp,
     * which is exactly what they were written with.
     */
    public String copyName() {
        return copyName;
    }

    public boolean hasHash() {
        return hash != null && !hash.isEmpty();
    }

    /** Whether this copy exists at (or before) the given moment. */
    public boolean atOrBefore(long momentSeconds) {
        return seconds <= momentSeconds;
    }
}

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
public record BackupVersion(String path, long seconds, long size, String hash) {

    public BackupVersion {
        if (path == null || path.isEmpty()) throw new IllegalArgumentException("path");
        if (seconds < 0) throw new IllegalArgumentException("seconds");
        if (size < 0) throw new IllegalArgumentException("size");
    }

    public boolean hasHash() {
        return hash != null && !hash.isEmpty();
    }

    /** Whether this copy exists at (or before) the given moment. */
    public boolean atOrBefore(long momentSeconds) {
        return seconds <= momentSeconds;
    }
}

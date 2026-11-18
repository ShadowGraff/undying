package com.undying.core.backup;

/**
 * One file inside a world directory, as the scanner sees it.
 *
 * <p>Only three fields, because those are the only three the scheduler ever compares: the path to
 * identify it, {@code mtime} to notice it changed, and {@code size} to confirm it (a file can be
 * touched by a mod without its content changing, and an interrupted write can change size
 * without moving mtime on some filesystems).
 *
 * <p>{@code mtimeSeconds} is deliberately second-resolution: comparing milliseconds would make
 * "the same file, touched again" look like a change on filesystems that round differently, and
 * the scanner already runs at one-minute granularity.
 */
public record WorldFile(String path, long size, long mtimeSeconds) {

    public WorldFile {
        if (path == null || path.isEmpty()) throw new IllegalArgumentException("path");
        if (size < 0) throw new IllegalArgumentException("size");
    }
}

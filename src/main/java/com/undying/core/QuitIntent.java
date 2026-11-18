package com.undying.core;

/**
 * "The player asked to leave, just now."
 *
 * <h3>Why a timestamp and not a flag</h3>
 * A boolean would have to be cleared by whoever consumed it, and the two consumers (client
 * teardown, server stop) do not run in a fixed order — one would clear the flag before the other
 * read it. A timestamp cannot be consumed, only expired, so both sides can ask the same question
 * and get the same answer.
 *
 * <p>The window is generous on purpose: tearing a world down and stopping the server are
 * separated by level saving, which on a large world is not instant. Five seconds of tolerance
 * costs nothing — the situation it guards against ("a mod stops the server") is not something a
 * player is likely to do inside five seconds of pressing Save and Quit.
 *
 * <p>Lives in {@code core} rather than {@code client} so the server-side guard can read it
 * without linking a client-only class.
 */
public final class QuitIntent {

    private QuitIntent() {}

    private static final long WINDOW_MS = 5_000L;

    private static volatile long markedAt = 0L;

    /** Called from the client when it starts tearing the level down. */
    public static void mark() {
        markedAt = System.currentTimeMillis();
    }

    /** Whether a player-initiated teardown happened recently. */
    public static boolean recent() {
        long at = markedAt;
        return at != 0L && System.currentTimeMillis() - at <= WINDOW_MS;
    }

    /** Test seam: makes the "just now" window verifiable without sleeping. */
    static boolean recentAt(long nowMs, long markedAtMs) {
        return markedAtMs != 0L && nowMs - markedAtMs <= WINDOW_MS;
    }
}

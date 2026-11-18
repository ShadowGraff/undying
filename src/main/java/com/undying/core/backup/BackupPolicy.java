package com.undying.core.backup;

/**
 * When to take a backup — the decision, with nothing game-related in it.
 *
 * <h3>Why this is not inside the scheduler</h3>
 * The scheduler needs a server and a player to do its work, so anything living there cannot be
 * tested without launching Minecraft. The rules themselves — one run a minute, none while a run is
 * in flight, none when nobody is in a world, none on someone else's server — are the part that can
 * be wrong in ways nobody notices for weeks. They live here, as three boolean/​int reads.
 */
public final class BackupPolicy {

    private BackupPolicy() {}

    /** One minute: fast enough to be useful, slow enough to be invisible. */
    public static final long INTERVAL_MS = 60_000L;

    /**
     * @param enabled        the feature switch
     * @param allowedHere    single-player gate
     * @param playersInWorld players actually in a world (not at the title screen)
     * @param busy           a run is already in flight
     * @param nowMs          current wall clock
     * @param lastMs         when the last run started, 0 for never
     */
    public static boolean shouldRun(boolean enabled, boolean allowedHere, int playersInWorld,
                                    boolean busy, long nowMs, long lastMs) {
        if (!enabled || !allowedHere) return false;
        // Nobody in a world means nothing is being written, so there is nothing to save. It also
        // stops a world left sitting at the title screen from being copied every minute.
        if (playersInWorld <= 0) return false;
        if (busy) return false;
        return nowMs - lastMs >= INTERVAL_MS;
    }
}

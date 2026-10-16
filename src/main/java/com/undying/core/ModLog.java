package com.undying.core;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Swallowed exceptions must still leave a trace.
 *
 * ═══════════════════════════════════════════════════════════
 *  Why this class is needed
 * ═══════════════════════════════════════════════════════════
 *
 * This mod has 37 occurrences of `catch (Throwable ignored) {}`. For event handlers,
 * "never crash the game" is right - a protection mod that crashes the player's game because
 * of its own bug is worse than no protection at all.
 *
 * But "swallow it and leave no trace" directly conflicts with the creed written in this
 * project's comments (no silent failures). And the three bad falls this project has taken
 *   - Startup protection was never loaded (ILaunchPluginService not discovered)
 *   - The experience whitelist was always true (getStackTrace()[0] is always java.lang.Thread)
 *   - The /kill hook was attached but could not block anything
 * All three "looked like they were running, actually were not, and gave no indication".
 *
 * => Conclusion: try/catch stays, but it must leave a trace.
 *   The trade-off here balances [not spamming] against [not staying silent]:
 *     - Log at DEBUG (not in latest.log, but the full text is in debug.log)
 *     - Log the first 5 occurrences per site in detail, then only accumulate a count
 *     - The count is visible to the self-check - so "swallowed 800 times" cannot hide
 *
 * Usage: change `catch (Throwable ignored) {}` into
 *       `catch (Throwable t) { ModLog.swallowed("ClassName.methodName", t); }`
 */
public final class ModLog {

    private ModLog() {}

    /** Site -> number of swallowed exceptions */
    private static final Map<String, Integer> COUNTS = new ConcurrentHashMap<>();

    /** The detailed-logging limit per site */
    private static final int DETAIL_LIMIT = 5;

    /**
     * Record one swallowed exception.
     *
     * @param where site identifier, preferably "Class.method"
     * @param t     the swallowed exception (may be null)
     */
    public static void swallowed(String where, Throwable t) {
        try {
            int n = COUNTS.merge(where, 1, Integer::sum);
            if (n <= DETAIL_LIMIT) {
                UndyingMod.LOGGER.debug("[swallowed#{}] {}: {}", n, where,
                        t == null ? "(null)" : t.toString());
            }
        } catch (Throwable ignored) {
            // If even this fails there is nothing left to do - but it must never crash the caller
        }
    }

    /** How many exceptions have been swallowed this session */
    public static int totalSwallowed() {
        int sum = 0;
        for (int v : COUNTS.values()) sum += v;
        return sum;
    }

    /**
     * Summarise into a single line, for use by the self-check.
     * Returns an empty string when nothing was swallowed (the normal case; keeps the output short).
     */
    public static String summary() {
        if (COUNTS.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        sb.append(totalSwallowed()).append(" time(s)");
        COUNTS.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .limit(5)
                .forEach(e -> sb.append("\n      ").append(e.getKey()).append(" × ").append(e.getValue()));
        return sb.toString();
    }
}

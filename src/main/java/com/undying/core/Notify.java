package com.undying.core;

import net.minecraft.server.level.ServerPlayer;

/**
 * Throttling and auditing of player notifications.
 *
 * <p>Two responsibilities:
 * <ul>
 *   <li><b>Throttling</b>: at most one notification of a given kind every 3 seconds.
 *       Interceptions can happen on any tick, so without throttling the chat box
 *       would be flooded — and a flooded notification is no notification at all.</li>
 *   <li><b>Auditing</b>: every interception also writes a WARN log entry. Chat gets
 *       scrolled away and players miss it; the log does not. This is the only
 *       objective evidence that an interception happened.</li>
 * </ul>
 *
 * <p>Throttle state lives on {@link PlayerState#notifyAt} — it is cleared together
 * with {@code PlayerState.clear} when the player disconnects, so no separate
 * cleanup path is needed.
 */
public final class Notify {

    private Notify() {}

    private static final long INTERVAL_MS = 3000L;

    private static boolean allowed(ServerPlayer p, String key) {
        PlayerState s = PlayerState.of(p);
        long now = System.currentTimeMillis();
        Long last = s.notifyAt.get(key);
        if (last != null && now - last < INTERVAL_MS) return false;
        s.notifyAt.put(key, now);
        return true;
    }

    /** Notify the player that a /kill command was intercepted */
    public static void killBlocked(ServerPlayer p) {
        try {
            if (allowed(p, "kill")) {
                Msg.warn(p, UndyingMod.TAG).text("/kill was intercepted").send();
            }
        } catch (Throwable t) {
            ModLog.swallowed("Notify.killBlocked", t);
        }
    }

    /** Notify the player and write an audit entry when a forced removal (kill()/remove()/die()) was intercepted */
    public static void removalBlocked(ServerPlayer p, String how) {
        try {
            UndyingMod.LOGGER.warn("[anti-tamper] intercepted a forced removal of player: {} path={}",
                    p.getName().getString(), how);
            if (allowed(p, "remove")) {
                Msg.warn(p, UndyingMod.TAG)
                        .text("Intercepted a forced removal (").text(how).text(")").send();
            }
        } catch (Throwable t) {
            ModLog.swallowed("Notify.removalBlocked", t);
        }
    }
}

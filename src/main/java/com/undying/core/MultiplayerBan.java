package com.undying.core;

/**
 * Multiplayer ban — the client-side half of "this mod is single-player only".
 *
 * ═══════════════════════════════════════════════════════════
 *  Why this exists in addition to {@link Environment}
 * ═══════════════════════════════════════════════════════════
 *
 * {@link Environment} is a <i>server-side reactive</i> gate: it waits until a server
 * exists, then decides whether the mod may act. That design has one hole that cannot be
 * closed from the server side alone, and one that cost us a real bug:
 *
 * <ol>
 *   <li><b>The LAN button is a client action.</b> By the time the integrated server knows
 *       it was published, the world is already open to the network. Catching it there is
 *       correct but late.</li>
 *   <li><b>The LAN predicate never actually fired in testing.</b> {@code isLanPublished}
 *       reads {@code getPort()} / {@code getLocalIp()}, and on a real "Open to LAN" session
 *       those did not produce the expected values — so the gate silently did not trigger.
 *       That is exactly the kind of "looks like it runs, actually does not" failure this
 *       project keeps getting bitten by.</li>
 * </ol>
 *
 * So the bans are moved to the <i>entrance</i>: the buttons that lead to multiplayer are
 * disabled before they can ever be pressed. This does not depend on any runtime reading of
 * server state, therefore it cannot be wrong about server state.
 *
 * <h3>Why match on translation keys instead of field names</h3>
 * The multiplayer button on {@code TitleScreen} is <b>not a field</b> — it is a local
 * variable created inside {@code init()} and registered immediately. There is nothing to
 * mix into. Vanilla also rearranges these screens between versions, so injecting at a
 * specific instruction index is fragile.
 *
 * The one thing that <i>is</i> stable is the translation key. {@code menu.multiplayer} and
 * {@code menu.shareToLan} are part of the vanilla language file contract; they are not
 * obfuscated, they are not renamed by Forge, and mods and resource packs depend on them
 * staying put. Matching on the key therefore survives both obfuscation and screen rewrites.
 *
 * <p>The decision itself lives in {@link #isBannedKey(String)}, a pure function, so it can
 * be unit-tested without the game on the classpath.
 */
public final class MultiplayerBan {

    private MultiplayerBan() {}

    // ═══════════════════════════════════════════════════════════
    //  Banned translation keys
    // ═══════════════════════════════════════════════════════════

    /** TitleScreen — the "Multiplayer" button (joins a server / opens the server list) */
    public static final String KEY_MULTIPLAYER = "menu.multiplayer";

    /** PauseScreen — the "Open to LAN" button */
    public static final String KEY_SHARE_TO_LAN = "menu.shareToLan";

    /** Disconnect / return-to-menu confirmations are not banned; only the two entrances are */

    /** Set to true once we have logged the ban, so the log stays readable */
    private static boolean logged = false;

    /**
     * Is this translation key one of the banned multiplayer entrances?
     *
     * <p><b>Exact comparison, deliberately.</b> A prefix or substring match would be
     * dangerous: the language file contains neighbouring keys such as
     * {@code menu.multiplayerWarning} and {@code menu.multiplayerOptions}, and quietly
     * disabling an unrelated widget is a worse failure than missing one.
     *
     * @param key the translation key, may be null
     * @return true when the widget carrying this key must be disabled
     */
    public static boolean isBannedKey(String key) {
        if (key == null) return false;
        return key.equals(KEY_MULTIPLAYER) || key.equals(KEY_SHARE_TO_LAN);
    }

    /**
     * The label shown for a banned key, for logging and tooltips.
     *
     * @return a short description, never null
     */
    public static String label(String key) {
        if (KEY_MULTIPLAYER.equals(key)) return "Multiplayer";
        if (KEY_SHARE_TO_LAN.equals(key)) return "Open to LAN";
        return "unknown";
    }

    /** Human-readable reason, shown to the player and written to the log */
    public static String reason() {
        return "This mod is single-player only";
    }

    /**
     * Log the ban exactly once per session.
     *
     * <p>Called from the mixin every time a button is disabled, because the screens are
     * rebuilt constantly (opening and closing a world re-creates the title screen). Without
     * the guard this would be the loudest thing in the log.
     */
    public static void logOnce() {
        if (logged) return;
        logged = true;
        try {
            UndyingMod.LOGGER.info(
                    "[Undying] Multiplayer entrances disabled by this mod "
                            + "(single-player only): {} / {}", KEY_MULTIPLAYER, KEY_SHARE_TO_LAN);
        } catch (Throwable t) {
            ModLog.swallowed("MultiplayerBan.logOnce", t);
        }
    }

    /** Reset the once-per-session flag (used by tests and on world unload) */
    public static void resetNotification() {
        logged = false;
    }
}

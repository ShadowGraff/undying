package com.undying.core;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

/**
 * Environment gate — single-player only.
 *
 * <p><b>This mod is deliberately single-player only.</b> It provides
 * "you do not die" behaviour, which is a cheat by design. Installing it on a
 * multiplayer server would hand invulnerability to every player on that server.
 * To keep that from ever happening by accident, every feature entry point passes
 * through this gate first.
 *
 * <h3>What is blocked</h3>
 * <ul>
 *   <li>Dedicated servers — the mod stays completely inert.</li>
 *   <li>Opening a single-player world to LAN.</li>
 *   <li>Joining any remote server (the integrated server is not running locally).</li>
 * </ul>
 *
 * <h3>How the check works</h3>
 * {@link MinecraftServer#isSingleplayer()} is true only for the client's own
 * integrated server. It is false for a dedicated server, and it is also false on
 * a client that is connected to someone else's server (there is no local server
 * at all in that case). That single predicate therefore covers all three cases.
 *
 * <p>LAN is the one case it does not cover on its own: when a single-player world
 * is published to LAN, the integrated server is still the local one, so
 * {@code isSingleplayer()} stays true. {@link #isLanPublished} closes that hole.
 *
 * <h3>Why notify instead of crash</h3>
 * Crashing a server would be hostile to whoever runs it, and would make this mod
 * look like malware. The gate disables every feature and says so plainly in the
 * log and in chat, once per session.
 */
public final class Environment {

    private Environment() {}

    /** Set to true when we have already told the player this session. */
    private static boolean notified = false;

    /**
     * Whether the mod may act at all.
     *
     * @param server the server to test; null means "no server yet" and is treated as blocked
     */
    public static boolean isAllowed(MinecraftServer server) {
        if (server == null) return false;
        boolean singleplayer;
        try {
            singleplayer = server.isSingleplayer();
        } catch (Throwable t) {
            ModLog.swallowed("Environment.isAllowed", t);
            return false;   // fail closed
        }
        return decide(singleplayer, isLanPublished(server));
    }

    /**
     * True when a single-player world has been opened to LAN.
     *
     * <p>The dedicated-server case never reaches here ({@code isSingleplayer()} is
     * already false), so this only has to detect the "Open to LAN" button.
     *
     * <h3>Two independent tests, OR-ed together</h3>
     * This is a security boundary, so it does not rest on a single signal:
     *
     * <ol>
     *   <li><b>Structural</b> — vanilla's {@code publishServer} sets a
     *       {@code LanServerPinger} field while the world is open and clears it on close,
     *       so "field present" is exactly equivalent to "published".</li>
     *   <li><b>Heuristic</b> (the original port/address test) — kept as a fallback, in case
     *       the field is renamed by a future version and the accessor stops matching.</li>
     * </ol>
     *
     * If <i>either</i> reports the world as published, the gate closes. A false positive only
     * disables the mod (annoying but safe); a false negative would hand invulnerability to
     * everyone on the network. The asymmetry is deliberate.
     */
    public static boolean isLanPublished(MinecraftServer server) {
        if (checkLanStructurally(server)) return true;
        try {
            // A published world has a bound server socket; a purely local one does not.
            return decideLan(server.getPort(), server.getLocalIp());
        } catch (Throwable t) {
            ModLog.swallowed("Environment.isLanPublished", t);
            // Fail closed: if we cannot tell, assume the world is published.
            return true;
        }
    }

    /**
     * The structural LAN signal, reached without naming a client-only type here.
     *
     * <p>{@code IntegratedServer} lives in {@code net.minecraft.client.server} and does not
     * exist on a dedicated server. This class is loaded on both sides, so naming that type
     * anywhere inside it — even in a branch that can never run server-side — would make the
     * class unlinkable on a server. {@link ClientHooks} holds the reference instead, behind
     * {@code @OnlyIn(Dist.CLIENT)}, and the {@code DistExecutor} wrapper keeps the server from
     * ever loading it.
     *
     * <p>A server contributes nothing here, which is correct:
     * {@code isSingleplayer()} has already ruled the environment out.
     */
    private static boolean checkLanStructurally(MinecraftServer server) {
        try {
            Boolean result = DistExecutor.unsafeCallWhenOn(Dist.CLIENT,
                    () -> () -> ClientHooks.isLanPublished(server));
            return Boolean.TRUE.equals(result);
        } catch (Throwable t) {
            // "This signal says nothing", not "not published" — the caller OR-s the signals,
            // so returning false cannot open the gate on its own.
            ModLog.swallowed("Environment.checkLanStructurally", t);
            return false;
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Pure decision functions
    // ═══════════════════════════════════════════════════════════
    //
    // The gate above touches MinecraftServer, which cannot be instantiated in a plain
    // unit test. The actual decision is therefore factored into these two static
    // functions, which take only primitives and can be tested exhaustively without
    // provisioning the game. This is a security boundary, so being able to test every
    // branch matters more than keeping the code in one method.

    /** The whole gate, as a pure function of its two inputs */
    static boolean decide(boolean singleplayer, boolean lanPublished) {
        return singleplayer && !lanPublished;
    }

    /**
     * LAN detection, as a pure function.
     *
     * <p>A world that was never published has no bound port and no local address.
     * A null/empty address also covers the "still starting up" window.
     */
    static boolean decideLan(int port, String localIp) {
        return port > 0 && localIp != null && !localIp.isEmpty();
    }

    /**
     * Gate for per-player entry points.
     *
     * @return true when the mod may act for this player
     */
    public static boolean isAllowed(ServerPlayer p) {
        if (p == null || p.server == null) return false;
        boolean ok = isAllowed(p.server);
        if (!ok) notifyOnce(p);
        return ok;
    }

    /** Convenience inverse, for readability at call sites */
    public static boolean isBlocked(ServerPlayer p) {
        return !isAllowed(p);
    }

    /**
     * Tell the player, once per session, that multiplayer is not supported.
     *
     * <p>Deliberately not repeated: a per-tick message would be spam, and the
     * player cannot act on it more than once anyway.
     */
    private static void notifyOnce(ServerPlayer p) {
        if (notified) return;
        notified = true;
        try {
            UndyingMod.LOGGER.warn(
                    "[Undying] Multiplayer environment detected — all features are disabled. "
                            + "This mod is single-player only.");
            Msg.warn(p, UndyingMod.TAG)
                    .text("多人游戏环境：本模组已全部禁用 / ")
                    .text("Multiplayer detected — all features disabled.").send();
            Msg.plain(p, UndyingMod.TAG)
                    .text("本模组仅供单人游戏使用 / This mod is single-player only.")
                    .dim(" (LAN / server / dedicated server)").send();
        } catch (Throwable t) {
            ModLog.swallowed("Environment.notifyOnce", t);
        }
    }

    /** Reset the once-per-session flag (used when leaving a world) */
    public static void resetNotification() {
        notified = false;
    }
}

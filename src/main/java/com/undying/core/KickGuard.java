package com.undying.core;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * World guard: when a mod tries to take the player out of their world, ask first.
 *
 * <h3>What this is for</h3>
 * Horror mods sometimes end the session instead of the player: they disconnect them, add a ban
 * entry, or stop the integrated server outright. There is nothing to "survive" in those cases —
 * the world simply closes and whatever was in progress is gone. So the shape of the protection
 * is different from the death lock: cancel the action, show who asked and what reason they gave,
 * and let the player decide.
 *
 * <h3>Why the player's own quit path must never be questioned</h3>
 * A guard that cannot tell "the player pressed Save and Quit" from "a mod closed the server" is
 * a guard that makes the game impossible to close, which is worse than the problem. The
 * distinction is made by {@link QuitIntent}: the client records the moment it starts tearing
 * the level down, and a stop within a few seconds of that is treated as the player's own
 * request. The honest limitation: a mod that calls {@code Minecraft.close()} or
 * {@code clearLevel()} directly would be misread as the player. That is written down rather
 * than hidden, because it is the one way this judgement can be wrong.
 *
 * <h3>Why the pending question lives on {@link PlayerState}</h3>
 * Same reason as every other feature here: one cleanup entry point. Leaving a world in any way
 * — quit, kick accepted, crash — clears the player's state, so an unanswered question can never
 * outlive the session it belonged to.
 */
public final class KickGuard {

    private KickGuard() {}

    public static final String KIND_DISCONNECT = "disconnect";
    public static final String KIND_STOP = "stopServer";

    /** A question currently on screen. */
    public static final class Pending {
        public String kind;
        public String source;
        public String reason;
        public long id;
    }

    /**
     * Set while this mod itself is performing the action the player approved, so the same guard
     * that blocked it lets it through. The alternative — a second, unguarded call path — is how
     * guards grow holes.
     */
    private static final ThreadLocal<Boolean> BYPASS = ThreadLocal.withInitial(() -> false);

    private static final int MAX_REASON = 300;

    private static boolean enabled(ServerPlayer p) {
        return ProtectionBaseline.worldGuard() && Environment.isAllowed(p);
    }

    // ══════════════ Interception ══════════════

    /**
     * Called at the head of {@code ServerGamePacketListenerImpl#disconnect}.
     *
     * @return true when the disconnect must be cancelled
     */
    public static boolean interceptDisconnect(ServerPlayer p, Component reason) {
        try {
            if (p == null) return false;
            if (!enabled(p)) return false;
            if (Boolean.TRUE.equals(BYPASS.get())) return false;

            // Already asking: cancel the new attempt but keep the question on screen, so the
            // player answers about the reason they are actually reading.
            if (PlayerState.of(p).pendingKick != null) return true;

            Pending k = new Pending();
            k.kind = KIND_DISCONNECT;
            k.source = describeOrigin(p);
            k.reason = text(reason);
            k.id = System.nanoTime();

            PlayerState.of(p).pendingKick = k;
            log(p, k);
            Networking.sendKickAsk(p, k);
            return true;
        } catch (Throwable t) {
            ModLog.swallowed("KickGuard.interceptDisconnect", t);
            return false;   // fail open: never trap the player in a world because of a bug here
        }
    }

    /**
     * Called at the head of {@code MinecraftServer#stopServer}.
     *
     * @return true when the stop must be cancelled
     */
    public static boolean interceptStopServer(MinecraftServer server) {
        try {
            if (server == null) return false;
            if (!ProtectionBaseline.worldGuard()) return false;
            if (Boolean.TRUE.equals(BYPASS.get())) return false;
            if (!server.isSingleplayer()) return false;

            // The player asked to leave. This is the case that must never be questioned.
            if (QuitIntent.recent()) return false;

            List<ServerPlayer> players = server.getPlayerList().getPlayers();
            if (players.isEmpty()) return false;   // nothing to ask, nothing to protect

            ServerPlayer p = players.get(0);
            if (PlayerState.of(p).pendingKick != null) return true;

            Pending k = new Pending();
            k.kind = KIND_STOP;
            k.source = describeOrigin(p);
            k.reason = "要求关闭内部服务器 / asked to stop the integrated server";
            k.id = System.nanoTime();

            PlayerState.of(p).pendingKick = k;
            log(p, k);
            Networking.sendKickAsk(p, k);
            return true;
        } catch (Throwable t) {
            ModLog.swallowed("KickGuard.interceptStopServer", t);
            return false;   // fail open: a crash while shutting down would be worse
        }
    }

    // ══════════════ The answer ══════════════

    /** The player chose to accept: perform the action they were asked about. */
    public static void accept(ServerPlayer p, long id) {
        try {
            if (p == null) return;
            Pending k = takeIfMatches(p, id);
            if (k == null) return;

            BYPASS.set(true);
            try {
                if (KIND_STOP.equals(k.kind)) {
                    p.server.halt(false);
                } else if (p.connection != null) {
                    p.connection.disconnect(Component.literal(k.reason));
                }
            } finally {
                BYPASS.set(false);
            }
        } catch (Throwable t) {
            ModLog.swallowed("KickGuard.accept", t);
        }
    }

    /** The player chose to stay. Nothing is retried on their behalf. */
    public static void refuse(ServerPlayer p, long id) {
        try {
            if (p == null) return;
            Pending k = takeIfMatches(p, id);
            if (k == null) return;

            Msg.ok(p, UndyingMod.TAG)
                    .bi("已拦下这次中断（" + shortKind(k) + "）",
                            "Blocked an interruption (" + shortKind(k) + ")")
                    .send();
        } catch (Throwable t) {
            ModLog.swallowed("KickGuard.refuse", t);
        }
    }

    /**
     * Take the pending question only if the id matches.
     *
     * <p>Ids matter: the client can answer a question that has already been replaced by a
     * newer one, and acting on the old answer would perform the wrong action.
     */
    private static Pending takeIfMatches(ServerPlayer p, long id) {
        Pending k = PlayerState.of(p).pendingKick;
        if (k == null || k.id != id) return null;
        PlayerState.of(p).pendingKick = null;
        return k;
    }

    /** Whether this player currently has an unanswered question. */
    public static boolean hasPending(ServerPlayer p) {
        return p != null && PlayerState.of(p).pendingKick != null;
    }

    // ══════════════ Description ══════════════

    /**
     * Who did this, as far as it can be known.
     *
     * <p>"Which mod" is not answerable from inside a cancel hook: by the time the call arrives
     * it is just a stack. Two things are checkable and worth showing — whether a command caused
     * it in this tick, and which thread ran it. Anything more would be a guess presented as a
     * fact, which is exactly what this project keeps refusing to do.
     */
    private static String describeOrigin(ServerPlayer p) {
        try {
            long tick = p.server.getTickCount();
            String cmd = TeleportGuard.currentCommandText(tick);
            if (TeleportGuard.insideCommand(tick) && !cmd.isEmpty()) {
                String shown = cmd.length() > 60 ? cmd.substring(0, 60) + "…" : cmd;
                String who = TeleportGuard.currentCommandSource(tick);
                return "命令 [" + who + "] / command: " + shown;
            }
        } catch (Throwable ignored) {
            // fall through to the generic answer
        }
        String thread = Thread.currentThread().getName();
        return "其他模组（线程 " + thread + "）/ another mod (thread: " + thread + ")";
    }

    private static String text(Component reason) {
        String s;
        try {
            s = reason == null ? "" : reason.getString();
        } catch (Throwable t) {
            s = "";
        }
        if (s.isEmpty()) return "（没有给出理由 / no reason given）";
        return s.length() > MAX_REASON ? s.substring(0, MAX_REASON) + "…" : s;
    }

    private static String shortKind(Pending k) {
        return KIND_STOP.equals(k.kind) ? "关闭服务器" : "踢出世界";
    }

    private static void log(ServerPlayer p, Pending k) {
        try {
            IntegrityGuard.LOGGER.warn("[world-guard] intercepted {} for {} (source: {})",
                    k.kind, p.getName().getString(), k.source);
            ReportLog.writeBlock("[world-guard] " + k.kind + " for " + p.getName().getString()
                    + " | source: " + k.source + " | reason: " + k.reason);
        } catch (Throwable t) {
            ModLog.swallowed("KickGuard.log", t);
        }
    }
}

package com.undying.core;

import net.minecraft.resources.ResourceKey;
import java.util.Map;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/**
 * Teleport guard: anything that moves the player without the player asking gets stopped and
 * turned into a question.
 *
 * <h3>Why cancel first and ask second</h3>
 * There is no way to "pause" a teleport halfway: by the time the coordinates are readable the
 * call is already in flight. So the call is cancelled, the intended destination is kept, and
 * the same teleport is performed later <i>if</i> the player says yes. Nothing is lost — a
 * cancelled teleport moves nobody.
 *
 * <h3>Two thread-local flags, both unavoidable</h3>
 * {@code COMMAND_DEPTH} exists because {@code Commands.performCommand} is the only place that
 * knows a teleport came from a command, and the teleport happens deeper in the stack. The
 * {@code bypass} flag exists so the approved teleport can pass through the same guard that
 * blocked it — the alternative is a second, unguarded call path, which is how a guard grows a
 * hole.
 *
 * <p>Both are thread-local rather than per-player because a server thread runs one call stack;
 * the guard is not re-entrant across threads and does not need to be.
 */
public final class TeleportGuard {

    private TeleportGuard() {}

    /** What a confirmed teleport needs, kept until the question is answered. */
    public static final class Pending {
        public ResourceKey<Level> dimension;
        public double x, y, z;
        public float yaw, pitch;
        public String origin;
        public long id;
    }

    // ══════════════ Command context ══════════════

    /**
     * The last command seen on this thread: {@code [serverTick, 1 if a player typed it]}.
     *
     * <h3>Why a tick stamp instead of begin/end pairing</h3>
     * The first version paired {@code commandBegin} at HEAD with {@code commandEnd} at RETURN.
     * That leaks: the {@code /kill} blocker cancels at HEAD, and a cancelled injection returns
     * without passing the RETURN hook, so the depth counter never came back down. Once leaked,
     * every later teleport on that thread looked like "inside a player command" and the guard
     * would have stayed permanently open — a silent failure of the exact kind this project
     * keeps writing down.
     *
     * <p>A command and the teleport it causes run in the same tick, so a tick stamp answers the
     * same question with nothing to unwind. A stale stamp becomes harmless one tick later.
     */
    private static final ThreadLocal<long[]> COMMAND = ThreadLocal.withInitial(() -> new long[]{-1L, 0L});
    /** The command text, kept only for the log line: "which command did this" is the first
     *  question anyone asks when a teleport is intercepted, and answering it after the fact
     *  is impossible. */
    private static final ThreadLocal<String> COMMAND_TEXT = ThreadLocal.withInitial(() -> "");
    /**
     * Who issued the command, in one word.
     *
     * <p>A player typing in chat produces a source with the player as its entity; a command
     * block, a function or the console produces one without. That difference is the whole
     * judgement, so it is recorded rather than inferred — when the guard fires on a command
     * that looked like it came from the player, this field says whether the source actually
     * had a player in it.
     */
    private static final ThreadLocal<String> COMMAND_SOURCE = ThreadLocal.withInitial(() -> "?");

    private static final ThreadLocal<Boolean> BYPASS = ThreadLocal.withInitial(() -> false);

    /** Called by the command hook for every command that actually runs. */
    public static void noteCommand(long serverTick, boolean byPlayer, String commandText,
                                   String sourceDescription) {
        long[] c = COMMAND.get();
        c[0] = serverTick;
        c[1] = byPlayer ? 1L : 0L;
        COMMAND_TEXT.set(commandText == null ? "" : commandText.trim());
        COMMAND_SOURCE.set(sourceDescription == null ? "?" : sourceDescription);
    }

    /** Who the command source was: a player, or something without an entity. */
    public static String currentCommandSource(long serverTick) {
        return sameTickCommand(serverTick) ? COMMAND_SOURCE.get() : "?";
    }

    /** The command text seen this tick, or an empty string. */
    public static String currentCommandText(long serverTick) {
        return sameTickCommand(serverTick) ? COMMAND_TEXT.get() : "";
    }

    private static boolean sameTickCommand(long serverTick) {
        long[] c = COMMAND.get();
        return c[0] >= 0 && c[0] == serverTick;
    }

    public static boolean insideCommand(long serverTick) {
        return sameTickCommand(serverTick);
    }

    public static boolean commandByPlayer(long serverTick) {
        long[] c = COMMAND.get();
        return sameTickCommand(serverTick) && c[1] == 1L;
    }

    // ══════════════ Interception ══════════════

    /**
     * Called at the head of the hooked teleport methods.
     *
     * @return true when the call was intercepted and the caller must cancel it
     */
    public static boolean intercept(ServerPlayer p, ServerLevel level,
                                    double x, double y, double z, float yaw, float pitch) {
        try {
            if (p == null || level == null) return false;
            long tick = p.server.getTickCount();
            if (!TeleportPolicy.shouldIntercept(
                    ModConfig.TELEPORT_GUARD.get(),
                    Environment.isAllowed(p),
                    commandByPlayer(tick),
                    Boolean.TRUE.equals(BYPASS.get()))) {
                return false;
            }

            // A question is already on screen. Cancel the new attempt but keep the one being
            // asked about: overwriting the destination while the player reads the old
            // coordinates would mean they answer about a place that is no longer the target.
            Pending existing = PlayerState.of(p).pendingTeleport;
            if (existing != null) return true;

            Pending pending = new Pending();
            pending.dimension = level.dimension();
            pending.x = x;
            pending.y = y;
            pending.z = z;
            pending.yaw = yaw;
            pending.pitch = pitch;
            String cmd = currentCommandText(tick);
            if (insideCommand(tick)) {
                String shown = cmd.length() > 60 ? cmd.substring(0, 60) + "…" : cmd;
                pending.origin = "命令 / command [" + currentCommandSource(tick) + "]: "
                        + (shown.isEmpty() ? "(unknown)" : shown);
            } else {
                pending.origin = "其他模组 / another mod";
            }
            pending.id = System.nanoTime();

            PlayerState.of(p).pendingTeleport = pending;

            if (throttleLog(p)) {
                IntegrityGuard.LOGGER.warn(
                        "[teleport] intercepted a teleport for {} -> {} {} {} {} (origin: {})",
                        p.getName().getString(), level.dimension().location(),
                        String.format("%.1f", x), String.format("%.1f", y), String.format("%.1f", z),
                        pending.origin);
                ReportLog.writeBlock("[teleport] " + p.getName().getString()
                        + " -> " + level.dimension().location()
                        + " " + String.format("%.1f %.1f %.1f", x, y, z)
                        + " (origin: " + pending.origin + ")");
            }

            Networking.sendTeleportAsk(p, pending);
            return true;
        } catch (Throwable t) {
            ModLog.swallowed("TeleportGuard.intercept", t);
            return false;   // fail open: a broken guard must not freeze the player in place
        }
    }

    // ══════════════ The answer ══════════════

    /** Perform the held teleport. Called only after the player said yes. */
    public static boolean confirm(ServerPlayer p) {
        if (p == null) return false;
        Pending pending = PlayerState.of(p).pendingTeleport;
        if (pending == null) return false;

        PlayerState.of(p).pendingTeleport = null;

        try {
            ServerLevel level = p.server.getLevel(pending.dimension);
            if (level == null) return false;
            BYPASS.set(true);
            try {
                p.teleportTo(level, pending.x, pending.y, pending.z, pending.yaw, pending.pitch);
            } finally {
                BYPASS.set(false);
            }
            Msg.ok(p, UndyingMod.TAG)
                    .bi("已传送", "Teleport accepted")
                    .send();
            return true;
        } catch (Throwable t) {
            ModLog.swallowed("TeleportGuard.confirm", t);
            return false;
        }
    }

    /** Drop the held teleport without moving anyone. */
    public static void deny(ServerPlayer p) {
        if (p == null) return;
        PlayerState.of(p).pendingTeleport = null;
        Msg.plain(p, UndyingMod.TAG)
                .bi("已取消这次传送", "Teleport refused")
                .send();
    }

    /**
     * One log line per player per 5 seconds.
     *
     * <p>A mod that teleports on every tick would otherwise write 20 lines a second, which
     * buries the lines that matter and fills the rolling report with one repeated sentence.
     * The interception itself is not throttled — only the paper trail.
     */
    private static boolean throttleLog(ServerPlayer p) {
        try {
            Map<String, Long> at = PlayerState.of(p).notifyAt;
            long now = System.currentTimeMillis();
            Long last = at.get("teleport-log");
            if (last != null && now - last < 5000L) return false;
            at.put("teleport-log", now);
            return true;
        } catch (Throwable t) {
            return true;   // losing a log line is better than losing the log
        }
    }

    /** Whether this player currently has an unanswered question. */
    public static boolean hasPending(ServerPlayer p) {
        return p != null && PlayerState.of(p).pendingTeleport != null;
    }
}

package com.undying.core;

/**
 * "A real player typed a command in chat, this tick."
 *
 * <h3>Why this exists</h3>
 * {@code /antigrief die} is a deliberate escape hatch: it is the one command that must work even
 * with every protection switched off. That makes it the most valuable thing in the mod for anyone
 * trying to kill the player, and the first version left it open to <b>any</b> code in the same
 * process: {@code getDispatcher().parse("antigrief die", source)} then {@code performCommand}
 * produces a perfectly legitimate-looking invocation with no player anywhere near the keyboard.
 *
 * <p>There is no way to tell "who called this" from inside the command — the source stack looks the
 * same. What can be told apart is <b>which path the tick came in on</b>: a player's chat command
 * arrives through {@code ServerGamePacketListenerImpl.handleChatCommand}, and a command block, a
 * function or another mod's {@code performCommand} does not. So the packet handler stamps the tick,
 * and the suicide command refuses to run without a stamp.
 *
 * <h3>Why a tick stamp and not a flag</h3>
 * Same reasoning as {@link TeleportGuard}'s command stamp: a boolean would have to be cleared by
 * whoever consumed it, and a cancelled injection can return without passing the clear. A stamp
 * expires by itself one tick later.
 */
public final class PlayerCommandMark {

    private PlayerCommandMark() {}

    private static final ThreadLocal<Long> CHAT_TICK = ThreadLocal.withInitial(() -> -1L);

    /** Called from the packet handler, before the command is dispatched. */
    public static void noteChatCommand(long serverTick) {
        CHAT_TICK.set(serverTick);
    }

    /** Whether a player-typed command arrived on this thread during the given tick. */
    public static boolean chatCommandThisTick(long serverTick) {
        return CHAT_TICK.get() == serverTick;
    }
}

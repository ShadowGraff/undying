package com.undying.core;

import net.minecraft.server.level.ServerPlayer;

/**
 * Chat triggers.
 *
 * <p>The entry point is "type a word into chat" rather than a command or a keybind - the
 * player's hands never have to leave the usual keys.
 *
 * <p>Every word is handled by <b>consuming</b> it (the caller cancels the chat event, so the word
 * is not sent) and then performing the action. Words that are not consumed pass through unchanged.
 *
 * <p>Every word has both a Chinese and an English spelling with the same effect.
 */
public final class ChatTriggers {

    private ChatTriggers() {}

    /**
     * Handle one chat word.
     *
     * @return true = this word was consumed by the mod and the caller should cancel the chat event
     */
    public static boolean handle(ServerPlayer p, String raw) {
        if (p == null) return false;
        // Single-player only: in multiplayer every trigger word is left untouched and
        // simply goes through as an ordinary chat message.
        if (!Environment.isAllowed(p)) return false;
        String msg = raw == null ? "" : raw.trim();
        if (msg.isEmpty()) return false;

        // ── Immortal mode toggle ──
        if (match(msg, "toggle", "无敌")) {
            boolean on = ImmortalMode.toggle(p);
            Msg.ok(p, UndyingMod.TAG).text(on
                    ? "无敌模式：开启 / Immortal mode: ON"
                    : "无敌模式：关闭 / Immortal mode: OFF").send();
            return true;
        }

        // ── Aura: timed ──
        if (match(msg, "aura", "光环")) {
            Aura.set(p, Aura.Mode.TIMED);
            Msg.info(p, UndyingMod.TAG).text("光环：开启 / Aura: ON ").dim("(")
                    .num(Const.AURA_DURATION_TICKS / Const.TICKS_PER_SECOND)
                    .text("s)").actionBar().send();
            return true;
        }

        // ── Aura: continuous ──
        if (match(msg, "aura on", "光环常驻")) {
            Aura.set(p, Aura.Mode.INFINITE);
            Msg.info(p, UndyingMod.TAG).text("光环：常驻 / Aura: CONTINUOUS").actionBar().send();
            return true;
        }

        // ── Aura: off ──
        if (match(msg, "aura off", "光环关闭")) {
            Aura.stop(p);
            Msg.info(p, UndyingMod.TAG).text("光环：关闭 / Aura: OFF").actionBar().send();
            return true;
        }

        // ── Obtain the sword ──
        if (match(msg, "sword", "取剑")) {
            VoidSword.give(p);
            return true;
        }

        // ── Creative inventory ──
        if (match(msg, "creative", "创造")) {
            Networking.sendOpenScreen(p);
            Msg.ok(p, UndyingMod.TAG).text("创造物品栏已打开 / Creative inventory opened").send();
            return true;
        }

        return false;
    }

    /** Either the Chinese or the English spelling counts as a match */
    private static boolean match(String msg, String en, String zh) {
        return msg.equalsIgnoreCase(en) || msg.equals(zh);
    }
}

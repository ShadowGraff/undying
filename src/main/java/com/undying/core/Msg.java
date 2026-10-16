package com.undying.core;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Message layer: centralises every player-facing chat / action bar notification.
 *
 * Problem it solves: the original 71 notifications each built coloured strings by hand,
 * for example
 *     p.sendSystemMessage(Component.literal("§4[Protection] §cRemoved " + n + " entities."), false);
 * Colour, prefix and delivery method were all scattered across the business logic, so
 * changing one colour meant searching the whole codebase.
 *
 * Current usage (chained):
 *     Msg.to(p).warn("Scan").num(n).text(" entities from that mod were removed").send();
 *     Msg.to(p).ok("Undying").text("Immortal mode enabled").send();
 *     Msg.to(p).info("Aura").text("").num(sec).text("s left").actionBar().send();
 *
 * There are four semantic levels; the colour is fixed by the level, so colour codes are no
 * longer written by hand:
 *     info  -> §6 gold    general notice
 *     ok    -> §a green   success / completed
 *     warn  -> §4 red     warning / blocked / failure
 *     plain -> §7 gray    neutral / disabled state
 *
 * Implementation note: a StringBuilder accumulates rich-text fragments internally and the
 * Component is built once at the end. It does not parse colour codes - callers should never write '§' again.
 */
public final class Msg {

    /** Semantic level -> tag colour + body colour */
    private enum Level {
        INFO ("§6", "§e"),
        OK   ("§a", "§a"),
        WARN ("§4", "§c"),
        PLAIN("§7", "§7"),
        /** Emphasis only: dark red + bold */
        FINAL("§4§l", "§c§l");

        final String head;  // tag colour
        final String body;  // body colour
        Level(String head, String body) { this.head = head; this.body = body; }
    }

    private static final String TAG_OPEN  = "[";
    private static final String TAG_CLOSE = "] ";
    private static final String SEP       = " §7";

    private final ServerPlayer player;
    private final Level level;
    private final StringBuilder sb = new StringBuilder();
    private boolean actionBar = false;

    private Msg(ServerPlayer player, Level level, String tag) {
        this.player = player;
        this.level = level;
        if (tag != null && !tag.isEmpty()) {
            sb.append(level.head).append(TAG_OPEN).append(tag).append(TAG_CLOSE);
        }
        sb.append(level.body);
    }

    /** Construction without a tag (for plain-text cases, e.g. the disconnect body) */
    public static Msg plain(ServerPlayer p) { return new Msg(p, Level.PLAIN, null); }

    // ═══════════ Entry points ═══════════

    public static Msg to(ServerPlayer p) {
        return new Msg(p, Level.INFO, null);
    }

    public static Msg info(ServerPlayer p, String tag)  { return new Msg(p, Level.INFO, tag); }
    public static Msg ok(ServerPlayer p, String tag)    { return new Msg(p, Level.OK, tag); }
    public static Msg warn(ServerPlayer p, String tag)  { return new Msg(p, Level.WARN, tag); }
    public static Msg plain(ServerPlayer p, String tag) { return new Msg(p, Level.PLAIN, tag); }
    /** Final chapter / emphasis (dark red bold) */
    public static Msg fin(ServerPlayer p, String tag)   { return new Msg(p, Level.FINAL, tag); }

    // ═══════════ Fragments ═══════════

    /** Append body text */
    public Msg text(String s) {
        sb.append(s);
        return this;
    }

    /** Append a numeric value (converted to a string automatically) */
    public Msg num(long n) {
        sb.append(n);
        return this;
    }

    public Msg num(double n) {
        sb.append(n);
        return this;
    }

    /** Append dimmed content (for mixed layouts, such as mod names or item names) */
    public Msg dim(String s) {
        sb.append(SEP).append(s).append(level.body);
        return this;
    }

    /** Switch to the action bar (the line above the hotbar); chat is the default */
    public Msg actionBar() {
        this.actionBar = true;
        return this;
    }

    // ═══════════ Delivery ═══════════

    /** Send (server-side players only; on the client use build() + displayClientMessage) */
    public void send() {
        if (player == null) return;
        player.sendSystemMessage(Component.literal(sb.toString()), actionBar);
    }

    /**
     * Static construction without a player: for client screens and other places where no
     * ServerPlayer is available.
     * Usage: minecraft.player.displayClientMessage(Msg.render("Creative", "Obtained", ...), true)
     * - this lets the client reuse the same colour scheme instead of hand-writing colour codes.
     */
    public static Component render(String tag, String text) {
        Msg m = new Msg((ServerPlayer) null, tag == null || tag.isEmpty() ? Level.PLAIN : Level.OK, tag);
        m.sb.append(text);
        return Component.literal(m.sb.toString());
    }

    /** Build the Component directly (for when you deliver it yourself, e.g. disconnect text) */
    public Component build() {
        return Component.literal(sb.toString());
    }
}

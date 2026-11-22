package com.undying.core;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;

/**
 * The {@code /antigrief} command tree.
 *
 * <p>Two subcommands:
 * <ul>
 *   <li>{@code die} — <b>our own suicide channel</b>. While the death lock is enabled
 *       this is the only path that can actually kill the player (see {@link DeathLock}).</li>
 *   <li>{@code master} — toggles immortal mode.</li>
 * </ul>
 */
public final class AntiGriefCommand {

    private AntiGriefCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        var root = Commands.literal("antigrief");

        // /antigrief die was removed in 2.9.1. It existed as "the escape hatch that works
        // even with everything off", and that is exactly what made it the most valuable thing
        // in the mod for anyone trying to kill the player: three lines of dispatcher code could
        // drive a real death. A player who wants to die turns the immortal mode off (O panel or
        // /antigrief master) and dies the normal way. Nothing is lost except the ability to
        // commit suicide while immortal, which is a contradiction anyway.

        // ── /antigrief master ──
        // Go through ImmortalMode.toggle because it also resets the integrity guard's
        // snapshot — without that reset the guard would flip the switch back within 2 seconds
        root.then(Commands.literal("master").executes(ctx -> {
            ServerPlayer p = ctx.getSource().getPlayerOrException();
            if (!Environment.isAllowed(p)) return 0;   // single-player only
            if (ImmortalMode.toggle(p)) {
                Msg.ok(p, UndyingMod.TAG).bi("不朽模式", "immortal mode").send();
            } else {
                Msg.plain(p, UndyingMod.TAG).bi("普通模式", "normal mode").send();
            }
            return 1;
        }));

        d.register(root);
    }
}

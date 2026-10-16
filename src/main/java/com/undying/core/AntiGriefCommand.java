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

        // ── /antigrief die ──
        // Set the suicide flag before setting health: that flag is the only credential
        // the death lock accepts, and reversing the order means our own lock blocks us.
        // Deliberately NOT gated on the environment: this is the escape hatch that lets a
        // player leave a world even when everything else is disabled.
        root.then(Commands.literal("die").executes(ctx -> {
            ServerPlayer p = ctx.getSource().getPlayerOrException();
            DeathLock.markSuicide(p.getUUID());
            p.setHealth(0F);
            Msg.warn(p, UndyingMod.TAG).text("Suicide command accepted...").send();
            return 1;
        }));

        // ── /antigrief master ──
        // Go through ImmortalMode.toggle because it also resets the integrity guard's
        // snapshot — without that reset the guard would flip the switch back within 2 seconds
        root.then(Commands.literal("master").executes(ctx -> {
            ServerPlayer p = ctx.getSource().getPlayerOrException();
            if (!Environment.isAllowed(p)) return 0;   // single-player only
            if (ImmortalMode.toggle(p)) {
                Msg.ok(p, UndyingMod.TAG).text("immortal mode").send();
            } else {
                Msg.plain(p, UndyingMod.TAG).text("normal mode").send();
            }
            return 1;
        }));

        d.register(root);
    }
}

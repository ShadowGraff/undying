package com.undying.core;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/**
 * {@code /undying report} — opens the folder holding the rolling report.
 *
 * <p>Why a server command instead of a client command: the first version used
 * {@code RegisterClientCommandsEvent}, and in 1.20.1 the click still reached the server —
 * the client dispatcher is not consulted for chat click commands here, so the player got
 * "unknown or incomplete command" and nothing opened. A plain server command goes through
 * the ordinary command path, which is the most reliable wiring Forge offers.
 *
 * <p>On a single-player world the client and the server are the same process, so the
 * server side can open the folder in the local file manager. On a dedicated server that
 * would pop a window on the <i>server's</i> desktop, which is useless to the player, so
 * there it only prints the path.
 */
public final class ReportCommand {

    private ReportCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("undying")
                .then(Commands.literal("report")
                        .executes(ctx -> run(ctx.getSource()))));
    }

    private static int run(CommandSourceStack source) {
        try {
            java.io.File dir = ReportLog.directory();
            if (!dir.isDirectory() && !dir.mkdirs()) {
                source.sendFailure(Component.literal("无法创建报告目录: " + dir.getAbsolutePath()));
                return 0;
            }

            if (source.getServer() != null && source.getServer().isSingleplayer()) {
                net.minecraft.Util.getPlatform().openUri(dir.toURI().toString());
                source.sendSuccess(() -> Component.literal("已打开报告目录: " + dir.getAbsolutePath()), false);
            } else {
                source.sendSuccess(() -> Component.literal("报告目录: " + dir.getAbsolutePath()), false);
            }
            return 1;
        } catch (Throwable t) {
            ModLog.swallowed("ReportCommand.run", t);
            source.sendFailure(Component.literal("打开报告目录失败，详见日志"));
            return 0;
        }
    }
}

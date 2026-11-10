package com.undying.core.client;

import com.undying.core.ModLog;
import com.undying.core.ReportLog;
import com.undying.core.UndyingMod;
import net.minecraft.commands.Commands;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Client command that opens the mod's report folder.
 *
 * <p>The chat box only carries a summary plus a clickable link; this is what the link runs.
 * It is a <b>client</b> command on purpose:
 * <ul>
 *   <li>the report lives in the local game directory, which a server cannot open for you;</li>
 *   <li>it works in single-player and on servers alike, because {@code /undying report}
 *       never leaves the client;</li>
 *   <li>nothing is granted or changed by running it — it only reveals a file that already
 *       exists on the player's own disk.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(value = Dist.CLIENT, modid = UndyingMod.MODID)
public final class ReportCommands {

    private ReportCommands() {}

    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        try {
            event.getDispatcher().register(Commands.literal("undying")
                    .then(Commands.literal("report")
                            .executes(ctx -> {
                                openDirectory();
                                return 1;
                            })));
        } catch (Throwable t) {
            ModLog.swallowed("ReportCommands.register", t);
        }
    }

    private static void openDirectory() {
        try {
            java.io.File dir = ReportLog.directory();
            if (!dir.isDirectory() && !dir.mkdirs()) {
                ModLog.swallowed("ReportCommands.openDirectory.mkdir", null);
                return;
            }
            net.minecraft.Util.getPlatform().openUri(dir.toURI().toString());
        } catch (Throwable t) {
            ModLog.swallowed("ReportCommands.openDirectory", t);
        }
    }
}

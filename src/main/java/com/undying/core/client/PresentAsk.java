package com.undying.core.client;

import com.undying.core.ModConfig;
import com.undying.core.ModLog;
import com.undying.core.ThreatScanner;
import com.undying.core.UndyingMod;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Asks once, on entering a world, whether to enable the mod scan.
 *
 * <p>Client-only by annotation: {@code value = Dist.CLIENT} makes Forge skip this class
 * entirely on a dedicated server, so the {@code net.minecraft.client.*} types below never
 * need to link there. That is the same fence {@code ClientHooks} uses, expressed more
 * cheaply.
 *
 * <p>Deliberately NOT behind {@code Environment.isAllowed}. That gate exists because the
 * death lock hands out invulnerability, which must never work on someone else's server.
 * A read-only jar scan hands out nothing, so it is allowed to run anywhere — the player's
 * own eyeballs are their business.
 */
@Mod.EventBusSubscriber(value = Dist.CLIENT, modid = UndyingMod.MODID)
public final class PresentAsk {

    private PresentAsk() {}

    /** Set on world join, consumed once the world is actually on screen. */
    private static boolean pending = false;

    @SubscribeEvent
    public static void onLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        pending = true;
    }

    /**
     * Waits for {@code screen == null} before showing the dialog.
     *
     * <p>World join fires while the "loading terrain" screen is still up; setting our screen
     * there would simply be replaced. Waiting for the first moment with no screen at all is
     * both the simplest and the most reliable condition: the player is in the world and can
     * actually read the question.
     */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !pending) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) return;
        pending = false;
        ask(mc);
    }

    private static void ask(Minecraft mc) {
        // Already answered once: never ask again.
        if (ModConfig.THREAT_SCAN_ASKED.get()) return;

        // Enabled by hand in the config: respect that and just record the question as done,
        // rather than nagging someone who already made the decision.
        if (ModConfig.THREAT_SCAN.get()) {
            ModConfig.THREAT_SCAN_ASKED.set(true);
            ModConfig.save();
            return;
        }

        mc.setScreen(new PresentAskScreen(PresentAsk::answer));
    }

    private static void answer(boolean enable) {
        try {
            ModConfig.THREAT_SCAN_ASKED.set(true);
            if (enable) {
                ModConfig.THREAT_SCAN.set(true);
                ModConfig.save();
                // Without a rescan the report would only appear on the next launch, which
                // looks exactly like a broken switch. One pass over the jars, on an explicit
                // user action, is affordable.
                ThreatScanner.rescan();
                announceNow();
            } else {
                ModConfig.save();
            }
        } catch (Throwable t) {
            ModLog.swallowed("PresentAsk.answer", t);
        }
    }

    /**
     * Hand the fresh findings to the server thread for reporting.
     *
     * <p>{@code announceTo} touches {@code PlayerState} and sends chat, both server-side, so
     * it must not run on the client thread. On a remote server there is no local server to
     * hand it to; the findings stay in the log and nothing is lost.
     */
    private static void announceNow() {
        try {
            Minecraft mc = Minecraft.getInstance();
            var server = mc.getSingleplayerServer();
            if (server == null || mc.player == null) return;
            ServerPlayer sp = server.getPlayerList().getPlayer(mc.player.getUUID());
            if (sp == null) return;
            server.execute(() -> ThreatScanner.announceTo(sp));
        } catch (Throwable t) {
            ModLog.swallowed("PresentAsk.announceNow", t);
        }
    }
}

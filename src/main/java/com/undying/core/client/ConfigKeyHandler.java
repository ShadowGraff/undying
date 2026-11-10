package com.undying.core.client;

import com.undying.core.ModLog;
import com.undying.core.UndyingMod;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Reads the O key every tick and opens the configuration screen.
 *
 * <p>Split out of {@link ConfigKeyBinds} because that class must sit on the mod bus to receive
 * {@code RegisterKeyMappingsEvent}, while this one must sit on the forge bus to receive client
 * ticks. One class cannot be on both, and having both handlers in one place is exactly how the
 * key ended up registered nowhere.
 *
 * <p>The click queue is drained even while a screen is open: otherwise presses pile up and one
 * key press appears to fire several times the next time the player closes a menu.
 */
@Mod.EventBusSubscriber(modid = UndyingMod.MODID, value = Dist.CLIENT)
public final class ConfigKeyHandler {

    private ConfigKeyHandler() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        try {
            Minecraft mc = Minecraft.getInstance();
            while (ConfigKeyBinds.OPEN_CONFIG.consumeClick()) {
                if (mc.screen == null && mc.player != null) {
                    mc.setScreen(new UndyingConfigScreen(null));
                }
            }
        } catch (Throwable t) {
            ModLog.swallowed("ConfigKeyHandler.tick", t);
        }
    }
}

package com.undying.core.client;

import com.undying.core.ModLog;
import com.undying.core.UndyingMod;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/**
 * The O key that opens the configuration screen.
 *
 * <p>Registered as a real {@link KeyMapping} rather than polled directly: that is what makes
 * it appear in Options → Controls → Key Binds, so the player can rebind it without this mod
 * shipping a binding screen. The default is O, unused by vanilla.
 */
@Mod.EventBusSubscriber(value = Dist.CLIENT, modid = UndyingMod.MODID)
public final class ConfigKeyBinds {

    private ConfigKeyBinds() {}

    public static final KeyMapping OPEN_CONFIG = new KeyMapping(
            "打开 Undying 配置",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_O,
            "Undying");

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        try {
            event.register(OPEN_CONFIG);
        } catch (Throwable t) {
            ModLog.swallowed("ConfigKeyBinds.register", t);
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        try {
            Minecraft mc = Minecraft.getInstance();
            // Drain the queue even while a screen is open, otherwise clicks pile up and one
            // key press appears to fire several times later.
            while (OPEN_CONFIG.consumeClick()) {
                if (mc.screen == null && mc.player != null) {
                    mc.setScreen(new UndyingConfigScreen(null));
                }
            }
        } catch (Throwable t) {
            ModLog.swallowed("ConfigKeyBinds.tick", t);
        }
    }
}

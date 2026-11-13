package com.undying.core.client;

import com.undying.core.ModLog;
import com.undying.core.UndyingMod;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/**
 * The O key that opens the configuration screen.
 *
 * <h3>Why this is registered on the MOD bus and polled on the FORGE bus</h3>
 * {@code RegisterKeyMappingsEvent} is a <b>mod-bus</b> event that fires once while the client
 * is starting. This class used to carry both the registration and the per-tick polling under a
 * single {@code @Mod.EventBusSubscriber}, which defaults to the <b>forge</b> bus — so the
 * registration handler was never called, the key was never registered, and O appeared nowhere
 * in Options → Controls. Nothing logged an error; the key simply did not exist.
 *
 * <p>An annotation is per class, so the two buses need two classes. The mapping itself lives
 * here, where it is registered; polling lives in {@link ConfigKeyHandler}.
 *
 * <p>The name and category are translation keys, so the entry in Controls reads in the
 * player's language instead of always English.
 */
@Mod.EventBusSubscriber(modid = UndyingMod.MODID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ConfigKeyBinds {

    private ConfigKeyBinds() {}

    public static final KeyMapping OPEN_CONFIG = new KeyMapping(
            "key.undying.open_config",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_O,
            "key.categories.undying");

    /**
     * Hold to preview what the inventory guard has seen change.
     *
     * <p>Deliberately a hold rather than a tap: this freezes the game, and a single key could be
     * hit by accident while typing. Three seconds is long enough that it is always intended.
     */
    public static final KeyMapping PREVIEW_INVENTORY = new KeyMapping(
            "key.undying.preview_inventory",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_I,
            "key.categories.undying");

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        try {
            event.register(OPEN_CONFIG);
            event.register(PREVIEW_INVENTORY);
        } catch (Throwable t) {
            ModLog.swallowed("ConfigKeyBinds.register", t);
        }
    }
}

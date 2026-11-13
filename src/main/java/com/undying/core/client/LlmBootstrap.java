package com.undying.core.client;

import com.undying.core.LlmChat;
import com.undying.core.UndyingMod;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Client start-up hook for the assistant.
 *
 * <h3>Why this is its own class</h3>
 * {@code FMLClientSetupEvent} is a <b>mod-bus</b> event. {@link LlmCommands} sits on the forge
 * bus because it listens for client commands and ticks, and an annotation is per class. Putting
 * this handler there would have repeated the mistake that once left the O key registered
 * nowhere at all — a listener that is never called, with no error to show for it.
 */
@Mod.EventBusSubscriber(modid = UndyingMod.MODID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.MOD)
public final class LlmBootstrap {

    private LlmBootstrap() {}

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(LlmChat::load);
    }
}

package com.undying.core.client;

import com.undying.core.ModLog;
import com.undying.core.Networking;
import com.undying.core.UndyingMod;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Holds the preview key for three seconds, then asks the server for the change list.
 *
 * <h3>Why a hold and not a tap</h3>
 * The preview freezes the game. A tap is one keystroke away from an accident — and "I" sits
 * next to several keys people press while moving. Three seconds cannot be reached by accident,
 * so the freeze is always something the player asked for.
 *
 * <p>Ticks, not milliseconds: the hold should feel the same on a machine running at 15 fps as
 * on one at 200, and the server tick is the only clock both ends share.
 *
 * <p>{@link #fired} exists so that keeping the key down after the screen closes does not
 * immediately open it again. Releasing is what re-arms it.
 */
@Mod.EventBusSubscriber(modid = UndyingMod.MODID, value = Dist.CLIENT)
public final class InventoryHoldTracker {

    /** 20 ticks per second, so three seconds. */
    private static final int HOLD_TICKS = 60;
    /** How often the countdown is refreshed in the action bar. */
    private static final int NOTICE_EVERY = 10;

    private static int held;
    private static boolean fired;

    private InventoryHoldTracker() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.level == null || mc.screen != null) {
                reset();
                return;
            }

            boolean down = ConfigKeyBinds.PREVIEW_INVENTORY.isDown();
            if (!down) {
                reset();
                return;
            }
            if (fired) return;

            held++;
            if (held >= HOLD_TICKS) {
                fired = true;
                Networking.sendInventoryPreviewRequest();
                return;
            }
            if (held % NOTICE_EVERY == 0) {
                int secondsLeft = (HOLD_TICKS - held) / 20 + 1;
                mc.player.displayClientMessage(
                        Component.translatable("undying.preview.holding", secondsLeft), true);
            }
        } catch (Throwable t) {
            ModLog.swallowed("InventoryHoldTracker.tick", t);
        }
    }

    private static void reset() {
        held = 0;
        fired = false;
    }
}

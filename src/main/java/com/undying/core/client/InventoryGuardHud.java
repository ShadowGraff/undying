package com.undying.core.client;

import com.undying.core.InventoryGuard;
import com.undying.core.ModLog;
import com.undying.core.PlayerState;
import com.undying.core.UndyingMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/**
 * The small inventory-change prompt: bottom-left, translucent, non-blocking.
 *
 * <h3>Why not a Screen</h3>
 * A {@code Screen} takes over the mouse, pauses single-player and cannot be ignored — the
 * opposite of what was asked for. This draws over the HUD and only listens for keys, so the
 * player can keep playing, keep fighting, or simply let it time out. Doing nothing is a valid
 * answer and the default one.
 *
 * <h3>Why three presses</h3>
 * Y and N are ordinary letters. One press is what happens when the player types in chat, or
 * sneezes on the keyboard. Three presses in a row is deliberate; the counter resets as soon
 * as the other key is pressed.
 */
@Mod.EventBusSubscriber(value = Dist.CLIENT, modid = UndyingMod.MODID)
public final class InventoryGuardHud {

    private InventoryGuardHud() {}

    private static final int TTL_TICKS = 20 * 15;

    private static long shownAskId = 0;
    /** The three-press rule, extracted so it can be tested without a running client. */
    private static final AnswerKeys KEYS = new AnswerKeys();
    private static int ticksLeft = 0;

    // ═══════════ State sync ═══════════

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) {
                reset();
                return;
            }

            PlayerState ps = PlayerState.find(mc.player.getUUID());
            long askId = (ps == null || ps.inventoryGuard == null) ? 0 : ps.inventoryGuard.askId;

            if (askId == 0) {
                reset();
                return;
            }

            if (askId != shownAskId) {
                shownAskId = askId;
                KEYS.reset();
                ticksLeft = TTL_TICKS;
            }

            if (--ticksLeft <= 0) {
                // Locally give up. The server expires its own copy at the same time, so
                // nothing has to be sent — and nothing happens, which is the point.
                reset();
            }
        } catch (Throwable t) {
            ModLog.swallowed("InventoryGuardHud.tick", t);
            reset();
        }
    }

    // ═══════════ Keys ═══════════

    @SubscribeEvent
    public static void onKey(InputEvent.Key event) {
        if (shownAskId == 0) return;
        if (event.getAction() != GLFW.GLFW_PRESS) return;

        Minecraft mc = Minecraft.getInstance();
        // While any screen is open the player is typing or clicking, not answering this.
        if (mc.screen != null) return;

        try {
            if (event.getKey() == GLFW.GLFW_KEY_Y) {
                if (KEYS.press(true) == AnswerKeys.Decision.CONFIRM) confirm(mc);
            } else if (event.getKey() == GLFW.GLFW_KEY_N) {
                if (KEYS.press(false) == AnswerKeys.Decision.DECLINE) decline(mc);
            }
        } catch (Throwable t) {
            ModLog.swallowed("InventoryGuardHud.key", t);
        }
    }

    private static void confirm(Minecraft mc) {
        applyAnswer(mc, true);
    }

    private static void decline(Minecraft mc) {
        applyAnswer(mc, false);
    }

    /**
     * Hand the answer to the server thread — the inventory lives there, and in a
     * single-player world that is still a different thread from the render thread.
     */
    private static void applyAnswer(Minecraft mc, boolean restore) {
        try {
            var server = mc.getSingleplayerServer();
            if (server != null && mc.player != null) {
                ServerPlayer sp = server.getPlayerList().getPlayer(mc.player.getUUID());
                if (sp != null) {
                    server.execute(() -> {
                        if (restore) InventoryGuard.confirm(sp);
                        else InventoryGuard.decline(sp);
                    });
                }
            }
        } catch (Throwable t) {
            ModLog.swallowed("InventoryGuardHud.answer", t);
        }
        reset();
    }

    // ═══════════ Drawing ═══════════

    @SubscribeEvent
    public static void onRender(RenderGuiEvent.Post event) {
        if (shownAskId == 0 || ticksLeft <= 0) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null || mc.player == null) return;

        try {
            GuiGraphics g = event.getGuiGraphics();
            int w = 224;
            int h = 62;
            int x = 4;
            int y = mc.getWindow().getGuiScaledHeight() - h - 4;

            // Translucent, so the world stays visible behind it.
            g.fill(x, y, x + w, y + h, 0x90000000);

            g.drawString(mc.font, Component.translatable("undying.inventory.title"), x + 6, y + 6, 0xFF9090);
            g.drawString(mc.font, Component.translatable("undying.inventory.keys"), x + 6, y + 21, 0xFFFFFF);
            g.drawString(mc.font,
                    Component.translatable("undying.inventory.presses",
                            KEYS.yesCount() + "/" + AnswerKeys.PRESSES_REQUIRED,
                            KEYS.noCount() + "/" + AnswerKeys.PRESSES_REQUIRED),
                    x + 6, y + 35, 0xC8C8C8);
            g.drawString(mc.font,
                    Component.translatable("undying.inventory.timeout", ticksLeft / 20),
                    x + 6, y + 49, 0x909090);
        } catch (Throwable t) {
            ModLog.swallowed("InventoryGuardHud.render", t);
        }
    }

    private static void reset() {
        shownAskId = 0;
        KEYS.reset();
        ticksLeft = 0;
    }
}

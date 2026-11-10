package com.undying.core.client;

import com.undying.core.ModLog;
import com.undying.core.MultiplayerBan;
import com.undying.core.UndyingMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Keeps the multiplayer entrances disabled, on every screen, every tick.
 *
 * ═══════════════════════════════════════════════════════════
 *  Why a per-tick sweep exists on top of the Screen.init hook
 * ═══════════════════════════════════════════════════════════
 *
 * {@code MixinScreenMultiplayerBan} injects at the end of
 * {@code Screen.init(Minecraft, int, int)} and that works for every screen that is
 * <i>created</i>. It does not work when a screen is <i>resized</i>:
 *
 * <pre>
 *   resize(Minecraft, int, int)            // m_6574_
 *     width = w; height = h;               // ← just the two fields
 *     repositionElements()                 // m_267719_
 *       rebuildWidgets()                   // m_232761_
 *         clearWidgets(); init();          // m_7856_ ← buttons recreated here
 * </pre>
 *
 * {@code init(Minecraft, int, int)} is <b>not</b> on that path. So toggling fullscreen, or
 * changing the GUI scale, rebuilt the title screen and handed back a clickable
 * "Multiplayer" button — until the player left that screen, which created a fresh
 * instance and went through the hook again. Measured: reproducible every single time.
 *
 * <p>Two options were considered:
 * <ul>
 *   <li>inject at {@code repositionElements} as well — works only for this one rebuild
 *       path; the next vanilla refactor moves it again;</li>
 *   <li>sweep every tick — covers every path, including ones that bypass {@code init}
 *       entirely, at the cost of walking the current screen's widget list.</li>
 * </ul>
 * The sweep was chosen because this is a security boundary: "usually disabled" is not a
 * property worth having. The cost is a few dozen {@code instanceof} checks per tick,
 * and the sweep is idempotent, so once a button is disabled it is skipped by the
 * {@code !w.active} guard — no repeated tooltip allocation.
 *
 * <p>The heavy lifting still lives in the mixin for the common case; this class is the
 * net underneath it. Both call the same {@link #enforce(Screen)}.
 */
@Mod.EventBusSubscriber(value = Dist.CLIENT, modid = UndyingMod.MODID)
public final class MultiplayerGuard {

    private MultiplayerGuard() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        try {
            Minecraft mc = Minecraft.getInstance();
            // No screen means the world is being played; nothing to enforce there.
            if (mc.screen != null) enforce(mc.screen);
        } catch (Throwable t) {
            ModLog.swallowed("MultiplayerGuard.onClientTick", t);
        }
    }

    /**
     * Disable the multiplayer entrances on this screen. Idempotent and allocation-free
     * once a widget has already been disabled.
     */
    public static void enforce(Screen screen) {
        if (screen == null) return;
        for (GuiEventListener listener : screen.children()) {
            banWidget(listener);
        }
    }

    /**
     * Disable one widget if it is a banned multiplayer entrance.
     *
     * <p>Isolated so that a failure on an unexpected widget type cannot abort the scan and
     * leave a later button uninspected.
     */
    private static void banWidget(GuiEventListener listener) {
        try {
            if (!(listener instanceof AbstractWidget w)) return;

            // Already handled: skip, so the tooltip is not re-created every tick.
            if (!w.active) return;

            Component message = w.getMessage();
            if (message == null) return;

            ComponentContents contents = message.getContents();
            if (!(contents instanceof TranslatableContents translatable)) return;

            String key = translatable.getKey();
            if (!MultiplayerBan.isBannedKey(key)) return;

            w.active = false;

            // Explain why, on hover. Cheap, and it turns "the mod broke my menu" into
            // "this is disabled on purpose".
            try {
                w.setTooltip(Tooltip.create(
                        Component.literal(MultiplayerBan.label(key) + " — disabled: ")
                                .append(Component.translatable(key))
                                .append(Component.literal(" (" + MultiplayerBan.reason() + ")"))));
            } catch (Throwable t) {
                ModLog.swallowed("MultiplayerGuard.tooltip", t);
            }

            MultiplayerBan.logOnce();
        } catch (Throwable t) {
            ModLog.swallowed("MultiplayerGuard.banWidget", t);
        }
    }
}

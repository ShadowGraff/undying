package com.undying.core.client;

import com.undying.core.ModLog;
import com.undying.core.UndyingMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;

/**
 * Top-left corner: the commands that just ran, one line each.
 *
 * <h3>Why a translucent overlay and not chat</h3>
 * Chat already has a job, and a command block firing repeatedly would push real conversation off
 * the screen. The corner is where a player glances without losing the crosshair, and the window
 * disappears on its own — nothing has to be dismissed.
 *
 * <h3>Why repeated commands collapse instead of stacking</h3>
 * A command block on a loop produces the same line twenty times a second. Four copies of one
 * sentence tells the player nothing that one copy does not, so an identical repeat refreshes the
 * existing line rather than adding a fifth.
 */
@Mod.EventBusSubscriber(modid = UndyingMod.MODID, value = Dist.CLIENT)
public final class CommandAuditHud {

    private static final int MAX_LINES = 4;
    private static final long TTL_MS = 8000L;
    private static final int LINE_H = 13;

    private static final class Entry {
        final String text;
        long expiresAt;

        Entry(String text, long expiresAt) {
            this.text = text;
            this.expiresAt = expiresAt;
        }
    }

    private static final Deque<Entry> ENTRIES = new ArrayDeque<>();

    private CommandAuditHud() {}

    /** Called from the network handler on the client thread. */
    public static void push(String text) {
        if (text == null || text.isEmpty()) return;
        synchronized (ENTRIES) {
            long now = System.currentTimeMillis();

            Entry last = ENTRIES.peekLast();
            if (last != null && last.text.equals(text)) {
                last.expiresAt = now + TTL_MS;   // same command again: keep one line
                return;
            }

            ENTRIES.addLast(new Entry(text, now + TTL_MS));
            while (ENTRIES.size() > MAX_LINES) ENTRIES.removeFirst();
        }
    }

    @SubscribeEvent
    public static void onRender(RenderGuiEvent.Post event) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.options.hideGui) return;

            synchronized (ENTRIES) {
                long now = System.currentTimeMillis();
                Iterator<Entry> it = ENTRIES.iterator();
                while (it.hasNext()) {
                    if (it.next().expiresAt < now) it.remove();
                }
                if (ENTRIES.isEmpty()) return;

                GuiGraphics g = event.getGuiGraphics();
                int x = 4;
                int y = 4;
                for (Entry e : ENTRIES) {
                    int w = mc.font.width(e.text);
                    // Translucent black so it reads over snow, netherrack and lava alike.
                    g.fill(x - 2, y - 2, x + w + 2, y + 9, 0x90000000);
                    g.drawString(mc.font, e.text, x, y, 0xFFE8E8E8, false);
                    y += LINE_H;
                }
            }
        } catch (Throwable t) {
            ModLog.swallowed("CommandAuditHud.render", t);
        }
    }
}

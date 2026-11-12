package com.undying.core.client;

import com.undying.core.Networking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The teleport question: somewhere wanted to move you, here is where, allow it?
 *
 * <h3>Why a Screen and not a HUD overlay</h3>
 * The point of this one is to <b>freeze the game</b> until it is answered. A HUD line would let
 * the world keep running, and a hostile mod that teleports on a timer would simply try again
 * while the player reads. {@link #isPauseScreen()} returns true, so in a single-player world
 * the integrated server stops ticking behind the dialog.
 *
 * <p>The inventory guard's prompt is the opposite case on purpose: accepting an inventory
 * change is safe to ignore, so it must not interrupt. This one interrupts because the thing it
 * prevents is the player losing their position.
 *
 * <h3>No socket is left hanging</h3>
 * The server holds the destination and does not care how long the answer takes, but the screen
 * is the client's only path to answering, so it must always answer exactly once: a click, ESC
 * and the close key all route through {@link #finish(boolean)}.
 */
public class TeleportAskScreen extends Screen {

    private final String dimension;
    private final double x, y, z;
    private final String origin;
    private boolean answered;

    public TeleportAskScreen(String dimension, double x, double y, double z, String origin) {
        super(Component.translatable("undying.teleport.title"));
        this.dimension = dimension;
        this.x = x;
        this.y = y;
        this.z = z;
        this.origin = origin;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int y = this.height / 2 + 40;

        this.addRenderableWidget(Button.builder(Component.translatable("undying.teleport.yes"),
                        b -> finish(true))
                .bounds(cx - 155, y, 150, 20).build());

        this.addRenderableWidget(Button.builder(Component.translatable("undying.teleport.no"),
                        b -> finish(false))
                .bounds(cx + 5, y, 150, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // 1.20.1 only has the single-argument overload (the four-argument form is 1.20.2+).
        this.renderBackground(g);

        int cx = this.width / 2;
        int y = this.height / 2 - 70;

        g.drawCenteredString(this.font, this.title, cx, y, 0xFFFFFF);
        y += 26;

        g.drawCenteredString(this.font, Component.translatable("undying.teleport.body"),
                cx, y, 0xD0D0D0);
        y += 16;
        g.drawCenteredString(this.font, Component.literal(dimension), cx, y, 0xFFFF80);
        y += 16;
        g.drawCenteredString(this.font,
                Component.translatable("undying.teleport.coords",
                        fmt(x), fmt(y), fmt(z)),
                cx, y, 0xFFFFFF);
        y += 16;
        g.drawCenteredString(this.font,
                Component.translatable("undying.teleport.origin", origin),
                cx, y, 0xA0A0A0);

        y += 28;
        g.drawCenteredString(this.font, Component.translatable("undying.teleport.frozen"),
                cx, y, 0x909090);

        super.render(g, mouseX, mouseY, partialTick);
    }

    private static String fmt(double v) {
        return String.format("%.1f", v);
    }

    /** ESC and the close key mean "no", and they still answer. */
    @Override
    public void onClose() {
        finish(false);
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }

    private void finish(boolean accept) {
        if (this.answered) return;
        this.answered = true;
        try {
            Networking.sendTeleportAnswer(accept);
        } finally {
            if (this.minecraft != null) this.minecraft.setScreen(null);
        }
    }
}

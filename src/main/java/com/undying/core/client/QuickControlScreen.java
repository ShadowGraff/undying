package com.undying.core.client;

import com.undying.core.ModLog;
import com.undying.core.Networking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Time, weather and game mode, one click each.
 *
 * <h3>Why every group has a "resume" button</h3>
 * The other buttons freeze what they set, because a setting that drifts back is worse than no
 * setting. But a freeze the player cannot undo is a trap: they would have to remember which
 * vanilla gamerule to touch. Every group therefore ends with the action that gives the world
 * back to itself.
 *
 * <h3>Why the buttons do not close the screen</h3>
 * Wanting night and then rain is one visit, not two. The screen stays open so a player can
 * press what they mean and watch the result behind it.
 */
public class QuickControlScreen extends Screen {

    private static final int BUTTON_W = 76;
    private static final int GAP = 4;
    private static final int ROW_H = 20;

    private final Screen parent;

    public QuickControlScreen(Screen parent) {
        super(Component.translatable("undying.quick.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int y = this.height / 2 - 74;

        // Time: four presets, each freezes the clock, plus the action that lets it run again.
        int rowW5 = 5 * BUTTON_W + 4 * GAP;
        int x = cx - rowW5 / 2;
        y += 16;
        x = button(x, y, "quick.time_day", "time_day");
        x = button(x, y, "quick.time_noon", "time_noon");
        x = button(x, y, "quick.time_night", "time_night");
        x = button(x, y, "quick.time_midnight", "time_midnight");
        button(x, y, "quick.time_resume", "time_resume");

        // Weather: three states plus "let it change again".
        y += 40;
        int rowW4 = 4 * BUTTON_W + 3 * GAP;
        x = cx - rowW4 / 2;
        x = button(x, y, "quick.weather_clear", "weather_clear");
        x = button(x, y, "quick.weather_rain", "weather_rain");
        x = button(x, y, "quick.weather_thunder", "weather_thunder");
        button(x, y, "quick.weather_resume", "weather_resume");

        // Game mode.
        y += 40;
        x = cx - rowW4 / 2;
        x = button(x, y, "quick.mode_survival", "mode_survival");
        x = button(x, y, "quick.mode_creative", "mode_creative");
        x = button(x, y, "quick.mode_adventure", "mode_adventure");
        button(x, y, "quick.mode_spectator", "mode_spectator");

        this.addRenderableWidget(Button.builder(Component.translatable("gui.back"), b -> onClose())
                .bounds(cx - 50, this.height - 32, 100, 20).build());
    }

    /** Adds one button and returns the next x, so the rows stay aligned without magic numbers. */
    private int button(int x, int y, String key, String action) {
        this.addRenderableWidget(Button.builder(Component.translatable("undying." + key),
                        b -> send(action))
                .bounds(x, y, BUTTON_W, ROW_H).build());
        return x + BUTTON_W + GAP;
    }

    private void send(String action) {
        try {
            Networking.sendQuickAction(action);
        } catch (Throwable t) {
            ModLog.swallowed("QuickControlScreen.send", t);
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g);
        int cx = this.width / 2;
        g.drawCenteredString(this.font, this.title, cx, 20, 0xFFFFFF);

        int y = this.height / 2 - 74;
        g.drawString(this.font, Component.translatable("undying.quick.group_time"),
                cx - 150, y, 0xA0C0FF);
        g.drawString(this.font, Component.translatable("undying.quick.group_weather"),
                cx - 150, y + 40, 0xA0C0FF);
        g.drawString(this.font, Component.translatable("undying.quick.group_mode"),
                cx - 150, y + 80, 0xA0C0FF);

        g.drawCenteredString(this.font, Component.translatable("undying.quick.note"),
                cx, this.height - 52, 0x909090);

        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) this.minecraft.setScreen(this.parent);
    }
}

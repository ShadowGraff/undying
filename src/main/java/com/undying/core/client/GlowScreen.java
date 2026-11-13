package com.undying.core.client;

import com.undying.core.ModConfig;
import com.undying.core.ModLog;
import com.undying.core.Networking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The glow page: what to outline, for how long, how far, and whether to draw boxes.
 *
 * <h3>Why target and duration are separate</h3>
 * "What" is a preference and belongs in the config file where it survives a restart. "How long"
 * is a decision made at the moment of pressing it — a player exploring a cave wants two minutes,
 * a player building in the dark wants it until they turn it off. Mixing them into one switch
 * would mean editing a file to look around.
 *
 * <h3>Why the config changes are saved immediately</h3>
 * In single player the client and server share one {@code ModConfig}, so a write is live at once.
 * A screen without a Save button cannot leave the player wondering whether their click counted.
 */
public class GlowScreen extends Screen {

    private static final int BTN_W = 76;
    private static final int GAP = 4;
    private static final int ROW_H = 20;

    private final Screen parent;

    public GlowScreen(Screen parent) {
        super(Component.translatable("undying.glow.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int y = this.height / 2 - 76;

        // Target: four modes, one row.
        int rowW4 = 4 * BTN_W + 3 * GAP;
        int x = cx - rowW4 / 2;
        y += 16;
        x = target(x, y, "glow.target_friendly", "friendly");
        x = target(x, y, "glow.target_hostile", "hostile");
        x = target(x, y, "glow.target_all", "all");
        target(x, y, "glow.target_custom", "custom");

        // Duration: how long it stays on, decided each time.
        y += 40;
        x = cx - rowW4 / 2;
        x = duration(x, y, "glow.dur_30", 30);
        x = duration(x, y, "glow.dur_60", 60);
        x = duration(x, y, "glow.dur_300", 300);
        duration(x, y, "glow.dur_forever", 0);

        // Range.
        y += 40;
        x = cx - rowW4 / 2;
        x = range(x, y, "glow.range_16", 16);
        x = range(x, y, "glow.range_32", 32);
        x = range(x, y, "glow.range_48", 48);
        range(x, y, "glow.range_64", 64);

        // Hitbox and off.
        y += 40;
        this.addRenderableWidget(Button.builder(hitboxLabel(), b -> {
                    ModConfig.GLOW_HITBOX.set(!ModConfig.GLOW_HITBOX.get());
                    ModConfig.save();
                    this.rebuildWidgets();
                })
                .bounds(cx - rowW4 / 2, y, 152, ROW_H).build());

        this.addRenderableWidget(Button.builder(Component.translatable("undying.glow.off"),
                        b -> Networking.sendGlow(-1))
                .bounds(cx - rowW4 / 2 + 156, y, 152, ROW_H).build());

        this.addRenderableWidget(Button.builder(Component.translatable("gui.back"), b -> onClose())
                .bounds(cx - 50, this.height - 32, 100, 20).build());
    }

    private Component hitboxLabel() {
        boolean on;
        try {
            on = ModConfig.GLOW_HITBOX.get();
        } catch (Throwable t) {
            on = false;
        }
        return Component.translatable("undying.glow.hitbox")
                .append(Component.literal(": "))
                .append(Component.translatable(on ? "undying.glow.on" : "undying.glow.off_state"));
    }

    private int target(int x, int y, String key, String value) {
        this.addRenderableWidget(Button.builder(Component.translatable("undying." + key), b -> {
            try {
                ModConfig.GLOW_TARGET.set(value);
                ModConfig.save();
            } catch (Throwable t) {
                ModLog.swallowed("GlowScreen.target", t);
            }
        }).bounds(x, y, BTN_W, ROW_H).build());
        return x + BTN_W + GAP;
    }

    private int duration(int x, int y, String key, int seconds) {
        this.addRenderableWidget(Button.builder(Component.translatable("undying." + key),
                        b -> Networking.sendGlow(seconds))
                .bounds(x, y, BTN_W, ROW_H).build());
        return x + BTN_W + GAP;
    }

    private int range(int x, int y, String key, int blocks) {
        this.addRenderableWidget(Button.builder(Component.translatable("undying." + key), b -> {
            try {
                ModConfig.GLOW_RANGE.set(blocks);
                ModConfig.save();
            } catch (Throwable t) {
                ModLog.swallowed("GlowScreen.range", t);
            }
        }).bounds(x, y, BTN_W, ROW_H).build());
        return x + BTN_W + GAP;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g);

        int cx = this.width / 2;
        g.drawCenteredString(this.font, this.title, cx, 20, 0xFFFFFF);

        int y = this.height / 2 - 76;
        g.drawString(this.font, Component.translatable("undying.glow.group_target"),
                cx - 150, y, 0xA0C0FF);
        g.drawString(this.font, Component.translatable("undying.glow.group_duration"),
                cx - 150, y + 40, 0xA0C0FF);
        g.drawString(this.font, Component.translatable("undying.glow.group_range"),
                cx - 150, y + 80, 0xA0C0FF);

        g.drawCenteredString(this.font, Component.translatable("undying.glow.note"),
                cx, this.height - 52, 0x909090);

        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) this.minecraft.setScreen(this.parent);
    }
}

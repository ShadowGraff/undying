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
        this.addRenderableWidget(Button.builder(marked("undying." + key, isCurrentTarget(value)), b -> {
            try {
                ModConfig.GLOW_TARGET.set(value);
                ModConfig.save();
                this.rebuildWidgets();   // so the marker moves to the new choice
            } catch (Throwable t) {
                ModLog.swallowed("GlowScreen.target", t);
            }
        }).bounds(x, y, BTN_W, ROW_H).build());
        return x + BTN_W + GAP;
    }

    private boolean isCurrentTarget(String value) {
        try {
            return value.equalsIgnoreCase(String.valueOf(ModConfig.GLOW_TARGET.get()));
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Duration buttons also switch the feature on.
     *
     * <h3>Why this was a bug</h3>
     * The switch lives on the parent page, the duration lives here. Pressing "30 s" while the
     * switch was off sent the request, the server saw {@code GLOW == false} and returned
     * silently — no outline, no message, nothing. From the player's side that is "the feature
     * does not work", and there was no way to tell it apart from a real failure. A control that
     * does nothing must say so or turn on what it needs; this one turns it on.
     */
    private int duration(int x, int y, String key, int seconds) {
        this.addRenderableWidget(Button.builder(Component.translatable("undying." + key),
                        b -> {
                            try {
                                if (!ModConfig.GLOW.get()) {
                                    ModConfig.GLOW.set(true);
                                    ModConfig.save();
                                }
                            } catch (Throwable t) {
                                ModLog.swallowed("GlowScreen.duration.enable", t);
                            }
                            Networking.sendGlow(seconds);
                            this.rebuildWidgets();
                        })
                .bounds(x, y, BTN_W, ROW_H).build());
        return x + BTN_W + GAP;
    }

    private int range(int x, int y, String key, int blocks) {
        this.addRenderableWidget(Button.builder(marked("undying." + key, isCurrentRange(blocks)), b -> {
            try {
                ModConfig.GLOW_RANGE.set(blocks);
                ModConfig.save();
                this.rebuildWidgets();
            } catch (Throwable t) {
                ModLog.swallowed("GlowScreen.range", t);
            }
        }).bounds(x, y, BTN_W, ROW_H).build());
        return x + BTN_W + GAP;
    }

    private boolean isCurrentRange(int blocks) {
        try {
            return ModConfig.GLOW_RANGE.get() == blocks;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Prepends a dot to the selected option, so the page shows its own state. */
    private Component marked(String key, boolean active) {
        Component label = Component.translatable(key);
        return active ? Component.literal("▶ ").append(label) : Component.literal("   ").append(label);
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
                cx, this.height - 66, 0x909090);
        // A shader mod rewrites the outline pipeline this feature depends on. Say so in the
        // warning colour when one is loaded, instead of letting the player conclude the
        // feature is broken. The hitbox renderer is ours and works either way.
        g.drawCenteredString(this.font, Component.translatable("undying.glow.shader_note"),
                cx, this.height - 54, shadersLoaded() ? 0xE0B050 : 0x909090);

        boolean on;
        try {
            on = ModConfig.GLOW.get();
        } catch (Throwable t) {
            on = false;
        }
        g.drawCenteredString(this.font,
                Component.translatable(on ? "undying.glow.status_on" : "undying.glow.status_off"),
                cx, this.height - 78, on ? 0x7FD87F : 0xE0B050);

        super.render(g, mouseX, mouseY, partialTick);
    }

    /**
     * Whether a shader mod is present.
     *
     * <p>Only used to decide the colour of one line of text, so it is deliberately a plain
     * lookup with no caching and no reflection: {@code ModList} is on both sides and the ids
     * are stable. If it throws, the line is drawn in the neutral colour — the warning is still
     * shown, just not highlighted.
     */
    private static boolean shadersLoaded() {
        try {
            return net.minecraftforge.fml.ModList.get().isLoaded("oculus")
                    || net.minecraftforge.fml.ModList.get().isLoaded("iris")
                    || net.minecraftforge.fml.ModList.get().isLoaded("optifine");
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) this.minecraft.setScreen(this.parent);
    }
}

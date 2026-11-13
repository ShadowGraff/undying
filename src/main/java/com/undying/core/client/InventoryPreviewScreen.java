package com.undying.core.client;

import com.undying.core.Networking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.List;

/**
 * What the inventory guard has seen change, shown on demand.
 *
 * <h3>Why this pauses the game</h3>
 * The point of holding a key for three seconds is that the player wants to <i>read</i>. A
 * HUD overlay would leave the world running underneath, and a hostile mod could move items out
 * of the inventory while the player is reading the list of what it already moved.
 *
 * <h3>Why the server produced the list</h3>
 * The baseline lives in the guard's per-player state on the server. A client-computed diff
 * would be a second opinion about the same fact, and the two would eventually disagree — with
 * the client's version being the one the player believes.
 */
public class InventoryPreviewScreen extends Screen {

    private static final int ROW_HEIGHT = 11;
    private static final int LIST_TOP = 52;

    private final boolean pending;
    private final boolean hasBaseline;
    private final List<String[]> rows;

    private int scroll;

    public InventoryPreviewScreen(boolean pending, boolean hasBaseline, List<String[]> rows) {
        super(Component.translatable("undying.preview.title"));
        this.pending = pending;
        this.hasBaseline = hasBaseline;
        this.rows = rows;
    }

    @Override
    protected void init() {
        this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(this.width / 2 - 50, this.height - 32, 100, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // 1.20.1 has only the single-argument overload.
        this.renderBackground(g);

        int cx = this.width / 2;
        g.drawCenteredString(this.font, this.title, cx, 16, 0xFFFFFF);

        // Which comparison this list is: an open rollback question, or "since you last looked".
        Component sub = Component.translatable(pending
                ? "undying.preview.pending"
                : "undying.preview.since_last");
        g.drawCenteredString(this.font, sub, cx, 30, pending ? 0xFFB0B0 : 0xA0C0FF);

        if (!hasBaseline) {
            g.drawCenteredString(this.font, Component.translatable("undying.preview.no_baseline"),
                    cx, this.height / 2, 0xC0C0C0);
        } else if (rows.isEmpty()) {
            g.drawCenteredString(this.font, Component.translatable("undying.preview.empty"),
                    cx, this.height / 2, 0x90D090);
        } else {
            renderRows(g);
        }

        g.drawCenteredString(this.font, Component.translatable("undying.preview.hint"),
                cx, this.height - 44, 0x808080);

        super.render(g, mouseX, mouseY, partialTick);
    }

    private void renderRows(GuiGraphics g) {
        int visible = Math.max(1, (this.height - LIST_TOP - 56) / ROW_HEIGHT);
        clampScroll(visible);

        int left = this.width / 2 - 190;
        int y = LIST_TOP;
        int end = Math.min(rows.size(), scroll + visible);
        for (int i = scroll; i < end; i++) {
            String[] r = rows.get(i);
            int slot = parse(r[0]);
            g.drawString(this.font, line(slot, r[1], r[2]), left, y, 0xE0E0E0);
            y += ROW_HEIGHT;
        }

        if (rows.size() > visible) {
            String pos = (scroll + 1) + "–" + end + " / " + rows.size();
            g.drawString(this.font, pos, left, LIST_TOP - 13, 0x707070);
        }
    }

    /** {@code [hotbar 1] diamond x3 → (empty)} */
    private MutableComponent line(int slot, String before, String after) {
        MutableComponent out = slotLabel(slot).copy();
        out.append(Component.literal(": "));
        out.append(before.isEmpty()
                ? Component.translatable("undying.preview.empty_slot")
                : Component.literal(before));
        out.append(Component.literal("  →  "));
        out.append(after.isEmpty()
                ? Component.translatable("undying.preview.empty_slot")
                : Component.literal(after));
        return out;
    }

    /**
     * Slot index to a readable name.
     *
     * <p>The indices are the vanilla inventory layout: 0-8 hotbar, 9-35 main, 36-39 armour,
     * 40 offhand. Anything beyond that comes from a modded inventory and is shown by number
     * rather than being given an invented name.
     */
    private Component slotLabel(int slot) {
        if (slot < 0) return Component.translatable("undying.preview.slot_unknown", slot);
        if (slot < 9) return Component.translatable("undying.preview.slot_hotbar", slot + 1);
        if (slot < 36) return Component.translatable("undying.preview.slot_main", slot - 8);
        if (slot < 40) return Component.translatable("undying.preview.slot_armor", slot - 35);
        if (slot == 40) return Component.translatable("undying.preview.slot_offhand");
        return Component.translatable("undying.preview.slot_unknown", slot);
    }

    private static int parse(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private void clampScroll(int visible) {
        int max = Math.max(0, rows.size() - visible);
        if (scroll > max) scroll = max;
        if (scroll < 0) scroll = 0;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        scroll -= (int) Math.signum(delta);
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }

    /**
     * Marking things as seen on close, but only when this was not a pending rollback question:
     * those changes have not been decided yet, and forgetting them because the list was opened
     * would quietly discard the very thing the player was about to answer.
     */
    @Override
    public void onClose() {
        try {
            if (!pending) Networking.sendInventoryAcknowledge();
        } finally {
            if (this.minecraft != null) this.minecraft.setScreen(null);
        }
    }
}

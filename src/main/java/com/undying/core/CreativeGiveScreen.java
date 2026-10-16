package com.undying.core;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

/**
 * Creative item menu (usable in survival mode):
 *  - Top: a grid of all items plus a search box. Left-click takes a full stack, right-click takes 1
 *    (sent over the network and granted by the server)
 *  - Bottom: the player's real survival inventory (read-only display); pressing E still opens the
 *    vanilla inventory for crafting/organizing
 */
@OnlyIn(Dist.CLIENT)
public class CreativeGiveScreen extends Screen {
    private static final int COLS = 9;
    private static final int ROWS = 5;
    private static final int SLOT = 18;

    private EditBox search;
    private final List<ItemStack> allItems = new ArrayList<>();
    private final List<ItemStack> filtered = new ArrayList<>();
    private int scroll = 0;
    private int gridLeft;
    private int gridTop;

    public CreativeGiveScreen() {
        super(Component.literal("Creative Inventory"));
    }

    @Override
    protected void init() {
        allItems.clear();
        for (net.minecraft.world.item.Item it : ForgeRegistries.ITEMS) {
            if (it == Items.AIR) continue;
            allItems.add(new ItemStack(it));
        }
        gridLeft = (width - COLS * SLOT) / 2;
        gridTop = 32;
        search = new EditBox(this.font, gridLeft, 8, COLS * SLOT, 16, Component.literal("Search"));
        search.setMaxLength(64);
        addRenderableWidget(search);
        applyFilter("");
    }

    private void applyFilter(String q) {
        filtered.clear();
        String query = q == null ? "" : q.trim().toLowerCase();
        for (ItemStack s : allItems) {
            String name = s.getHoverName().getString().toLowerCase();
            String id = ForgeRegistries.ITEMS.getKey(s.getItem()).toString().toLowerCase();
            if (query.isEmpty() || name.contains(query) || id.contains(query)) filtered.add(s);
        }
        scroll = 0;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float delta) {
        renderBackground(g);
        super.render(g, mx, my, delta);

        // ── Creative item grid ──
        int start = scroll * COLS;
        int end = Math.min(filtered.size(), start + COLS * ROWS);
        for (int i = start; i < end; i++) {
            int col = (i - start) % COLS;
            int row = (i - start) / COLS;
            int x = gridLeft + col * SLOT;
            int y = gridTop + row * SLOT;
            g.fill(x - 1, y - 1, x + 17, y + 17, 0x66FFFFFF);
            ItemStack s = filtered.get(i);
            g.renderItem(s, x, y);
            if (mx >= x && mx < x + SLOT && my >= y && my < y + SLOT) {
                g.fill(x - 1, y - 1, x + 17, y + 17, 0x44FFFF00);
                g.renderTooltip(this.font, s, mx, my);
            }
        }
        int maxScroll = Math.max(0, (filtered.size() + COLS - 1) / COLS - ROWS);
        g.drawString(this.font, "Creative Inventory (LMB = stack / RMB = one / wheel = page)", gridLeft, 22, 0xFFFFFF);
        if (maxScroll > 0) {
            g.drawString(this.font, (scroll + 1) + "/" + (maxScroll + 1), gridLeft + COLS * SLOT - 30, 22, 0xAAAAAA);
        }

        // ── Survival inventory below (read-only) ──
        int by = gridTop + ROWS * SLOT + 8;
        g.drawString(this.font, "Survival inventory (press E for vanilla crafting/sorting)", gridLeft, by - 10, 0xFFFFFF);
        Inventory inv = this.minecraft.player.getInventory();
        int idx = 0;
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < COLS; col++) {
                int x = gridLeft + col * SLOT;
                int y = by + row * SLOT;
                g.fill(x - 1, y - 1, x + 17, y + 17, 0x44AAAAAA);
                ItemStack s = inv.items.get(idx);
                if (!s.isEmpty()) g.renderItem(s, x, y);
                idx++;
            }
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (search.mouseClicked(mx, my, btn)) return true;
        int start = scroll * COLS;
        int end = Math.min(filtered.size(), start + COLS * ROWS);
        for (int i = start; i < end; i++) {
            int col = (i - start) % COLS;
            int row = (i - start) / COLS;
            int x = gridLeft + col * SLOT;
            int y = gridTop + row * SLOT;
            if (mx >= x && mx < x + SLOT && my >= y && my < y + SLOT) {
                ItemStack s = filtered.get(i);
                int count = btn == 1 ? 1 : Math.min(s.getMaxStackSize(), Const.GIVE_ITEM_MAX_COUNT);
                Networking.CHANNEL.sendToServer(new Networking.GiveItemMessage(
                        ForgeRegistries.ITEMS.getKey(s.getItem()).toString(), count));
                // The client screen has no ServerPlayer, so reuse the same color scheme via Msg.render
                this.minecraft.player.displayClientMessage(
                        Msg.render("creative", "Obtained " + count + "x " + s.getHoverName().getString()), true);
                return true;
            }
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        int maxScroll = Math.max(0, (filtered.size() + COLS - 1) / COLS - ROWS);
        scroll = Mth.clamp(scroll - (int) delta, 0, maxScroll);
        return true;
    }

    @Override
    public boolean charTyped(char c, int k) {
        if (search.charTyped(c, k)) { applyFilter(search.getValue()); return true; }
        return super.charTyped(c, k);
    }

    @Override
    public boolean keyPressed(int k, int sc, int mod) {
        if (search.keyPressed(k, sc, mod)) { applyFilter(search.getValue()); return true; }
        return super.keyPressed(k, sc, mod);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}

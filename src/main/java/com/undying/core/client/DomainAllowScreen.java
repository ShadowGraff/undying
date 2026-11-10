package com.undying.core.client;

import com.undying.core.DomainFilter;
import com.undying.core.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Allow-list editor for the domain: which mob categories and which exact entities may
 * approach the player.
 *
 * <p>Two halves, because they answer different questions:
 * <ul>
 *   <li><b>Categories</b> — the coarse switch. "All vanilla animals" is one click.</li>
 *   <li><b>Exact entities</b> — the exceptions. Searchable, because there are hundreds of
 *       registered types and scrolling to {@code minecraft:zombie_villager} by hand is not
 *       a feature.</li>
 * </ul>
 *
 * <p>Every change is written immediately. There is no Save button on purpose: a checkbox
 * that needs a second confirm step is the kind of thing that gets "saved" wrongly.
 */
@OnlyIn(Dist.CLIENT)
public class DomainAllowScreen extends Screen {

    private final Screen parent;
    private final List<Button> categoryButtons = new ArrayList<>();
    private EntityList list;
    private EditBox search;

    public DomainAllowScreen(Screen parent) {
        super(Component.literal("领域 · 放行名单"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        categoryButtons.clear();

        // ── Category toggles, two columns ──
        MobCategory[] categories = MobCategory.values();
        int colW = 148;
        int rowH = 20;
        int gap = 4;
        int startY = 44;
        int perCol = (categories.length + 1) / 2;
        int startX = this.width / 2 - colW - gap / 2;

        for (int i = 0; i < categories.length; i++) {
            MobCategory cat = categories[i];
            int col = i / perCol;
            int row = i % perCol;
            int x = startX + col * (colW + gap);
            int y = startY + row * (rowH + 4);
            Button b = Button.builder(labelFor(cat), btn -> flipCategory(cat))
                    .bounds(x, y, colW, rowH).build();
            categoryButtons.add(b);
            this.addRenderableWidget(b);
        }

        // ── Search box ──
        int listTop = startY + perCol * (rowH + 4) + 8;
        this.search = new EditBox(this.font, this.width / 2 - 120, listTop - 22, 240, 18,
                Component.literal("搜索实体"));
        this.search.setResponder(s -> rebuildList());
        this.addRenderableWidget(this.search);

        // ── Entity list ──
        int bottom = this.height - 36;
        this.list = new EntityList(this.minecraft, this.width, bottom - listTop, listTop, 20);
        this.addWidget(this.list);
        rebuildList();

        this.addRenderableWidget(Button.builder(Component.literal("返回"), b -> onClose())
                .bounds(this.width / 2 - 75, this.height - 30, 150, 20).build());
    }

    private Component labelFor(MobCategory cat) {
        boolean on = categoryAllowed(cat);
        return Component.literal(cat.name() + ": " + (on ? "§a放行" : "§c排斥"));
    }

    private boolean categoryAllowed(MobCategory cat) {
        try {
            for (String s : ModConfig.DOMAIN_ALLOW_CATEGORIES.get()) {
                if (String.valueOf(s).trim().equalsIgnoreCase(cat.name())) return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private void flipCategory(MobCategory cat) {
        try {
            List<String> cats = new ArrayList<>();
            for (String s : ModConfig.DOMAIN_ALLOW_CATEGORIES.get()) cats.add(String.valueOf(s));

            if (categoryAllowed(cat)) {
                cats.removeIf(s -> s.trim().equalsIgnoreCase(cat.name()));
            } else {
                cats.add(cat.name());
            }
            ModConfig.DOMAIN_ALLOW_CATEGORIES.set(cats);
            ModConfig.save();

            // Refresh the button labels in place rather than rebuilding the screen: the list
            // scroll position would be lost otherwise.
            for (int i = 0; i < categoryButtons.size(); i++) {
                MobCategory c = MobCategory.values()[i];
                categoryButtons.get(i).setMessage(labelFor(c));
            }
        } catch (Throwable ignored) {
        }
    }

    private void rebuildList() {
        if (this.list == null) return;

        String query = this.search == null ? "" : this.search.getValue().toLowerCase(Locale.ROOT).trim();

        List<ResourceLocation> ids = new ArrayList<>(BuiltInRegistries.ENTITY_TYPE.keySet());
        ids.sort(Comparator.comparing(ResourceLocation::toString));

        List<EntityEntry> entries = new ArrayList<>();
        for (ResourceLocation id : ids) {
            String text = id.toString();
            if (!query.isEmpty() && !text.contains(query)) continue;
            entries.add(new EntityEntry(id));
        }
        this.list.replaceAll(entries);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g);

        g.drawCenteredString(this.font, this.title, this.width / 2, 12, 0xFFFFFF);
        g.drawCenteredString(this.font,
                Component.literal("勾选类别或具体实体；未勾选的一律会被推开"),
                this.width / 2, 26, 0x909090);

        super.render(g, mouseX, mouseY, partialTick);

        // The list draws itself through super.render; the hint goes underneath afterwards.
        g.drawCenteredString(this.font,
                Component.literal("分类共 " + MobCategory.values().length + " 项 · 实体点击即切换"),
                this.width / 2, this.height - 44, 0x707070);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }

    // ═══════════════════════════════════════════════════════════

    private class EntityList extends ObjectSelectionList<EntityEntry> {

        EntityList(Minecraft mc, int width, int height, int y0, int itemHeight) {
            // y1 is an absolute screen coordinate, not a height: the list would have zero
            // height if the caller's height were passed straight through.
            super(mc, width, height, y0, y0 + height, itemHeight);
        }

        @Override
        public int getRowWidth() {
            return 260;
        }

        @Override
        protected int getScrollbarPosition() {
            return DomainAllowScreen.this.width / 2 + 135;
        }

        /**
         * Replacing the whole content in one call.
         *
         * <p>{@code clearEntries()} and {@code addEntry()} are protected on the vanilla
         * superclass, so the screen cannot drive the list directly from outside.
         */
        void replaceAll(List<EntityEntry> entries) {
            clearEntries();
            for (EntityEntry e : entries) addEntry(e);
        }
    }

    private class EntityEntry extends ObjectSelectionList.Entry<EntityEntry> {

        private final ResourceLocation id;

        EntityEntry(ResourceLocation id) {
            this.id = id;
        }

        @Override
        public void render(GuiGraphics g, int index, int top, int left, int width, int height,
                           int mouseX, int mouseY, boolean hovered, float partialTick) {
            boolean allowed = entityAllowed(this.id);
            String prefix = allowed ? "§a✔ " : "§7▢ ";
            g.drawString(DomainAllowScreen.this.font, prefix + this.id, left + 4, top + 6,
                    hovered ? 0xFFFFA0 : 0xFFFFFF);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            flipEntity(this.id);
            return true;
        }

        @Override
        public Component getNarration() {
            return Component.literal(this.id.toString());
        }
    }

    private boolean entityAllowed(ResourceLocation id) {
        try {
            for (String s : ModConfig.DOMAIN_ALLOW_ENTITIES.get()) {
                if (String.valueOf(s).trim().equalsIgnoreCase(id.toString())) return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private void flipEntity(ResourceLocation id) {
        try {
            List<String> ents = new ArrayList<>();
            for (String s : ModConfig.DOMAIN_ALLOW_ENTITIES.get()) ents.add(String.valueOf(s));

            boolean allowed = false;
            for (String s : ents) {
                if (s.trim().equalsIgnoreCase(id.toString())) {
                    allowed = true;
                    break;
                }
            }

            if (allowed) {
                ents.removeIf(s -> s.trim().equalsIgnoreCase(id.toString()));
            } else {
                ents.add(id.toString());
            }
            ModConfig.DOMAIN_ALLOW_ENTITIES.set(ents);
            ModConfig.save();
        } catch (Throwable ignored) {
        }
    }
}

package com.undying.core.client;

import com.undying.core.ModConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.common.ForgeConfigSpec;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The configuration screen, opened with O by default (the key is a real
 * {@code KeyMapping}, so it also appears in Options → Controls → Key Binds and the player
 * can rebind it there without this mod building a binding UI of its own).
 *
 * <h3>Cascade rule (the user's decision)</h3>
 * Turning <b>any</b> switch off turns off immortal mode and every switch under it. The
 * reasoning is that immortal mode is one contract, not a menu of independent extras: a
 * player who disables, say, the totem protection but keeps the death lock would get a
 * configuration whose behaviour nobody has ever tested. Turning something <i>on</i> only
 * affects that one switch; the master switch is never turned on implicitly.
 */
@OnlyIn(Dist.CLIENT)
public class UndyingConfigScreen extends Screen {

    /** One row in the list: a label plus how to read and write the config value. */
    private record Toggle(String id, String label, Supplier<Boolean> get, Consumer<Boolean> set) {}

    private final Screen parent;
    private final List<Toggle> toggles = new ArrayList<>();

    public UndyingConfigScreen(Screen parent) {
        super(Component.literal("Undying 配置"));
        this.parent = parent;
        buildToggles();
    }

    private void buildToggles() {
        add("masterToggle", "不朽模式", ModConfig.MASTER_TOGGLE);
        add("deathLock", "死亡锁", ModConfig.DEATH_LOCK);
        add("killBlocker", "拦截 /kill", ModConfig.KILL_BLOCKER);
        add("gameModeLock", "锁定游戏模式", ModConfig.GAMEMODE_LOCK);
        add("cheatPerms", "自动 OP", ModConfig.CHEAT_PERMS);
        add("opLock", "OP 不被撤销", ModConfig.OP_LOCK);
        add("totemProtect", "图腾保护", ModConfig.TOTEM_PROTECT);
        add("hiddenGear", "隐藏装备", ModConfig.HIDDEN_GEAR);
        add("voidRescue", "虚空救援", ModConfig.VOID_RESCUE);
        add("permaEffects", "常驻增益", ModConfig.PERMA_EFFECTS);
        add("spawnProtect", "出生保护", ModConfig.SPAWN_PROTECT);
        add("deathAura", "死亡光环", ModConfig.DEATH_AURA);
        add("damageBoost", "伤害增幅", ModConfig.DAMAGE_BOOST);
        add("antiTamper", "反篡改", ModConfig.ANTI_TAMPER);
        add("integrityGuard", "完整性守卫", ModConfig.INTEGRITY_GUARD);
        add("saveSnapshot", "状态回滚", ModConfig.SAVE_SNAPSHOT);
        add("autoRestore", "自动回滚", ModConfig.SAVE_SNAPSHOT_RESTORE);
        add("threatScan", "模组扫描", ModConfig.THREAT_SCAN);
        add("inventoryGuard", "物品栏快照", ModConfig.INVENTORY_GUARD);
        add("domain", "领域（排斥生物）", ModConfig.DOMAIN_ENABLED);
    }

    private void add(String id, String label, ForgeConfigSpec.BooleanValue value) {
        toggles.add(new Toggle(id, label, value::get, value::set));
    }

    @Override
    protected void init() {
        int columns = 2;
        int perColumn = (toggles.size() + columns - 1) / columns;
        int w = 150;
        int h = 20;
        int gapX = 8;
        int gapY = 4;

        int totalW = columns * w + gapX;
        int startX = (this.width - totalW) / 2;
        int startY = 48;

        for (int i = 0; i < toggles.size(); i++) {
            Toggle t = toggles.get(i);
            int col = i / perColumn;
            int row = i % perColumn;
            int x = startX + col * (w + gapX);
            int y = startY + row * (h + gapY);

            this.addRenderableWidget(Button.builder(label(t), b -> flip(t))
                    .bounds(x, y, w, h).build());
        }

        int bottom = this.height - 40;
        this.addRenderableWidget(Button.builder(Component.literal("领域白名单…"),
                        b -> this.minecraft.setScreen(new DomainAllowScreen(this)))
                .bounds(this.width / 2 - 155, bottom, 150, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("完成"),
                        b -> onClose())
                .bounds(this.width / 2 + 5, bottom, 150, 20).build());
    }

    private Component label(Toggle t) {
        boolean on;
        try {
            on = t.get.get();
        } catch (Throwable ignored) {
            on = false;
        }
        return Component.literal(t.label + ": " + (on ? "§a开" : "§c关"));
    }

    /**
     * Flip one switch and apply the cascade.
     *
     * <p>Rebuilding the widgets is the simplest way to refresh twenty labels; the list is
     * tiny and this only happens on a click.
     */
    private void flip(Toggle t) {
        int index = toggles.indexOf(t);
        if (index < 0) return;

        boolean[] current = new boolean[toggles.size()];
        for (int i = 0; i < toggles.size(); i++) {
            try {
                current[i] = toggles.get(i).get.get();
            } catch (Throwable ignored) {
                current[i] = false;   // one unreadable entry must not strand the others
            }
        }

        boolean[] next = CascadeRule.afterFlip(current, index);
        for (int i = 0; i < toggles.size(); i++) {
            if (next[i] != current[i]) setQuietly(toggles.get(i), next[i]);
        }

        ModConfig.save();
        this.rebuildWidgets();
    }

    private void setQuietly(Toggle t, boolean value) {
        try {
            t.set.accept(value);
        } catch (Throwable ignored) {
            // A single unreadable config entry must not stop the rest from updating.
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g);

        g.drawCenteredString(this.font, this.title, this.width / 2, 16, 0xFFFFFF);
        g.drawCenteredString(this.font,
                Component.literal("关闭任意一项，会连同不朽模式及所有子项一起关闭"),
                this.width / 2, 30, 0x909090);

        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }
}

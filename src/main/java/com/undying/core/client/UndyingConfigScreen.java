package com.undying.core.client;

import com.undying.core.ModConfig;
import net.minecraft.ChatFormatting;
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
 * <h3>Switch rules (current)</h3>
 * Immortal mode is a total switch: turning it off turns everything off, because that is what a
 * total switch means. Turning any single child off affects only that child — a player who
 * silences the aura should not lose the death lock as a side effect of one click.
 * Turning something <i>on</i> only affects that switch; the master is never enabled implicitly.
 *
 * <h3>Text</h3>
 * Every label is a translation key resolved from {@code assets/undying/lang/}. The older
 * screens wrote Chinese strings inline, which meant a player running the game in English saw
 * Chinese buttons; that is fixed here for the whole client UI, not just for new screens.
 */
@OnlyIn(Dist.CLIENT)
public class UndyingConfigScreen extends Screen {

    /** Index of the total switch in {@link #toggles}; the list is built in order. */
    private static final int MASTER_INDEX = 0;

    /** One row in the list: a translation key plus how to read and write the config value. */
    private record Toggle(String key, Supplier<Boolean> get, Consumer<Boolean> set) {}

    private final Screen parent;
    private final List<Toggle> toggles = new ArrayList<>();

    public UndyingConfigScreen(Screen parent) {
        super(Component.translatable("undying.config.title"));
        this.parent = parent;
        buildToggles();
    }

    private void buildToggles() {
        add("masterToggle", ModConfig.MASTER_TOGGLE);
        add("deathLock", ModConfig.DEATH_LOCK);
        add("damageClamp", ModConfig.CLAMP_DAMAGE);
        add("killBlocker", ModConfig.KILL_BLOCKER);
        add("gameModeLock", ModConfig.GAMEMODE_LOCK);
        add("cheatPerms", ModConfig.CHEAT_PERMS);
        add("opLock", ModConfig.OP_LOCK);
        add("totemProtect", ModConfig.TOTEM_PROTECT);
        add("hiddenGear", ModConfig.HIDDEN_GEAR);
        add("voidRescue", ModConfig.VOID_RESCUE);
        add("permaEffects", ModConfig.PERMA_EFFECTS);
        add("spawnProtect", ModConfig.SPAWN_PROTECT);
        add("deathAura", ModConfig.DEATH_AURA);
        add("damageBoost", ModConfig.DAMAGE_BOOST);
        add("antiTamper", ModConfig.ANTI_TAMPER);
        add("integrityGuard", ModConfig.INTEGRITY_GUARD);
        add("saveSnapshot", ModConfig.SAVE_SNAPSHOT);
        add("autoRestore", ModConfig.SAVE_SNAPSHOT_RESTORE);
        add("threatScan", ModConfig.THREAT_SCAN);
        add("inventoryGuard", ModConfig.INVENTORY_GUARD);
        add("teleportGuard", ModConfig.TELEPORT_GUARD);
        add("worldGuard", ModConfig.WORLD_GUARD);
        add("worldBackup", ModConfig.WORLD_BACKUP);
        add("flight", ModConfig.FLIGHT);
        add("quickControls", ModConfig.QUICK_CONTROLS);
        add("commandAudit", ModConfig.COMMAND_AUDIT);
        add("glow", ModConfig.GLOW);
        add("domain", ModConfig.DOMAIN_ENABLED);
    }

    private void add(String id, ForgeConfigSpec.BooleanValue value) {
        toggles.add(new Toggle("undying.option." + id, value::get, value::set));
    }

    @Override
    protected void init() {
        ConfigLayout.Plan plan = ConfigLayout.plan(toggles.size(), this.width, this.height);
        int w = ConfigLayout.columnWidth(this.width, plan.columns());
        int h = plan.buttonHeight();
        int gapX = ConfigLayout.GAP_X;
        int startY = plan.startY();

        int totalW = plan.columns() * w + (plan.columns() - 1) * gapX;
        int startX = Math.max(8, (this.width - totalW) / 2);

        for (int i = 0; i < toggles.size(); i++) {
            Toggle t = toggles.get(i);
            int col = i / plan.perColumn();
            int row = i % plan.perColumn();
            int x = startX + col * (w + gapX);
            int y = startY + row * plan.pitch();

            this.addRenderableWidget(Button.builder(label(t), b -> flip(t))
                    .bounds(x, y, w, h).build());
        }

        // Bottom row: five entries. Widths are computed from the window so they cannot run into
        // each other on a narrow screen — the same rule the grid above follows.
        int bottom = ConfigLayout.bottomRowY(this.height);
        int gap = 4;
        int total = Math.min(this.width - 20, 5 * 64 + 4 * gap);
        int btnW = Math.max(42, (total - 4 * gap) / 5);
        int x = (this.width - (btnW * 5 + gap * 4)) / 2;

        this.addRenderableWidget(Button.builder(Component.translatable("undying.config.allow_list"),
                        b -> this.minecraft.setScreen(new DomainAllowScreen(this)))
                .bounds(x, bottom, btnW, 20).build());
        x += btnW + gap;
        this.addRenderableWidget(Button.builder(Component.translatable("undying.config.glow"),
                        b -> this.minecraft.setScreen(new GlowScreen(this)))
                .bounds(x, bottom, btnW, 20).build());
        x += btnW + gap;
        this.addRenderableWidget(Button.builder(Component.translatable("undying.config.quick"),
                        b -> this.minecraft.setScreen(new QuickControlScreen(this)))
                .bounds(x, bottom, btnW, 20).build());
        x += btnW + gap;
        this.addRenderableWidget(Button.builder(Component.translatable("undying.config.backup"),
                        b -> this.minecraft.setScreen(new BackupScreen(this)))
                .bounds(x, bottom, btnW, 20).build());
        x += btnW + gap;
        this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(x, bottom, btnW, 20).build());
    }

    private Component label(Toggle t) {
        boolean on;
        try {
            on = t.get.get();
        } catch (Throwable ignored) {
            on = false;
        }
        Component state = Component.translatable(on ? "undying.state.on" : "undying.state.off")
                .withStyle(on ? ChatFormatting.GREEN : ChatFormatting.RED);
        return Component.translatable(t.key).append(": ").append(state);
    }

    /**
     * Flip one switch and apply the rules.
     *
     * <p>Rebuilding the widgets is the simplest way to refresh twenty labels; the list is tiny
     * and this only happens on a click.
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

        boolean[] next = CascadeRule.afterFlip(current, index, MASTER_INDEX);
        for (int i = 0; i < toggles.size(); i++) {
            if (next[i] != current[i]) setQuietly(toggles.get(i), next[i]);
        }

        ModConfig.save();

        // Re-capture the baseline here as well, not only over the network.
        //
        // Single-player client and integrated server share one JVM and one ModConfig instance, so
        // this is the same object the server reads. Relying on the packet alone meant that if it
        // was dropped (or handled a tick late), the panel showed the switch off while the gates
        // still used the old baseline - measured as "I turned immortal mode off and still could
        // not die". Capturing locally removes that dependency entirely.
        try {
            com.undying.core.ProtectionBaseline.capture();
        } catch (Throwable e) {
            com.undying.core.ModLog.swallowed("UndyingConfigScreen.captureBaseline", e);
        }

        // Tell the integrity guard this was the player's own hand, not an external mod. Without
        // this, turning a protected child switch off while immortal mode is on was undone
        // within two seconds by the guard's "repair" step — the switch refused to stay off.
        com.undying.core.Networking.sendConfigToggled();

        // The tick and packet handlers live on the server side, but they read the same
        // ModConfig instance, so the change is already live. Rebuilding only refreshes text.
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
        g.drawCenteredString(this.font, Component.translatable("undying.config.hint"),
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

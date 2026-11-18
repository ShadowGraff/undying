package com.undying.core.client;

import com.undying.core.ModConfig;
import com.undying.core.ModLog;
import com.undying.core.backup.BackupScheduler;
import com.undying.core.backup.BackupStore;
import com.undying.core.backup.BackupVersion;
import com.undying.core.backup.HumanStamp;
import com.undying.core.backup.TimePoints;
import com.undying.core.backup.WorldRestore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.nio.file.Path;
import java.util.List;

/**
 * The save-rescue page: which moments can be rolled back to, and one button to copy now.
 *
 * <h3>Why restoring is only offered with the world closed</h3>
 * Replacing world files while the game holds them open is the one thing this module must never do.
 * The page can tell the difference — the integrated server exists only while a world is loaded —
 * so it offers the list, explains why, and disables the buttons instead of failing halfway.
 *
 * <h3>Why the list is short and coarse</h3>
 * Twelve entries two seconds apart are not a choice. Moments within five minutes collapse into
 * the newest one, which is also the one that restores the most files.
 */
public class BackupScreen extends Screen {

    private static final int MAX_POINTS = 8;

    private final Screen parent;
    private List<Long> points = List.of();
    private String status = "";
    private boolean inWorld;

    public BackupScreen(Screen parent) {
        super(Component.translatable("undying.wb.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        Minecraft mc = Minecraft.getInstance();
        ClientWorldMemory.remember(mc);
        this.inWorld = mc.getSingleplayerServer() != null;
        Path world = ClientWorldMemory.last();

        if (world != null) {
            try {
                List<BackupVersion> index = BackupStore.loadIndex(BackupStore.root(world)).versions();
                points = TimePoints.list(index, MAX_POINTS, 300);
                if (index.isEmpty()) status = Component.translatable("undying.wb.empty").getString();
            } catch (Throwable t) {
                ModLog.swallowed("BackupScreen.init", t);
                status = Component.translatable("undying.wb.unreadable").getString();
            }
        } else {
            status = Component.translatable("undying.wb.need_world").getString();
        }

        int cx = this.width / 2;
        int y = 62;

        for (long moment : points) {
            long ago = System.currentTimeMillis() / 1000L - moment;
            String label = HumanStamp.shortLine(moment, BackupScheduler.zone())
                    + "  ·  " + HumanStamp.ago(ago);
            Button b = Button.builder(Component.literal(label),
                            btn -> this.minecraft.setScreen(new RestoreConfirmScreen(this, moment)))
                    .bounds(cx - 150, y, 300, 20).build();
            b.active = !this.inWorld;   // see the class comment: no writes while a world is open
            this.addRenderableWidget(b);
            y += 24;
        }

        int bottom = this.height - 44;
        this.addRenderableWidget(Button.builder(Component.translatable("undying.wb.copy_now"),
                        b -> copyNow())
                .bounds(cx - 152, bottom, 148, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.back"),
                        b -> this.minecraft.setScreen(parent))
                .bounds(cx + 4, bottom, 148, 20).build());
    }

    private void copyNow() {
        Path world = ClientWorldMemory.last();
        if (world == null) return;
        var result = BackupScheduler.runNow(world);
        status = result.summaryZh();
        this.rebuildWidgets();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g);
        int cx = this.width / 2;

        g.drawCenteredString(this.font, this.title, cx, 22, 0xFFFFFF);
        g.drawCenteredString(this.font, Component.translatable("undying.wb.hint"), cx, 36, 0x909090);
        if (this.inWorld) {
            g.drawCenteredString(this.font, Component.translatable("undying.wb.in_world"),
                    cx, 50, 0xE0B050);
        }

        if (!status.isEmpty()) {
            g.drawCenteredString(this.font, Component.literal(status), cx, this.height - 62, 0xE0B050);
        }
        if (ModConfig.WORLD_BACKUP.get()) {
            String summary = BackupScheduler.lastSummary();
            if (!summary.isEmpty()) {
                g.drawCenteredString(this.font, Component.literal(summary), cx, this.height - 74, 0x7FD87F);
            }
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) this.minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }
}

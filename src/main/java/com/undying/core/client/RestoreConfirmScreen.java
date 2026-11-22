package com.undying.core.client;

import com.undying.core.ModLog;
import com.undying.core.backup.BackupScheduler;
import com.undying.core.backup.HumanStamp;
import com.undying.core.backup.WorldRestore;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

/**
 * "Roll back to this moment" — shown only after a rehearsal has said exactly what would happen.
 *
 * <h3>Why the rehearsal happens before this screen, not after the click</h3>
 * The numbers on this screen come from {@link WorldRestore#restore} with {@code dryRun = true}:
 * the same function, the same plan, no writes. If the confirmation screen kept its own estimate,
 * the two could disagree — and the player would have approved one thing and been given another.
 *
 * <h3>Why it does not run in the background</h3>
 * A restore touches a handful of files (only the ones with an old enough copy), usually well under
 * a second. Showing the outcome here, synchronously, means "done" is真实 rather than "started".
 * The expensive part — scanning 250 MB — is not on this path at all.
 */
public class RestoreConfirmScreen extends Screen {

    private final Screen parent;
    private final long moment;
    private WorldRestore.Outcome rehearsal;
    private String error = "";

    /** Non-null while a restore is running; the screen stops accepting input until it lands. */
    private CompletableFuture<WorldRestore.Outcome> running;

    public RestoreConfirmScreen(Screen parent, long moment) {
        super(Component.translatable("undying.wb.confirm_title"));
        this.parent = parent;
        this.moment = moment;
    }

    @Override
    protected void init() {
        Path world = ClientWorldMemory.last();
        if (world != null) {
            rehearsal = WorldRestore.restore(world, moment, true);
        } else {
            error = Component.translatable("undying.wb.need_world").getString();
        }

        int cx = this.width / 2;
        int bottom = this.height / 2 + 52;

        boolean busy = running != null;
        Button confirm = Button.builder(
                        Component.translatable(busy ? "undying.wb.restoring" : "undying.wb.do_restore"),
                        b -> doRestore())
                .bounds(cx - 152, bottom, 148, 20).build();
        confirm.active = !busy && rehearsal != null && rehearsal.planned() > 0;
        this.addRenderableWidget(confirm);

        Button cancel = Button.builder(Component.translatable("gui.cancel"),
                        b -> this.minecraft.setScreen(parent))
                .bounds(cx + 4, bottom, 148, 20).build();
        cancel.active = !busy;   // leaving mid-restore would hide a half-finished write
        this.addRenderableWidget(cancel);
    }

    /**
     * Run the restore off the render thread.
     *
     * <h3>Why this is not synchronous any more</h3>
     * A first restore on a large world copies a good number of region files; doing that on the
     * render thread froze the game for as long as the disk needed, with no cursor movement and no
     * way to tell "working" from "hung". {@code WorldRestore} touches nothing but paths, so it is
     * safe on a worker; the screen polls {@code isDone} in {@link #tick()}.
     */
    private void doRestore() {
        Path world = ClientWorldMemory.last();
        if (world == null || running != null) return;

        running = CompletableFuture.supplyAsync(() -> {
            try {
                return WorldRestore.restore(world, moment, false);
            } catch (Throwable t) {
                ModLog.swallowed("RestoreConfirmScreen.doRestore", t);
                return null;
            }
        });
        this.rebuildWidgets();
    }

    @Override
    public void tick() {
        if (running == null || !running.isDone()) return;

        WorldRestore.Outcome outcome = null;
        try {
            outcome = running.join();
        } catch (Throwable t) {
            ModLog.swallowed("RestoreConfirmScreen.tick", t);
        }
        running = null;

        if (outcome == null) {
            error = Component.translatable("undying.wb.failed").getString();
            this.rebuildWidgets();
            return;
        }

        // Chat is the right place for the result: it survives closing the screen, and the player
        // needs the wording ("replaced 8 files, 3 left as they are") more than a green tick.
        var player = this.minecraft == null ? null : this.minecraft.player;
        if (player != null) {
            player.displayClientMessage(Component.literal("[Undying] " + outcome.summaryZh()), false);
        }
        this.minecraft.setScreen(parent);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g);
        int cx = this.width / 2;
        int y = this.height / 2 - 72;

        g.drawCenteredString(this.font, this.title, cx, y, 0xFFFFFF);
        y += 24;

        String when = HumanStamp.shortLine(moment, BackupScheduler.zone())
                + "  ·  " + HumanStamp.ago(System.currentTimeMillis() / 1000L - moment);
        g.drawCenteredString(this.font, Component.literal(when), cx, y, 0xA0C0FF);
        y += 22;

        if (!error.isEmpty()) {
            for (var line : this.font.split(Component.literal(error), this.width - 60)) {
                g.drawCenteredString(this.font, line, cx, y, 0xE08080);
                y += 11;
            }
        } else if (rehearsal != null) {
            for (var line : this.font.split(Component.literal(rehearsal.summaryZh()), this.width - 60)) {
                g.drawCenteredString(this.font, line, cx, y, 0xE0D0A0);
                y += 11;
            }
            y += 8;
            g.drawCenteredString(this.font, Component.translatable("undying.wb.aside_note"),
                    cx, y, 0x909090);
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        // A restore in flight must finish (or fail) before the player walks away; the screen is
        // the only place that will report the outcome.
        if (running != null) return;
        if (this.minecraft != null) this.minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }
}

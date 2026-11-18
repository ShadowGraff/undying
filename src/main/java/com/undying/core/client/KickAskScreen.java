package com.undying.core.client;

import com.undying.core.KickGuard;
import com.undying.core.ModLog;
import com.undying.core.Networking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * "Something wants to take you out of this world."
 *
 * <h3>Why a paused screen again</h3>
 * Same reason as the teleport question: the answer has to be read, and a hostile mod that keeps
 * trying must not be able to act while the player is reading. {@link #isPauseScreen()} stops the
 * integrated server ticking behind the dialog.
 *
 * <h3>Why the reason is wrapped instead of truncated</h3>
 * The reason is the whole point — it is the only part of this dialog that tells the player
 * whether they are being kicked for a reason they agree with ("you cheated") or for no reason at
 * all. Cutting it at 40 characters would throw that away. Long reasons wrap over several lines
 * and the dialog shrinks its own text rather than hiding words.
 *
 * <h3>Exactly one answer, always</h3>
 * The buttons, ESC and the close key all route through {@link #finish(boolean)}, and
 * {@code answered} makes a second call a no-op. The server also checks the id, so a late answer
 * for a question that has since been replaced is ignored instead of performing the wrong action.
 */
public class KickAskScreen extends Screen {

    private final long id;
    private final String kind;
    private final String source;
    private final String reason;
    private boolean answered;

    public KickAskScreen(long id, String kind, String source, String reason) {
        super(Component.translatable(isStop(kind) ? "undying.kick.title_stop" : "undying.kick.title"));
        this.id = id;
        this.kind = kind == null ? "" : kind;
        this.source = source == null ? "" : source;
        this.reason = reason == null ? "" : reason;
    }

    private static boolean isStop(String kind) {
        return KickGuard.KIND_STOP.equals(kind);
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int y = this.height - 60;

        this.addRenderableWidget(Button.builder(
                        Component.translatable(isStop(this.kind)
                                ? "undying.kick.stay_server" : "undying.kick.stay"),
                        b -> finish(false))
                .bounds(cx - 155, y, 150, 20).build());

        this.addRenderableWidget(Button.builder(
                        Component.translatable(isStop(this.kind)
                                ? "undying.kick.accept_server" : "undying.kick.accept"),
                        b -> finish(true))
                .bounds(cx + 5, y, 150, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // 1.20.1 has only the single-argument overload; the four-argument form is 1.20.2+.
        this.renderBackground(g);

        int cx = this.width / 2;
        int y = Math.max(40, this.height / 2 - 78);

        g.drawCenteredString(this.font, this.title, cx, y, 0xFFFFFF);
        y += 24;

        // Who: shown plainly, because "which mod" is the question the player is really asking.
        for (var line : this.font.split(Component.translatable("undying.kick.source", this.source),
                this.width - 60)) {
            g.drawCenteredString(this.font, line, cx, y, 0xA0C0FF);
            y += 11;
        }

        y += 8;
        // Why: wrapped, not truncated.
        for (var line : this.font.split(Component.translatable("undying.kick.reason", this.reason),
                this.width - 60)) {
            g.drawCenteredString(this.font, line, cx, y, 0xE0D0A0);
            y += 11;
        }

        super.render(g, mouseX, mouseY, partialTick);
    }

    /**
     * ESC means "keep me here", not "answer later". Silence must never be read as consent for
     * something that ends the session.
     */
    @Override
    public void onClose() {
        finish(false);
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }

    private void finish(boolean accept) {
        if (this.answered) return;
        this.answered = true;
        try {
            Networking.sendKickAnswer(this.id, accept);
        } catch (Throwable t) {
            ModLog.swallowed("KickAskScreen.finish", t);
        }
        if (this.minecraft != null) this.minecraft.setScreen(null);
    }
}

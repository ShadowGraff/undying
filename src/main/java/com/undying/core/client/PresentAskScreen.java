package com.undying.core.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/**
 * One-time prompt asking whether to enable the mod scan.
 *
 * <p>Why a dialog instead of a config entry: the scan is read-only, changes nothing and
 * touches no other mod's code. Burying something that harmless behind a config file that
 * nobody opens means the feature effectively does not exist. The things that deserve a
 * config entry are the dangerous ones, where turning it on has consequences.
 *
 * <p>{@link #isPauseScreen()} returns true, so in a single-player world the game pauses
 * behind the dialog the same way it does behind ESC. On a server nothing pauses — the
 * server keeps running regardless of what a client draws, and pretending otherwise would
 * just be a lie in the UI.
 *
 * <p>Answering is one-shot by design: the caller records that the question was asked, so
 * this never turns into a nag screen.
 */
public class PresentAskScreen extends Screen {

    private final Consumer<Boolean> onAnswer;
    private boolean answered = false;

    public PresentAskScreen(Consumer<Boolean> onAnswer) {
        super(Component.literal("Undying · 呈现控制 / Presentation control"));
        this.onAnswer = onAnswer;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int y = this.height / 2 + 46;

        this.addRenderableWidget(Button.builder(
                        Component.literal("启用 / Enable"),
                        b -> finish(true))
                .bounds(cx - 155, y, 150, 20).build());

        this.addRenderableWidget(Button.builder(
                        Component.literal("以后再说 / Not now"),
                        b -> finish(false))
                .bounds(cx + 5, y, 150, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // 1.20.1 only has the single-argument overload; the (g, mouseX, mouseY, partialTick)
        // form arrived in 1.20.2. Do not copy the newer signature here.
        this.renderBackground(g);

        int cx = this.width / 2;
        int y = this.height / 2 - 62;

        g.drawCenteredString(this.font, this.title, cx, y, 0xFFFFFF);
        y += 28;
        g.drawCenteredString(this.font,
                Component.literal("可以只读扫描其他模组的 jar，报告哪些模组替换了原版资源"),
                cx, y, 0xD0D0D0);
        y += 14;
        g.drawCenteredString(this.font,
                Component.literal("（音效 / GUI 贴图 / 后处理着色器）。不修改任何文件，也不拦截任何东西。"),
                cx, y, 0xD0D0D0);
        y += 24;
        g.drawCenteredString(this.font,
                Component.literal("是否现在启用？"),
                cx, y, 0xFFFFFF);
        y += 14;
        g.drawCenteredString(this.font,
                Component.literal("之后可在 undying-common.toml 里随时修改"),
                cx, y, 0x909090);

        super.render(g, mouseX, mouseY, partialTick);
    }

    /**
     * ESC and the close key mean "not now", not "ask me again next time" — the caller stores
     * the answer either way, so a dismissed dialog counts as a decision.
     */
    @Override
    public void onClose() {
        finish(false);
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }

    private void finish(boolean enable) {
        if (this.answered) return;
        this.answered = true;
        this.onAnswer.accept(enable);
        if (this.minecraft != null) this.minecraft.setScreen(null);
    }
}

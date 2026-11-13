package com.undying.core.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.NativeImage;
import com.undying.core.ModLog;
import com.undying.core.UndyingMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

import java.util.Base64;

/**
 * F2 + backtick: attach the game screen to the next message.
 *
 * <h3>Why a combination instead of F2 alone</h3>
 * F2 is the vanilla screenshot key and people use it for screenshots. Taking it over would mean
 * every ordinary screenshot also uploaded itself to an API — a privacy failure made of a
 * convenience. F2 plus a second key cannot be pressed by habit.
 *
 * <h3>Why the image is scaled down before it is sent</h3>
 * A 1920×1080 PNG is one to three megabytes; base64 adds a third on top, and the whole thing
 * goes in a JSON string. Providers cap request size, and a request rejected for being large is
 * a confusing failure that looks like a broken mod. 854 pixels wide keeps text in the HUD
 * readable while cutting the payload by roughly a factor of five.
 *
 * <p>The picture is only held in memory. It is attached to the next message exactly once, and
 * nothing is written to disk: a screenshot of someone's world is not something to leave lying
 * around for a feature they may use once.
 */
@Mod.EventBusSubscriber(modid = UndyingMod.MODID, value = Dist.CLIENT)
public final class ScreenshotCapture {

    /** Longest edge after scaling. Wide enough to read chat and coordinates. */
    private static final int MAX_WIDTH = 854;

    /** Base64 PNG waiting to be attached, or null. */
    private static volatile String pending;
    private static volatile long pendingAt;

    private ScreenshotCapture() {}

    @SubscribeEvent
    public static void onKey(InputEvent.Key event) {
        if (event.getAction() != GLFW.GLFW_PRESS) return;
        if (event.getKey() != GLFW.GLFW_KEY_F2) return;

        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.screen != null) return;

            boolean chord = InputConstants.isKeyDown(mc.getWindow().getWindow(), GLFW.GLFW_KEY_GRAVE_ACCENT);
            if (!chord) return;   // plain F2 belongs to vanilla

            capture(mc);
        } catch (Throwable t) {
            ModLog.swallowed("ScreenshotCapture.key", t);
        }
    }

    private static void capture(Minecraft mc) {
        NativeImage image = null;
        NativeImage scaled = null;
        try {
            image = Screenshot.takeScreenshot(mc.getMainRenderTarget());
            if (image == null) return;

            int w = image.getWidth();
            int h = image.getHeight();
            if (w > MAX_WIDTH) {
                int nw = MAX_WIDTH;
                int nh = Math.max(1, h * MAX_WIDTH / w);
                scaled = new NativeImage(nw, nh, false);
                image.resizeSubRectTo(0, 0, w, h, scaled);
                image.close();
                image = null;
            }

            NativeImage source = scaled != null ? scaled : image;
            byte[] png = source.asByteArray();

            pending = Base64.getEncoder().encodeToString(png);
            pendingAt = System.currentTimeMillis();

            say(mc, "§b[助手] §f已截取当前画面，会在下一条消息里一起发送");
        } catch (Throwable t) {
            ModLog.swallowed("ScreenshotCapture.capture", t);
            say(mc, "§c[助手] 截图失败: " + t.getClass().getSimpleName());
        } finally {
            if (image != null) image.close();
            if (scaled != null) scaled.close();
        }
    }

    /**
     * Take the pending image, if any.
     *
     * <p>One-shot by design: an attachment that silently stayed would be sent again with the
     * next message, and again, and the player would be paying for a picture they had forgotten.
     */
    public static String takePending() {
        String p = pending;
        pending = null;
        return p;
    }

    public static boolean hasPending() {
        return pending != null;
    }

    /** Age of the pending image in seconds, for the "still attached?" hint. */
    public static long pendingAgeSeconds() {
        return pending == null ? -1 : (System.currentTimeMillis() - pendingAt) / 1000L;
    }

    private static void say(Minecraft mc, String text) {
        if (mc.player == null) return;
        mc.player.displayClientMessage(net.minecraft.network.chat.Component.literal(text), false);
    }
}

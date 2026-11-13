package com.undying.core.client;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.undying.core.LlmChat;
import com.undying.core.LlmConfig;
import com.undying.core.ModLog;
import com.undying.core.UndyingMod;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * {@code /ask}, the way to talk to the assistant.
 *
 * <h3>Why a command and not a chat prefix</h3>
 * Catching plain chat before it is sent would swallow messages meant for a server — on someone
 * else's server that is a real message that never arrives, and the player would have no idea
 * why. A command is unambiguous: it either goes to the assistant or it is not typed. It is also
 * one keystroke away from ordinary chat, so nothing is lost in convenience.
 *
 * <p>Client-side command: the key never leaves this machine, and the request is made here, not
 * by the server.
 */
@Mod.EventBusSubscriber(modid = UndyingMod.MODID, value = Dist.CLIENT)
public final class LlmCommands {

    private static final String PREFIX = "§b[助手] §f";

    private LlmCommands() {}

    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        try {
            event.getDispatcher().register(Commands.literal("ask")
                    .then(Commands.argument("message", StringArgumentType.greedyString())
                            .executes(ctx -> {
                                String text = StringArgumentType.getString(ctx, "message");
                                send(text);
                                return 1;
                            }))
                    .then(Commands.literal("reset").executes(ctx -> {
                        LlmChat.clear();
                        say("会话已清空");
                        return 1;
                    }))
                    .then(Commands.literal("compact").executes(ctx -> {
                        compact();
                        return 1;
                    }))
                    .then(Commands.literal("status").executes(ctx -> {
                        status();
                        return 1;
                    })));
        } catch (Throwable t) {
            ModLog.swallowed("LlmCommands.register", t);
        }
    }

    private static void send(String text) {
        if (text == null || text.isBlank()) return;

        // Said once per session, before anything is sent. Not a dialog and not a confirmation:
        // the switch is the consent, and nagging someone who already turned it on is how a
        // warning becomes noise. But the first message of a session should not leave without
        // the player having been told what it carries.
        if (!disclaimerShown) {
            disclaimerShown = true;
            say("§e实验性功能：本功能会把你的消息、坐标、附近实体名称、模组列表与你附上的截图"
                    + "发送到第三方接口，安全性尚未完全验证。因使用本功能造成的任何损失，作者不承担责任。");
            say("§eExperimental: your message, position, nearby entity names, mod list and any "
                    + "attached screenshot go to a third-party endpoint. Not fully security-reviewed. "
                    + "Use at your own risk; the author accepts no liability for any loss.");
        }

        String warn = LlmClient.usageWarning();
        if (warn != null) say(warn);

        // One-shot: taking it here means a forgotten screenshot cannot ride along with a later
        // message the player has long stopped thinking about.
        String image = ScreenshotCapture.takePending();
        say(image != null ? "（思考中…含截图）" : "（思考中…）");

        // Through say(): the reply arrives seconds later, and the player may have left the
        // world by then. Dereferencing mc.player there is a null pointer the callback wrapper
        // would swallow, so the player would simply never see the answer to a question they
        // already paid for.
        LlmClient.ask(text, image, LlmCommands::say, error -> say("§c" + error));
    }

    /**
     * Ask for a summary, then replace the history with it.
     *
     * <p>Compressing is the only way to keep a long relationship without sending a long
     * conversation; truncating alone loses what was said, and this keeps the shape of it.
     */
    private static void compact() {
        if (LlmChat.size() == 0) {
            say("没有可压缩的对话");
            return;
        }
        say("（正在压缩…）");
        LlmClient.ask("请把我们到目前为止的对话压缩成一段不超过 300 字的摘要，"
                        + "保留关键事实、约定和还没做完的事。只输出摘要本身。",
                summary -> {
                    LlmChat.clear();
                    LlmChat.add("assistant", "（此前对话摘要）" + summary);
                    say("已压缩成摘要，新会话从这里开始");
                },
                error -> say("§c" + error));
    }

    private static void status() {
        boolean on = LlmClient.enabled();
        int tokens = LlmChat.estimatedTokens();
        double usage = LlmChat.usage();
        say("开关: " + (on ? "开" : "关")
                + " · 消息 " + LlmChat.size() + " 条"
                + " · 约 " + tokens + " tokens（估算）"
                + " · 占上限 " + Math.round(usage * 100) + "%");
        if (on && LlmConfig.API_KEY.get().isBlank()) {
            say("§e尚未填写 apiKey（undying-client.toml → [undying.assistant]）");
        }
    }

    private static void say(String text) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        mc.player.displayClientMessage(Component.literal(PREFIX + text), false);
    }

    /**
     * Kept for the one thing the command cannot show: a nudge before the request is cut off.
     *
     * <p>Polled on the client tick because the warning has to appear while the player is typing,
     * not after they have already sent a message that will be rejected.
     */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!LlmClient.enabled()) return;
        if (Minecraft.getInstance().player == null) return;
        if (Minecraft.getInstance().player.tickCount % 200 != 0) return;   // every 10 s

        if (!warned && LlmChat.usage() >= 0.9) {
            warned = true;
            say("对话已接近上限（估算），可以用 /ask compact 压缩或 /ask reset 重开");
        } else if (warned && LlmChat.usage() < 0.8) {
            warned = false;
        }
    }

    private static boolean warned;
    private static boolean disclaimerShown;
}

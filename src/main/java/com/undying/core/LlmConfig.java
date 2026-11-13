package com.undying.core;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Client-side settings for the assistant.
 *
 * <h3>Why this is a CLIENT config and not part of {@link ModConfig}</h3>
 * The API key is a secret. A COMMON config is loaded on both sides and stored next to the world;
 * putting a key there would mean it travels with a save file and exists on any server the player
 * joins. A CLIENT config never leaves this machine and is never read by a server.
 *
 * <h3>Why it defaults to off</h3>
 * This is the one feature that sends anything off the machine — the player's words, their
 * position, the names of their mobs. It is also the one feature that costs money per message.
 * Neither is something to switch on for someone without asking.
 */
public final class LlmConfig {

    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.BooleanValue ENABLED;
    public static final ForgeConfigSpec.ConfigValue<String> API_URL;
    public static final ForgeConfigSpec.ConfigValue<String> API_KEY;
    public static final ForgeConfigSpec.ConfigValue<String> MODEL;
    public static final ForgeConfigSpec.ConfigValue<String> REASONING_EFFORT;
    public static final ForgeConfigSpec.IntValue MAX_TOKENS;
    public static final ForgeConfigSpec.DoubleValue TEMPERATURE;
    public static final ForgeConfigSpec.ConfigValue<String> SYSTEM_PROMPT;
    public static final ForgeConfigSpec.IntValue HISTORY_LIMIT;
    public static final ForgeConfigSpec.IntValue CONTEXT_TOKENS;
    public static final ForgeConfigSpec.BooleanValue ALLOW_TOOLS;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("undying");
        b.push("assistant");

        b.comment("=== Assistant (OpenAI-compatible chat) ===",
                  "=== 助手（OpenAI 兼容接口）===");

        ENABLED = b.comment(
                "Off by default, and never asks. Turning this on sends your messages, your",
                "coordinates and the names of nearby entities to the endpoint below.",
                "默认关闭，也不会主动询问。开启后，你的消息、坐标和附近实体的名字会发送到下面的接口。")
                .define("enabled", false);

        API_URL = b.comment(
                "Full chat-completions URL. DeepSeek: https://api.deepseek.com/v1/chat/completions",
                "Moonshot: https://api.moonshot.cn/v1/chat/completions",
                "Anything that speaks the OpenAI /chat/completions format works.",
                "完整的 chat-completions 地址。任何兼容 OpenAI /chat/completions 格式的接口都可用。")
                .define("apiUrl", "https://api.deepseek.com/v1/chat/completions");

        API_KEY = b.comment(
                "Bearer token. Stored in plain text in this file, on this machine only.",
                "This file is not synced anywhere by the mod.",
                "Bearer 令牌。以明文存在本机这个文件里，模组不会把它同步到任何地方。")
                .define("apiKey", "");

        MODEL = b.comment("Model name, e.g. deepseek-chat / moonshot-v1-8k",
                        "模型名，例如 deepseek-chat")
                .define("model", "deepseek-chat");

        REASONING_EFFORT = b.comment(
                "Optional. Sent as reasoning_effort when non-empty (low / medium / high).",
                "Providers differ: most ignore it, some reject unknown values. There is no API to",
                "ask a provider which levels it supports, so this is typed, not discovered.",
                "Optional. 留空则不发送。各家支持程度不同，也没有接口能查询支持哪些档位，只能手填。")
                .define("reasoningEffort", "");

        MAX_TOKENS = b.comment("Reply length cap", "回复长度上限")
                .defineInRange("maxTokens", 800, 64, 8192);

        TEMPERATURE = b.comment("0 = steady, 1 = varied. A companion reads better around 0.7-0.9",
                        "0 更稳定，1 更多变。陪伴用途 0.7–0.9 比较合适")
                .defineInRange("temperature", 0.8, 0.0, 2.0);

        SYSTEM_PROMPT = b.comment(
                "Empty uses the built-in prompt: a companion that speaks Chinese, keeps replies",
                "short, and says so when it does not know something.",
                "留空则使用内置提示词：中文、简短、不知道就直说。")
                .define("systemPrompt", "");

        HISTORY_LIMIT = b.comment(
                "How many past messages are sent with each request. 0 = no memory.",
                "每次请求携带多少条历史消息。0 = 不保留记忆。")
                .defineInRange("historyLimit", 20, 0, 200);

        CONTEXT_TOKENS = b.comment(
                "Budget used to warn when the conversation is getting long. The estimate is",
                "characters divided by three — deliberately crude, and labelled as an estimate",
                "wherever it is shown.",
                "用于提醒对话过长的预算。估算方式是字符数除以三——故意粗略，界面上也标明是估算。")
                .defineInRange("contextTokens", 32000, 2000, 200000);

        ALLOW_TOOLS = b.comment(
                "Let the model ask for facts about your world (position, nearby mobs, inventory",
                "summary, loaded mods). Read-only: no tool can change anything or run a command.",
                "允许模型查询你的世界（坐标、附近生物、背包摘要、已加载模组）。",
                "全部只读：任何工具都不能修改东西，也不能执行指令。")
                .define("allowTools", true);

        b.pop();
        b.pop();
        SPEC = b.build();
    }

    private LlmConfig() {}

    /** The built-in prompt, used when the config entry is empty. */
    public static String systemPrompt() {
        String custom = safe(SYSTEM_PROMPT);
        if (!custom.isBlank()) return custom;
        return "你是一个 Minecraft 单人世界的陪伴者。玩家装了大量恐怖模组，可能正处在紧张状态。"
                + "说话简短、具体、不夸张，不制造更多恐怖感。用中文回答。"
                + "不知道的事情就说不知道。不要编造游戏内的数据——需要事实时调用工具。"
                + "可以建议玩家输入某条指令，但要明确说明那需要他自己执行。";
    }

    private static String safe(ForgeConfigSpec.ConfigValue<String> v) {
        try {
            String s = v.get();
            return s == null ? "" : s;
        } catch (Throwable t) {
            return "";
        }
    }
}

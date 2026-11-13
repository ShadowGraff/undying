package com.undying.core.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.undying.core.LlmChat;
import com.undying.core.LlmConfig;
import com.undying.core.ModLog;
import net.minecraft.client.Minecraft;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * One conversation turn, over the OpenAI chat-completions format.
 *
 * <h3>Why this runs on its own thread and never on the render thread</h3>
 * A request takes seconds. Any wait on the render thread is a frozen game — the same mistake as
 * decoding audio on the main thread, except the wait is measured in seconds rather than
 * milliseconds. The turn is dispatched to the common pool and the answer is handed back through
 * {@code Minecraft.execute}.
 *
 * <h3>Why one request at a time</h3>
 * Two overlapping turns would interleave their tool calls and produce a history where a reply
 * answers a question that is no longer the last one. {@link #BUSY} makes the second attempt say
 * "still working" instead of silently queueing.
 */
public final class LlmClient {

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private static final AtomicBoolean BUSY = new AtomicBoolean(false);

    /** How many tool round-trips one question may cause before we stop. */
    private static final int MAX_TOOL_ROUNDS = 4;

    private LlmClient() {}

    /**
     * Ask the model something. Callbacks run on the client thread.
     *
     * @param text    the player's message
     * @param onReply the reply, ready to show
     * @param onError a message to show when something went wrong
     */
    public static void ask(String text, Consumer<String> onReply, Consumer<String> onError) {
        if (text == null || text.isBlank()) return;

        if (!enabled()) {
            onError.accept("助手未开启：在 undying-client.toml 的 [undying.assistant] 里把 enabled 设为 true，并填 apiKey");
            return;
        }
        String key = value(LlmConfig.API_KEY);
        if (key.isBlank()) {
            onError.accept("未配置 apiKey（undying-client.toml → [undying.assistant]）");
            return;
        }
        if (!BUSY.compareAndSet(false, true)) {
            onError.accept("还在等上一条回复");
            return;
        }

        JsonObject body;
        try {
            body = buildRequest(text);
        } catch (Throwable t) {
            BUSY.set(false);
            onError.accept("请求构造失败: " + t);
            return;
        }

        CompletableFuture.runAsync(() -> {
            try {
                String reply = roundTrip(body, key);
                LlmChat.add("user", text);
                LlmChat.add("assistant", reply);
                onClientThread(() -> onReply.accept(reply));
            } catch (Throwable t) {
                String msg = describe(t);
                onClientThread(() -> onError.accept(msg));
            } finally {
                BUSY.set(false);
            }
        });
    }

    public static boolean enabled() {
        try {
            return LlmConfig.ENABLED.get();
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean busy() {
        return BUSY.get();
    }

    /** A warning to show before asking, or null when the conversation is still short. */
    public static String usageWarning() {
        double u = LlmChat.usage();
        if (u < 0.9) return null;
        int pct = (int) Math.round(u * 100);
        return "对话已用约 " + pct + "% 的估算上限。可以用 /ask reset 开新会话，或 /ask compact 让我先小结一下。";
    }

    // ═══════════ Request ═══════════

    private static JsonObject buildRequest(String text) {
        JsonObject body = new JsonObject();
        body.addProperty("model", value(LlmConfig.MODEL));
        body.addProperty("max_tokens", intValue(LlmConfig.MAX_TOKENS, 800));
        body.addProperty("temperature", doubleValue(LlmConfig.TEMPERATURE, 0.8));

        String effort = value(LlmConfig.REASONING_EFFORT);
        if (!effort.isBlank()) body.addProperty("reasoning_effort", effort);

        JsonArray messages = new JsonArray();
        messages.add(message("system", LlmConfig.systemPrompt()));

        int limit = intValue(LlmConfig.HISTORY_LIMIT, 20);
        for (LlmChat.Msg m : LlmChat.recent(limit)) {
            messages.add(message(m.role, m.content));
        }
        messages.add(message("user", text));

        body.add("messages", messages);

        if (boolValue(LlmConfig.ALLOW_TOOLS, true)) {
            body.add("tools", LlmTools.schemas());
        }
        return body;
    }

    private static JsonObject message(String role, String content) {
        JsonObject m = new JsonObject();
        m.addProperty("role", role);
        m.addProperty("content", content == null ? "" : content);
        return m;
    }

    /** Sends, and follows tool calls until the model answers in words. */
    private static String roundTrip(JsonObject body, String key) throws Exception {
        JsonArray messages = body.getAsJsonArray("messages");

        for (int round = 0; round <= MAX_TOOL_ROUNDS; round++) {
            JsonObject response = post(body, key);

            JsonArray choices = response.getAsJsonArray("choices");
            if (choices == null || choices.isEmpty()) {
                throw new IllegalStateException("响应里没有 choices");
            }
            JsonObject message = choices.get(0).getAsJsonObject().getAsJsonObject("message");
            if (message == null) throw new IllegalStateException("响应里没有 message");

            JsonArray toolCalls = message.getAsJsonArray("tool_calls");
            if (toolCalls == null || toolCalls.isEmpty()) {
                JsonElement content = message.get("content");
                return content == null || content.isJsonNull() ? "（模型没有返回内容）" : content.getAsString();
            }

            if (round == MAX_TOOL_ROUNDS) {
                return "（模型连续请求工具超过 " + MAX_TOOL_ROUNDS + " 轮，已停止）";
            }

            // The assistant's tool-call turn has to go back verbatim, including the ids, or the
            // provider rejects the following tool results as unattached.
            messages.add(message);

            for (JsonElement el : toolCalls) {
                JsonObject call = el.getAsJsonObject();
                String id = call.has("id") ? call.get("id").getAsString() : "";
                JsonObject fn = call.getAsJsonObject("function");
                String name = fn != null && fn.has("name") ? fn.get("name").getAsString() : "";
                JsonObject args = parseArguments(fn);

                String result = LlmTools.run(name, args);

                JsonObject toolMsg = new JsonObject();
                toolMsg.addProperty("role", "tool");
                toolMsg.addProperty("tool_call_id", id);
                toolMsg.addProperty("content", result);
                messages.add(toolMsg);
            }
        }
        return "（没有拿到回复）";
    }

    private static JsonObject parseArguments(JsonObject fn) {
        if (fn == null || !fn.has("arguments")) return new JsonObject();
        JsonElement args = fn.get("arguments");
        try {
            // Some providers send the arguments as an object, most as a JSON string.
            if (args.isJsonObject()) return args.getAsJsonObject();
            String s = args.getAsString();
            if (s == null || s.isBlank()) return new JsonObject();
            return JsonParser.parseString(s).getAsJsonObject();
        } catch (Throwable t) {
            return new JsonObject();
        }
    }

    private static JsonObject post(JsonObject body, String key) throws Exception {
        String url = value(LlmConfig.API_URL);
        if (url.isBlank()) throw new IllegalStateException("未配置 apiUrl");

        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + key)
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        if (response.statusCode() / 100 != 2) {
            throw new IllegalStateException("HTTP " + response.statusCode() + " " + truncate(response.body()));
        }
        JsonElement parsed = JsonParser.parseString(response.body());
        if (!parsed.isJsonObject()) throw new IllegalStateException("响应不是 JSON 对象");
        return parsed.getAsJsonObject();
    }

    // ═══════════ Plumbing ═══════════

    private static void onClientThread(Runnable r) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            try {
                r.run();
            } catch (Throwable t) {
                ModLog.swallowed("LlmClient.callback", t);
            }
        });
    }

    private static String describe(Throwable t) {
        String m = t.getMessage();
        if (m == null || m.isBlank()) m = t.getClass().getSimpleName();
        if (t instanceof java.net.http.HttpTimeoutException) return "请求超时";
        if (t instanceof java.net.ConnectException) return "连不上接口（检查 apiUrl 和网络）";
        return "请求失败: " + truncate(m);
    }

    private static String truncate(String s) {
        if (s == null) return "";
        return s.length() <= 200 ? s : s.substring(0, 200) + "…";
    }

    private static String value(net.minecraftforge.common.ForgeConfigSpec.ConfigValue<String> v) {
        try {
            String s = v.get();
            return s == null ? "" : s;
        } catch (Throwable t) {
            return "";
        }
    }

    private static int intValue(net.minecraftforge.common.ForgeConfigSpec.IntValue v, int fallback) {
        try {
            return v.get();
        } catch (Throwable t) {
            return fallback;
        }
    }

    private static double doubleValue(net.minecraftforge.common.ForgeConfigSpec.DoubleValue v, double fallback) {
        try {
            return v.get();
        } catch (Throwable t) {
            return fallback;
        }
    }

    private static boolean boolValue(net.minecraftforge.common.ForgeConfigSpec.BooleanValue v, boolean fallback) {
        try {
            return v.get();
        } catch (Throwable t) {
            return fallback;
        }
    }
}

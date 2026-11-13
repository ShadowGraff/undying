package com.undying.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The conversation: what has been said, how much room is left, and where it is kept.
 *
 * <h3>Why the estimate is a division and says so</h3>
 * There is no tokeniser on this side of the API. Counting characters and dividing is wrong by a
 * factor that depends on language and content — Chinese is roughly one token per character,
 * English about one per four. A number presented as exact would be trusted and then be wrong at
 * the worst moment, so the ratio is deliberately crude and every place it is shown calls it an
 * estimate.
 *
 * <h3>Why history is trimmed from the front</h3>
 * The system prompt and the newest exchange are what make a reply coherent. Dropping the oldest
 * turns a long conversation into a slightly forgetful one, which is the correct failure: the
 * alternative is a request the provider rejects for being too long.
 */
public final class LlmChat {

    /** One turn. Roles are the three the API defines: system, user, assistant. */
    public static final class Msg {
        public String role;
        public String content;

        public Msg() {}

        public Msg(String role, String content) {
            this.role = role;
            this.content = content;
        }
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final List<Msg> HISTORY = new ArrayList<>();

    private LlmChat() {}

    public static synchronized void add(String role, String content) {
        if (content == null || content.isBlank()) return;
        HISTORY.add(new Msg(role, content));
        trim();
        saveAsync();
    }

    /** The last {@code limit} turns, oldest first. */
    public static synchronized List<Msg> recent(int limit) {
        if (limit <= 0) return List.of();
        int from = Math.max(0, HISTORY.size() - limit);
        return new ArrayList<>(HISTORY.subList(from, HISTORY.size()));
    }

    public static synchronized int size() {
        return HISTORY.size();
    }

    public static synchronized void clear() {
        HISTORY.clear();
        saveAsync();
    }

    /**
     * Estimated tokens for the whole conversation.
     *
     * <p>{@code chars * 2 / 5} — a little over one token per 2.5 characters. See the class note
     * for why this is not exact, and why an over-estimate is the safe direction: warning a
     * little early is a nuisance, warning late means a rejected request.
     */
    public static synchronized int estimatedTokens() {
        long chars = 0;
        for (Msg m : HISTORY) {
            if (m.content != null) chars += m.content.length();
        }
        return (int) Math.min(Integer.MAX_VALUE, chars * 2 / 5);
    }

    /** 0.0 to 1.0+ of the configured budget. */
    public static double usage() {
        int budget = 32000;
        try {
            budget = LlmConfig.CONTEXT_TOKENS.get();
        } catch (Throwable ignored) {
            // Config not loaded yet (very early call); the default is fine.
        }
        return budget <= 0 ? 0.0 : (double) estimatedTokens() / budget;
    }

    // ═══════════ Persistence ═══════════

    private static Path file() {
        try {
            return net.minecraftforge.fml.loading.FMLPaths.CONFIGDIR.get()
                    .resolve(UndyingMod.MODID).resolve("chat.json");
        } catch (Throwable t) {
            return null;
        }
    }

    /** Written off the client thread: a chat reply should never wait for a disk flush. */
    private static void saveAsync() {
        Path f = file();
        if (f == null) return;
        List<Msg> snapshot;
        synchronized (LlmChat.class) {
            snapshot = new ArrayList<>(HISTORY);
        }
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                Files.createDirectories(f.getParent());
                Files.writeString(f, GSON.toJson(snapshot));
            } catch (Throwable t) {
                ModLog.swallowed("LlmChat.save", t);
            }
        });
    }

    /** Called once when the client starts. Missing or corrupt files start an empty history. */
    public static void load() {
        Path f = file();
        if (f == null || !Files.isRegularFile(f)) return;
        try {
            String json = Files.readString(f);
            List<Msg> loaded = GSON.fromJson(json, new TypeToken<List<Msg>>() {}.getType());
            if (loaded == null) return;
            synchronized (LlmChat.class) {
                HISTORY.clear();
                for (Msg m : loaded) {
                    if (m != null && m.role != null && m.content != null) HISTORY.add(m);
                }
                trim();
            }
        } catch (Throwable t) {
            // A corrupt history must not stop the game or the feature from starting.
            ModLog.swallowed("LlmChat.load", t);
        }
    }

    /** Keeps the file from growing without bound even when the context budget is generous. */
    private static void trim() {
        int max = 400;
        try {
            max = Math.max(20, LlmConfig.HISTORY_LIMIT.get() * 4);
        } catch (Throwable ignored) {
            // keep the default
        }
        while (HISTORY.size() > max) HISTORY.remove(0);
    }
}

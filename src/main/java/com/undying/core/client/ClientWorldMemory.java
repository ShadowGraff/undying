package com.undying.core.client;

import net.minecraft.client.Minecraft;

import java.nio.file.Path;

/**
 * Remembers the last world folder, so the rescue page still works after the player quits.
 *
 * <h3>Why this exists</h3>
 * Restoring has to happen with the world closed, but "closed" also means the integrated server is
 * gone — and with it the only handle the client had on the world folder. Without a note of where
 * that folder was, the feature would be reachable only at the exact moment it cannot be used.
 *
 * <p>One string, memory only, never written to disk: after a game restart it is empty, and the
 * page says "enter the world once, then come back" rather than guessing a folder.
 */
public final class ClientWorldMemory {

    private ClientWorldMemory() {}

    private static String lastWorldPath;

    /** Called from screens that already have access to the client. */
    public static void remember(Minecraft mc) {
        try {
            var server = mc == null ? null : mc.getSingleplayerServer();
            if (server == null) return;
            lastWorldPath = server.getWorldPath(
                    net.minecraft.world.level.storage.LevelResource.ROOT).toString();
        } catch (Throwable ignored) {
            // Remembering is a convenience; failing to remember must not break a screen.
        }
    }

    /** @return the last world folder, or null when no world has been opened this session */
    public static Path last() {
        String p = lastWorldPath;
        if (p == null || p.isEmpty()) return null;
        try {
            return Path.of(p);
        } catch (Throwable t) {
            return null;
        }
    }
}

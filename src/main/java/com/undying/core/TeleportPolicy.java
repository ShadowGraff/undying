package com.undying.core;

import java.util.Locale;

/**
 * The teleport guard's judgement, split out from everything that needs a live server.
 *
 * <h3>What counts as "the player asked for this"</h3>
 * A teleport is treated as player-issued when it happens inside a command that a player typed.
 * That is the same shape as the OP check: the question is not "did something call a method",
 * it is "was this the player's own action". Everything else — a command block, a datapack
 * function, the server console, another mod calling {@code teleportTo} directly — is not.
 *
 * <p>This is a heuristic and it is stated as one: a mod that deliberately runs its teleport
 * inside a player-issued {@code /execute} would be misread as the player's own action. The
 * alternative (stack inspection to find the caller's class) is both slower and easier to fake,
 * and it produces false positives on perfectly ordinary command paths.
 *
 * <h3>What it deliberately does not cover</h3>
 * Only the two {@code ServerPlayer.teleportTo(ServerLevel, ...)} entry points are hooked. The
 * three-argument {@code teleportTo(x, y, z)} is used by ender pearls, chorus fruit and ordinary
 * movement, where an interception would be felt as the game breaking rather than as protection.
 * {@code setPos} and {@code changeDimension} are likewise out of scope. That boundary is
 * written down here instead of being discovered by a player who fell into the void.
 */
public final class TeleportPolicy {

    private TeleportPolicy() {}

    /**
     * Whether this command text is a teleport command.
     *
     * <p>Segment scan, same reasoning as the {@code /kill} check: "the string contains tp"
     * would match an item named "tp" in {@code /give}. A segment counts when it is the command
     * head, or when it directly follows {@code run}.
     */
    public static boolean isTeleportCommand(String raw) {
        if (raw == null) return false;
        String t = raw.trim();
        if (t.startsWith("/")) t = t.substring(1);
        if (t.isEmpty()) return false;

        String[] parts = t.split("\\s+");
        for (int i = 0; i < parts.length; i++) {
            String seg = parts[i].toLowerCase(Locale.ROOT);
            boolean isTp = seg.equals("tp") || seg.equals("teleport")
                    || seg.equals("minecraft:tp") || seg.equals("minecraft:teleport");
            if (!isTp) continue;

            if (i == 0) return true;                                  // bare /tp
            if (parts[i - 1].equalsIgnoreCase("run")) return true;     // /execute ... run tp
        }
        return false;
    }

    /**
     * The whole decision, as a pure function of its inputs.
     *
     * @param enabled      the feature switch
     * @param allowed      the single-player environment gate
     * @param playerIssued the teleport is happening inside a command a player typed
     * @param bypassing    we are performing a teleport the player already approved
     * @return true when the teleport must be cancelled and the player asked
     */
    public static boolean shouldIntercept(boolean enabled, boolean allowed,
                                          boolean playerIssued, boolean bypassing) {
        if (!enabled || !allowed) return false;
        if (bypassing) return false;
        return !playerIssued;
    }
}

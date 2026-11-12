package com.undying.core;

import java.util.Locale;

/**
 * The teleport guard's judgement, split out from everything that needs a live server.
 *
 * <h3>Every teleport is asked about — there is no "the player asked for this" exception</h3>
 * An earlier version tried to let player-typed {@code /tp} through, using the same idea as the
 * OP check: a command entry point records who issued the command, and the teleport compares
 * against it. It was measured to be wrong in the first live session — a {@code /tp} typed into
 * chat was intercepted and labelled as coming from a command rather than from the player. The
 * marker was unreliable in a way that could not be reproduced by reading the code.
 *
 * <p>Rather than keep a heuristic that quietly lies, the guard now asks about <b>all</b>
 * teleports. The cost was judged acceptable and is worth writing down: the prompt freezes the
 * game, shows the destination, and the command text is printed with it, so a player who just
 * typed {@code /tp} reads their own command back and presses one button. Nothing is timed, so
 * a redundant question cannot make anyone late.
 *
 * <p>What this buys is a rule with no exceptions to get wrong: "a teleport moved you only
 * because you said yes". No judgement of intent, nothing to fake, nothing to leak.
 *
 * <h3>What it deliberately does not cover</h3>
 * Only the two {@code ServerPlayer.teleportTo(ServerLevel, ...)} entry points are hooked. The
 * three-argument {@code teleportTo(x, y, z)} is used by ender pearls, chorus fruit and ordinary
 * movement, where an interception would be felt as the game breaking rather than as protection.
 * {@code setPos} and {@code changeDimension} are likewise out of scope — which is also why
 * respawn and portal travel never prompt: {@code PlayerList.respawn} builds a new player, and
 * {@code changeDimension} removes and re-adds the entity, neither of which calls the hooked
 * methods. That boundary is written down here instead of being discovered in play.
 */
public final class TeleportPolicy {

    private TeleportPolicy() {}

    /**
     * Whether this command text is a teleport command.
     *
     * <p><b>Not used for the decision any more</b> — every teleport is asked about. It is still
     * here because the command text is shown to the player and written to the log, and a
     * reader benefits from knowing whether the command was a teleport at all. Kept tested so it
     * stays correct for that purpose.
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
     * @param enabled   the feature switch
     * @param allowed   the single-player environment gate
     * @param bypassing we are performing a teleport the player already approved
     * @return true when the teleport must be cancelled and the player asked
     */
    public static boolean shouldIntercept(boolean enabled, boolean allowed, boolean bypassing) {
        if (!enabled || !allowed) return false;
        return !bypassing;
    }
}

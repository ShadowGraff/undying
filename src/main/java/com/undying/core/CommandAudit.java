package com.undying.core;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Shows the player every command that runs, translated into one sentence.
 *
 * <h3>Why this is worth a network packet per command</h3>
 * Commands are how a hostile mod does its work without touching a single method the player can
 * see: set the time to midnight, give itself items, teleport the player into the void, kill
 * everything within thirty blocks. None of that is visible in chat, and none of it is caught by
 * a guard that watches for damage.
 *
 * <p>The audit does not block anything. It answers the question a player asks right after
 * something strange happens to their world: <i>what just ran?</i>
 *
 * <h3>What it deliberately does not do</h3>
 * No filtering by source, no "suspicious" label. A command block that opens a door and a command
 * block that kills the player look identical from here, and pretending otherwise would be the
 * same mistake as the threat scanner's keyword matching — a guess dressed as a finding.
 */
public final class CommandAudit {

    /** Long commands are cut for display; the full text stays in the log. */
    private static final int MAX_DISPLAY = 90;

    private CommandAudit() {}

    /**
     * Record one executed command and show it to everyone in the world.
     *
     * <p>Sent to all players rather than only the source: a command block has no player attached
     * and is exactly the case worth seeing. In single player those are the same person anyway.
     */
    public static void record(MinecraftServer server, String command, boolean fromPlayer) {
        try {
            if (server == null || command == null || command.isBlank()) return;
            if (!ModConfig.COMMAND_AUDIT.get()) return;
            if (!Environment.isAllowed(server)) return;   // single-player only

            String trimmed = command.trim();
            String summary = CommandTranslator.describe(trimmed);

            String display = summary != null
                    ? summary + "   [" + cut(trimmed) + "]"
                    : cut(trimmed);

            String payload = display.length() > 160 ? display.substring(0, 160) : display;

            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                Networking.sendCommandAudit(p, payload);
            }

            // Only commands nobody typed are written to the report. A player's own commands
            // are already in chat, and writing them would fill the file with the one kind of
            // entry that never explains anything.
            if (!fromPlayer) {
                ReportLog.writeBlock("[command] " + trimmed);
            }
        } catch (Throwable t) {
            ModLog.swallowed("CommandAudit.record", t);
        }
    }

    private static String cut(String s) {
        return s.length() <= MAX_DISPLAY ? s : s.substring(0, MAX_DISPLAY) + "…";
    }
}

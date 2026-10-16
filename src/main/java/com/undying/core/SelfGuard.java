package com.undying.core;

import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * Self-protection reporting and the first-time explanation.
 *
 * <p>Scope: <b>guarding against "quietly breaking on our own", not against "someone coming
 * to strip us out".</b> We have been burned by silent failure three times (launch-time
 * protection never loaded / the XP whitelist always true / the {@code /kill} hook attached
 * but unable to block), and that is the real pain point.
 *
 * <p>Division of labor:
 * <ul>
 *   <li>{@link SelfCheck} — how to check (the probes)</li>
 *   <li>{@link SelfDeclaration} — what to tell the player (the text)</li>
 *   <li>this class — when to say it and where to send it</li>
 * </ul>
 *
 * <p><b>What this deliberately does not do</b>: it does not "repair" anything automatically
 * (breaking vanilla structures would crash the game outright), does not modify any state,
 * and never silently enables anything.
 */
public final class SelfGuard {

    private SelfGuard() {}

    /**
     * Reports the self-check result to the player.
     *
     * @param p    player (may be null; then only the log is written)
     * @param full whether to output the complete list (true on first entry into the world;
     *             the periodic self-check only outputs failures)
     */
    public static void report(ServerPlayer p, boolean full) {
        List<SelfCheck.Probe> probes = SelfCheck.run();
        List<SelfCheck.Probe> failed = new ArrayList<>();
        for (SelfCheck.Probe pr : probes) {
            if (!pr.ok) failed.add(pr);
        }

        // Log: the complete list is written only when full, to avoid spamming
        if (full) {
            StringBuilder sb = new StringBuilder("[self-check] results:");
            for (SelfCheck.Probe pr : probes) sb.append("\n    ").append(pr);
            IntegrityGuard.LOGGER.info(sb.toString());
        }

        if (!failed.isEmpty()) {
            // There are failures — report them whether full or not.
            // This is the core value of self-protection: *no silent failure allowed*.
            StringBuilder sb = new StringBuilder();
            sb.append("[self-check] WARN: ").append(failed.size()).append(" protection check(s) may be inactive:");
            for (SelfCheck.Probe pr : failed) sb.append("\n    ").append(pr);
            IntegrityGuard.LOGGER.warn(sb.toString());

            if (p != null) {
                for (SelfCheck.Probe pr : failed) {
                    Msg.warn(p, "self-check").text("protection inactive: ").dim(pr.name)
                            .text(pr.detail == null || pr.detail.isEmpty() ? "" : "（" + pr.detail + ")")
                            .send();
                }
            }
        } else if (p != null && full) {
            Msg.ok(p, "self-check").text("all ").num(probes.size()).text(" protection checks passed").send();
        }

        // Swallowed exceptions must surface too — the last piece of "no silent failure".
        // It appears only when something was actually swallowed; otherwise it is completely quiet.
        int swallowed = ModLog.totalSwallowed();
        if (swallowed > 0) {
            IntegrityGuard.LOGGER.warn("[self-check] swallowed-exception record: {}", ModLog.summary());
            if (p != null) {
                Msg.warn(p, "self-check").text("swallowed ").num(swallowed)
                        .text(" internal exception(s)").dim(" (see debug.log)").send();
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    //   First world entry explanation (chat box, shown only once)
    // ═══════════════════════════════════════════════════════════

    /**
     * Whether the explanation has already been given in this server session.
     *
     * <p>A static variable adds a second layer: the config's explained flag is a persistent
     * record across sessions, while this guarantees <b>the same game process does not spam
     * the chat box</b> when the player enters the world over and over.
     */
    private static boolean explainedThisSession = false;

    /** Whether that explanation is needed */
    public static boolean needsFirstTimeExplanation() {
        if (explainedThisSession) return false;
        try {
            if (ModConfig.SELF_PROTECTION_ASKED.get()) {
                explainedThisSession = true;
                return false;
            }
        } catch (Throwable t) {
            // Reading the config failed → treat it as "not explained yet". Better to say it
            // once too often in chat than not at all.
            ModLog.swallowed("SelfGuard.needsFirstTimeExplanation", t);
        }
        return true;
    }

    /**
     * Gives the full self-check explanation in the chat box once, and persists the
     * explained flag.
     *
     * <p>Why the chat box instead of a popup screen (the user's decision):
     * <ul>
     *   <li>A popup needs an extra client Screen and network channel; it costs more and is
     *       prone to compatibility problems</li>
     *   <li>The chat box is actually easier to notice (it is right in view and leaves a record)</li>
     *   <li>It works naturally on servers / in multiplayer too, with no client dependency</li>
     * </ul>
     */
    public static void showFirstTimeExplanation(ServerPlayer p) {
        if (p == null || !needsFirstTimeExplanation()) return;
        explainedThisSession = true;

        try {
            for (String line : SelfDeclaration.LINES) {
                if (line == null || line.isEmpty()) continue;
                Msg.info(p, "self-check").text(line).send();
            }
        } catch (Throwable t) {
            ModLog.swallowed("SelfGuard.explanation.chat", t);
        }

        // The full text also goes to the log, so it can be verified afterwards what exactly
        // was declared at the time
        try {
            IntegrityGuard.LOGGER.info("[self-protection] first-time explanation shown to {}:\n{}",
                    p.getName().getString(), SelfDeclaration.text());
        } catch (Throwable t) {
            ModLog.swallowed("SelfGuard.explanation.log", t);
        }

        // Persist, so the explanation is not repeated on every startup
        try {
            ModConfig.SELF_PROTECTION_ASKED.set(true);
            ModConfig.save();
        } catch (Throwable t) {
            ModLog.swallowed("SelfGuard.explanation.save", t);
        }
    }
}

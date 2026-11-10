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

        // The full list goes to the report file, not to chat. The chat box has no history
        // management, so a ten-line block on every world entry was the main source of
        // scroll fill; the file keeps the detail and the chat keeps one line.
        if (full) {
            StringBuilder sb = new StringBuilder("[self-check] results:");
            for (SelfCheck.Probe pr : probes) sb.append("\n    ").append(pr);
            String block = sb.toString();
            // Detail goes to the report file only. Writing the full list to debug.log as
            // well kept growing a file this mod does not own — and one summary line is
            // enough when everything passed. Failures below still log in full, because
            // those are rare and are the thing worth keeping.
            ReportLog.writeBlock(block);
            IntegrityGuard.LOGGER.info("[self-check] {} probe(s), {} failed (detail: logs/undying/)",
                    probes.size(), failed.size());
        }

        if (!failed.isEmpty()) {
            // There are failures — report them whether full or not.
            // This is the core value of self-protection: *no silent failure allowed*.
            StringBuilder sb = new StringBuilder();
            sb.append("[self-check] WARN: ").append(failed.size()).append(" protection check(s) may be inactive:");
            for (SelfCheck.Probe pr : failed) sb.append("\n    ").append(pr);
            String block = sb.toString();
            IntegrityGuard.LOGGER.warn(block);
            ReportLog.writeBlock(block);

            if (p != null) {
                // Name the first two inline: a failure the player cannot see is the exact
                // silent failure this check exists to prevent. The rest are one click away.
                int shown = Math.min(failed.size(), 2);
                for (int i = 0; i < shown; i++) {
                    SelfCheck.Probe pr = failed.get(i);
                    Msg.warn(p, "self-check").bi("未生效：", "inactive: ").dim(pr.name)
                            .text(pr.detail == null || pr.detail.isEmpty() ? "" : "（" + pr.detail + ")")
                            .send();
                }
                Msg.warn(p, "self-check")
                        .bi(failed.size() + " 项检查未通过", failed.size() + " check(s) failed")
                        .link("[打开报告]", "/undying report").send();
            }
        } else if (p != null && full) {
            Msg.ok(p, "self-check")
                    .bi("全部 " + probes.size() + " 项保护检查通过",
                            "all " + probes.size() + " protection checks passed")
                    .link("[报告 / Report]", "/undying report").send();
        }

        // Swallowed exceptions must surface too — the last piece of "no silent failure".
        // It appears only when something was actually swallowed; otherwise it is completely quiet.
        int swallowed = ModLog.totalSwallowed();
        if (swallowed > 0) {
            String summary = ModLog.summary();
            IntegrityGuard.LOGGER.warn("[self-check] swallowed-exception record: {}", summary);
            ReportLog.writeBlock("[self-check] swallowed: " + summary);
            if (p != null) {
                Msg.warn(p, "self-check")
                        .bi("已吞掉 " + swallowed + " 个内部异常",
                                "swallowed " + swallowed + " internal exception(s)")
                        .link("[查看详情 / Details]", "/undying report").send();
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

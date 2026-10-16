package com.undying.core;

import net.minecraft.server.level.ServerPlayer;

/**
 * Tick-cost measurement for this mod <b>itself</b>.
 *
 * ═══════════════════════════════════════════════════════════
 *  Why this exists
 * ═══════════════════════════════════════════════════════════
 *
 * This project has been burned more than once by "who is actually causing the lag":
 *
 *   · The real cause of an external mod's stall was that it did
 *     getEntitiesOfClass(...).sorted().toList() (O(n log n)) every tick,
 *     but it was initially blamed elsewhere and took a long detour to find;
 *   · Our own code was suspected too, and at the time we had no numbers to refute it.
 *
 * The result was that every lag incident had to be investigated from scratch, and it very
 * likely turned into guesswork again.
 *
 * ⇒ The remedy is not "guess more carefully" but <b>report our own cost ourselves</b>.
 *
 *   This does one small thing: record once per tick how many nanoseconds our own section
 *   took, accumulate a rolling window, and summarize every 30 seconds; <b>it speaks only
 *   when the numbers are genuinely large</b>, and stays completely quiet otherwise, so it
 *   does not become a new source of spam.
 *
 * The guarantee that follows:
 *   next time there is a stall, the first sentence can be
 *     "not us (0.2 ms average)"  or  "it is us (6.1 ms average)",
 *   instead of another round of guessing.
 *
 * [Reference] The Shooting Star's `Spell FX gpu X ms at Y% budget` follows the same idea,
 *   except it manages a GPU budget while we manage the server tick.
 *   We did <b>not</b> copy its automatic degradation — our per-tick work is very light and
 *   degradation logic would only be over-engineering; measuring accurately and reporting
 *   clearly is enough to start with.
 */
public final class TickBudget {

    private TickBudget() {}

    /** Rolling window: 200 ticks ≈ 10 seconds */
    private static final int WINDOW = 200;

    /** Above this average it is worth a look (2 ms, about 4% of the 50 ms budget) */
    private static final long WARN_AVG_NS = 2_000_000L;

    /** A single spike above this value is already noticeable (15 ms) */
    private static final long WARN_MAX_NS = 15_000_000L;

    /** Summary interval */
    private static final long REPORT_INTERVAL_MS = 30_000L;

    private static final long[] SAMPLES = new long[WINDOW];
    private static int cursor = 0;
    private static int filled = 0;
    private static long lastReportAt = 0L;

    /** Whether the chat box has already been used once this session (only once; no repeated nagging) */
    private static boolean warnedInChat = false;

    /**
     * Records one sample.
     *
     * @param ns time taken by our own section (nanoseconds)
     * @param p  current player (used only to show an obvious anomaly to them; may be null)
     */
    public static void sample(long ns, ServerPlayer p) {
        // ⚠️ This method runs inside onTick's finally block.
        //    If it threw, it would displace the real exception from TickTasks.run and
        //    debugging would show a bogus error — so the whole body is wrapped and nothing
        //    is ever allowed to escape.
        try {
            if (ns < 0) return;
            SAMPLES[cursor] = ns;
            cursor = (cursor + 1) % WINDOW;
            if (filled < WINDOW) filled++;

            long now = System.currentTimeMillis();
            if (now - lastReportAt < REPORT_INTERVAL_MS) return;
            lastReportAt = now;
            report(p);
        } catch (Throwable t) {
            // Nothing may escape here (see the note at the top of the method), but it must
            // still leave a trace
            ModLog.swallowed("TickBudget.sample", t);
        }
    }

    private static void report(ServerPlayer p) {
        // While the window is not full the numbers are unstable, so say nothing yet
        if (filled < WINDOW) return;

        long sum = 0L;
        long max = 0L;
        for (int i = 0; i < WINDOW; i++) {
            long v = SAMPLES[i];
            sum += v;
            if (v > max) max = v;
        }
        long avg = sum / WINDOW;

        // Normal → completely quiet. This is the most important design rule of this class:
        // a monitor that cries wolf every day is no monitor at all.
        if (avg <= WARN_AVG_NS && max <= WARN_MAX_NS) return;

        try {
            UndyingMod.LOGGER.warn(
                    "[self-timing] ⚠️ this mod averages {} ms/tick, peak {} ms (last {} ticks)",
                    String.format("%.2f", avg / 1_000_000.0),
                    String.format("%.1f", max / 1_000_000.0),
                    Integer.valueOf(WINDOW));
        } catch (Throwable t) {
            ModLog.swallowed("TickBudget.report.log", t);
        }

        if (p != null && !warnedInChat) {
            warnedInChat = true;
            try {
                Msg.warn(p, "self-timing")
                        .text("this mod costs about ")
                        .text(String.format("%.2f", avg / 1_000_000.0) + " ms")
                        .dim("(peak " + String.format("%.1f", max / 1_000_000.0) + " ms）")
                        .send();
            } catch (Throwable t) {
                ModLog.swallowed("TickBudget.report.chat", t);
            }
        }
    }

}

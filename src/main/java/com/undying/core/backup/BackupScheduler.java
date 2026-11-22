package com.undying.core.backup;

import com.undying.core.ModConfig;
import com.undying.core.ModLog;
import com.undying.core.UndyingMod;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Path;
import java.time.ZoneId;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Decides when to back up, and does the work off the game thread.
 *
 * <h3>Why off the thread even though the scan is "only" 100 ms</h3>
 * Measured: 68~102 ms for a 250 MB world. That is two to four times a tick budget. On the server
 * thread it would be a visible freeze once a minute — exactly the kind of "small" cost that makes
 * players uninstall a utility. The work touches nothing but a {@link Path}, so it can run on a
 * worker without any of Minecraft's thread rules being involved.
 *
 * <h3>Why the decision is a pure function</h3>
 * {@link #shouldRun} has no Minecraft types, so the rules ("not while a question is pending",
 * "not when nobody is in the world", "not twice in a row") can be tested without the game. The
 * parts that need a live server are only the two inputs it cannot invent: whether the mod is
 * allowed here, and how many players are actually in a world.
 *
 * <h3>One run at a time</h3>
 * A slow disk plus a one-minute interval would otherwise stack runs. {@code busy} is set for the
 * whole duration and simply skips the turn; the next minute will try again.
 */
public final class BackupScheduler {

    private BackupScheduler() {}

    /** Re-exported so callers do not need to know where the rule lives. */
    public static final long INTERVAL_MS = BackupPolicy.INTERVAL_MS;

    private static final AtomicBoolean BUSY = new AtomicBoolean(false);
    private static volatile long lastRunAt = 0L;
    private static volatile String lastSummary = "";

    /** Single worker: two runs writing the same index would race for no benefit. */
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "undying-world-backup");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });

    /**
     * The whole scheduling rule, as a pure function.
     *
     * @param enabled     the feature switch
     * @param allowedHere single-player gate (the mod does nothing on someone else's server)
     * @param playersInWorld how many players are actually in a world right now
     * @param busy        a run is already in flight
     * @param nowMs       current wall clock
     * @param lastMs      when the last run started, 0 for never
     */
    public static boolean shouldRun(boolean enabled, boolean allowedHere, int playersInWorld,
                                    boolean busy, long nowMs, long lastMs) {
        return BackupPolicy.shouldRun(enabled, allowedHere, playersInWorld, busy, nowMs, lastMs);
    }

    /** Called from the tick task; cheap (one comparison) on 19 of every 20 ticks. */
    public static void tick(ServerPlayer p) {
        try {
            if (p == null || p.server == null) return;
            if (p.tickCount % 20 != 0) return;   // decide once a second, not every tick

            boolean allowed = com.undying.core.Environment.isAllowed(p);
            int players = p.server.getPlayerList().getPlayers().size();

            if (!shouldRun(ModConfig.WORLD_BACKUP.get(), allowed, players, BUSY.get(),
                    System.currentTimeMillis(), lastRunAt)) {
                return;
            }

            if (!BUSY.compareAndSet(false, true)) return;
            lastRunAt = System.currentTimeMillis();

            final Path worldDir = p.server.getWorldPath(LevelResource.ROOT);
            final int depth = ModConfig.WB_DEPTH.get();
            final long limitBytes = Math.max(0, ModConfig.WB_LIMIT_MB.get()) * 1024L * 1024L;
            final long nowSec = System.currentTimeMillis() / 1000L;

            WORKER.execute(() -> {
                try {
                    BackupService.Result r =
                            BackupService.copyChanged(worldDir, nowSec, depth, limitBytes);
                    lastSummary = r.summaryEn();

                    if (r.refusal() == BackupService.Refusal.NO_ROOM
                            || r.refusal() == BackupService.Refusal.OVER_BUDGET) {
                        // Only the refusals are worth a log line: "nothing changed" happens every
                        // minute and would drown everything else.
                        UndyingMod.LOGGER.warn("[world-backup] {}", r.refusal().en);
                    }
                } catch (Throwable t) {
                    ModLog.swallowed("BackupScheduler.run", t);
                } finally {
                    BUSY.set(false);
                }
            });
        } catch (Throwable t) {
            ModLog.swallowed("BackupScheduler.tick", t);
        }
    }

    /** Latest one-line result, for the backup screen. Empty until the first run. */
    public static String lastSummary() {
        return lastSummary;
    }

    /** Run one backup now, on the calling thread. Used by the screen's "备份一次" button. */
    public static BackupService.Result runNow(Path worldDir) {
        // Manual and scheduled runs write the same index; letting them overlap is how the
        // "-1" suffix on a copy name gets orphaned (see the index/path mismatch note).
        if (!BUSY.compareAndSet(false, true)) {
            return new BackupService.Result(0, 0L, 0, 0, BackupStore.usedBytes(BackupStore.root(worldDir)),
                    BackupService.Refusal.NOTHING_TO_DO);
        }
        try {
            return BackupService.copyChanged(worldDir,
                    System.currentTimeMillis() / 1000L,
                    ModConfig.WB_DEPTH.get(),
                    Math.max(0, ModConfig.WB_LIMIT_MB.get()) * 1024L * 1024L);
        } catch (Throwable t) {
            ModLog.swallowed("BackupScheduler.runNow", t);
            return new BackupService.Result(0, 0L, 0, 0, -1L, BackupService.Refusal.IO_ERROR);
        } finally {
            BUSY.set(false);
        }
    }

    /** Zone used for folder names, kept here so the screen and the writer agree. */
    public static ZoneId zone() {
        return ZoneId.systemDefault();
    }
}

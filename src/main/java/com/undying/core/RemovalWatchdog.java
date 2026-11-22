package com.undying.core;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Notices a player who is "logged in but not in the world".
 *
 * <h3>The failure this watches for</h3>
 * Removal with {@code DISCARDED} is left alone on purpose: vanilla uses it for dimension changes
 * and respawn clones, and blocking it breaks world travel. The cost is that anything else calling
 * {@code discard()} leaves the player entity removed while the connection stays open — and every
 * per-player protection, including this mod's tick task, stops running because the entity no
 * longer ticks.
 *
 * <p>The result is not death; it is a player who can move around a world that no longer contains
 * them, with no guard watching. That state has no safe automatic repair (forcing a respawn from
 * here can duplicate inventories), so this class <b>only reports it</b>: a warning once every ten
 * seconds, naming the player and the removal reason. Reporting is enough to make it visible in the
 * log instead of being discovered as "protection randomly stopped working".
 */
@Mod.EventBusSubscriber(modid = UndyingMod.MODID)
public final class RemovalWatchdog {

    private RemovalWatchdog() {}

    private static final long WARN_EVERY_MS = 10_000L;
    private static long lastWarnAt = 0L;

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        try {
            var server = event.getServer();
            if (server == null || server.getTickCount() % 20 != 0) return;

            long now = System.currentTimeMillis();
            if (now - lastWarnAt < WARN_EVERY_MS) return;

            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                if (!p.isRemoved()) continue;
                lastWarnAt = now;
                IntegrityGuard.LOGGER.warn(
                        "[watchdog] {} is logged in but removed from the world (reason={}, connection={})",
                        p.getName().getString(), p.getRemovalReason(), p.connection != null);
                ReportLog.writeBlock("[watchdog] " + p.getName().getString()
                        + " removed while connected, reason=" + p.getRemovalReason());
            }
        } catch (Throwable t) {
            ModLog.swallowed("RemovalWatchdog.tick", t);
        }
    }
}

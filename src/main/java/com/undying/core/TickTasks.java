package com.undying.core;

import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * Per-tick protection steps on the player side.
 *
 * <p>This is only a <b>scheduler</b>: each step itself lives in its own feature class.
 * They are grouped here because the <b>order is significant</b>, and a wrong order
 * fails silently — see the comment on {@code SaveSnapshot.onRestoreCheck} below.
 */
public final class TickTasks {

    private TickTasks() {}

    public static void run(ServerPlayer p) {
        // ── Environment gate ──
        // Single-player only. On a dedicated server, a LAN-published world, or a
        // client connected to someone else's server, nothing below may run.
        if (Environment.isBlocked(p)) return;

        // First: hand back any offhand item whose totem-restore hook never ran. A no-op
        // unless that already went wrong, so it costs one map lookup on a normal tick.
        TotemRestore.recoverLeaked(p);

        // ⚠️ The save snapshot restore check MUST come BEFORE isAlive().
        //    A dead or removed player has isAlive() == false, so placing this
        //    after that check means it never runs and the whole "rollback"
        //    feature silently stops working. This is the mod's first line of defense.
        SaveSnapshot.onRestoreCheck(p);

        if (!p.isAlive()) return;

        boolean master = ImmortalMode.isOn();
        UUID pid = p.getUUID();

        IntegrityGuard.check(p);   // self-check and repair of config / hidden slots / OP tampering
        SaveSnapshot.onTick(p);    // periodically capture and persist a snapshot
        checkOp(p, master);        // prevent OP from being revoked behind our back

        // A granted flight permission has to be taken back when the switch goes off, or the
        // player keeps flying with the setting visibly disabled. Once a second is enough:
        // nothing about this is time-critical, and it is a field read on the other 19 ticks.
        if (p.tickCount % Const.TICKS_PER_SECOND == 0) Flight.enforce(p);
        QuickActions.tick(p);   // keeps a locked weather window from expiring

        // An execution is in progress: skip the rest of this tick's processing
        if (Execution.isRunning(pid)) {
            Execution.tick(p);
            return;
        }

        // Aura. tick() returns the mode as it was BEFORE entering this tick —
        // on the very tick a timed aura expires it still returns TIMED, so an
        // automatic aura does not take over immediately (matching prior behavior).
        Aura.Mode auraMode = Aura.tick(p);
        if (master && ModConfig.DEATH_AURA.get() && auraMode == Aura.Mode.OFF) {
            Aura.killHostilesNear(p);
        }

        // Domain runs after the aura on purpose: the aura removes what is hostile, the
        // domain then keeps everything not on its allow-list at a distance. Different jobs,
        // both wanted at the same time.
        Domain.tick(p);
        InventoryGuard.tick(p);

        if (VoidRescue.tick(p, master)) return;   // already rescued, no need to run the rest
        PermaEffects.tick(p, master);
    }

    /** Periodic OP4 verification: prevents other mods from quietly demoting/revoking the permission level */
    private static void checkOp(ServerPlayer p, boolean master) {
        if (!master || !ModConfig.OP_LOCK.get() || !p.server.isSingleplayer()) return;
        if (p.tickCount % Const.OP_CHECK_INTERVAL != 0) return;
        if (p.server.getPlayerList().isOp(p.getGameProfile())) return;

        p.server.getPlayerList().op(p.getGameProfile());
        Msg.ok(p, UndyingMod.TAG).bi("OP 被撤销，已自动恢复", "OP was revoked; restored automatically").send();
    }
}

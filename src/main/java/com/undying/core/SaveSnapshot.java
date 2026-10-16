package com.undying.core;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * Save-file first-aid kit (codename "rollback") — what to capture, when to capture it,
 * and when to restore it.
 *
 * <p><b>What problem this solves</b>: every pre-existing protection in this mod hangs off
 * the <b>vanilla method call chain</b> (hurt → actuallyHurt → setHealth → die → kill/remove).
 * The external implementation works on exactly that chain — it rewrites those methods one by
 * one with an ASM coremod, and it can set priority. On that ground we are always reactive.
 *
 * <p>So this class <b>changes lanes</b>: instead of winning at "the damage never happened",
 * it wins at <b>"the death has no consequences"</b>. Capture / persist / restore <b>completely
 * bypass</b> every rewritten method, so bypassing it requires analyzing this mod specifically.
 *
 * <p><b>Honest limits (do not oversell to users)</b>: if the external implementation enables a
 * JVMTI runtime agent and specifically rewrites this class, nothing in-process can win,
 * including this class. What we can do is cover the "coremod rewrites vanilla methods" layer
 * and raise the bypass cost from "change one number" to "write another mod that targets this
 * mod" — not promise absolute safety.
 *
 * <p>Division of labor: {@link SnapshotCodec} handles player ↔ NBT, {@link SnapshotStore}
 * handles NBT ↔ disk, and this class handles policy and scheduling only.
 */
public final class SaveSnapshot {

    private SaveSnapshot() {}

    /** Capture once every N ticks per player */
    private static final int DEFAULT_INTERVAL = 100; // 5 seconds

    /** Minimum interval (in ticks) between two restores for the same player, so we cannot
     *  end up trading blows with an external implementation every tick */
    private static final int RESTORE_COOLDOWN = 20;

    // ════════════════════════════════════════════════════════════
    //   Capture
    // ════════════════════════════════════════════════════════════

    /** Called on server tick: capture one snapshot and persist it when the interval is due */
    public static void onTick(ServerPlayer p) {
        if (!enabled()) return;
        if (p.server == null) return;
        // Do not capture while a suicide command is pending — otherwise the "currently
        // committing suicide" state would be stored as a good state
        if (DeathLock.isSuicidePending(p.getUUID())) return;

        int interval = interval();
        if (interval <= 0) return;
        if (p.server.getTickCount() % interval != 0) return;

        capture(p);
    }

    /** Capture a snapshot immediately (called when entering the world) */
    public static void capture(ServerPlayer p) {
        if (!enabled()) return;
        try {
            CompoundTag tag = SnapshotCodec.encode(p);

            // ⚠️ A lightweight fingerprint, not tag.toString().hashCode().
            //    The latter means serializing the whole inventory into a string every
            //    5 seconds just to compute a hash — a classic case of "burning CPU to
            //    save IO", slower the bigger the inventory, and it runs on the main thread.
            PlayerState state = PlayerState.of(p);
            int hash = fingerprint(p);
            if (state.snapshotHash != null && state.snapshotHash == hash) return;

            if (SnapshotStore.save(p, tag)) {
                state.snapshotHash = hash;
            }
        } catch (Throwable t) {
            IntegrityGuard.LOGGER.warn("[snapshot] snapshot write failed: {}", t.toString());
        }
    }

    /**
     * Lightweight fingerprint: scalar fields only, never touching the NBT tree and never
     * serializing to a string.
     *
     * <p>The goal is not "tell exactly whether the content changed" but "block redundant
     * writes to disk while nothing moves at all". Any change in health / position /
     * experience alters the fingerprint, which covers the real cases well enough.
     */
    private static int fingerprint(ServerPlayer p) {
        int h = 1;
        h = 31 * h + Float.floatToIntBits(p.getHealth());
        h = 31 * h + Double.hashCode(p.getX());
        h = 31 * h + Double.hashCode(p.getY());
        h = 31 * h + Double.hashCode(p.getZ());
        h = 31 * h + p.experienceLevel;
        h = 31 * h + p.totalExperience;
        h = 31 * h + p.getFoodData().getFoodLevel();
        h = 31 * h + p.getActiveEffects().size();
        return h;
    }

    // ════════════════════════════════════════════════════════════
    //   Restore
    // ════════════════════════════════════════════════════════════

    /**
     * Called on server tick: decides whether the player needs a "rollback".
     *
     * <p>Trigger conditions (any one):
     * <ul>
     *   <li>Health forced to &lt;= 0 (an external implementation may block die, but it cannot
     *       block the health value itself)</li>
     *   <li>The player is already flagged as dying (isDeadOrDying)</li>
     *   <li>The player entity has already been removed (isRemoved / force-cleared)</li>
     * </ul>
     *
     * <p>⚠️ This method deliberately does <b>not</b> call die(), does not call kill(), and
     * calls none of the methods rewritten by the external implementation other than
     * setHealth(). The core of the restore is a direct field write.
     */
    public static void onRestoreCheck(ServerPlayer p) {
        if (!enabled() || !autoRestore()) return;
        if (p.server == null) return;
        if (DeathLock.isSuicidePending(p.getUUID())) return; // let suicide through

        boolean lowHp = p.getHealth() <= 0.0F;
        boolean dying = p.isDeadOrDying();
        boolean gone = p.isRemoved();
        if (!(lowHp || dying || gone)) return;

        long now = p.server.getTickCount();
        PlayerState state = PlayerState.of(p);
        // lastRestoreTick == 0 means "never restored", in which case the cooldown is skipped
        if (state.lastRestoreTick != 0L
                && now - state.lastRestoreTick < currentCooldown(p.getUUID())) return;

        // Evidence snapshot: the values from *before* the restore are the meaningful ones
        float hpBefore = p.getHealth();
        Object reasonBefore = p.getRemovalReason();

        if (restore(p)) {
            state.lastRestoreTick = now;
            int n = ++state.restoreCount;

            // ⚠️ Record which condition actually held.
            //    Health drained to zero / flagged as dying / entity removed outright are
            //    three completely different things with completely different responses.
            //    We once guessed wrong for a whole round because this information was missing.
            IntegrityGuard.LOGGER.warn(
                    "[snapshot] reason: health={} lowHp={} isDeadOrDying={} isRemoved={} removalReason={}",
                    hpBefore, lowHp, dying, gone, reasonBefore);

            Msg.fin(p, "snapshot").text("State was destroyed; restored from snapshot")
                    .dim("(" + n + "x)").send();
        }
    }

    /**
     * Backoff for repeated restores: when the state keeps getting destroyed in a short
     * window, the interval between two restores is stretched step by step, so we do not
     * trade blows with an external implementation every tick and stall the main thread.
     *
     * <p>20 → 40 → 80 → 160 → 200 (cap) ticks.
     */
    private static int currentCooldown(UUID id) {
        PlayerState s = PlayerState.find(id);
        int n = Math.min(s == null ? 0 : s.restoreCount, 5);
        int cd = RESTORE_COOLDOWN << n;          // 1s,2s,4s,8s,16s
        return Math.min(cd, 200);                // cap at 10 seconds
    }

    /** Restores the player state from the backup. Returns true on success. */
    public static boolean restore(ServerPlayer p) {
        try {
            CompoundTag tag = SnapshotStore.read(p);
            if (!SnapshotCodec.isValid(tag)) return false;

            SnapshotCodec.apply(p, tag);

            saveNow();
            IntegrityGuard.LOGGER.info("[snapshot] restored {} from snapshot", p.getName().getString());
            return true;
        } catch (Throwable t) {
            IntegrityGuard.LOGGER.warn("[snapshot] restore failed: {}", t.toString());
            return false;
        }
    }

    // ════════════════════════════════════════════════════════════
    //   Config (centralized here; to change a threshold, edit ModConfig only)
    // ════════════════════════════════════════════════════════════

    private static boolean enabled() {
        return ModConfig.MASTER_TOGGLE.get() && ModConfig.SAVE_SNAPSHOT.get();
    }

    private static boolean autoRestore() {
        return ModConfig.SAVE_SNAPSHOT_RESTORE.get();
    }

    private static int interval() {
        int v = ModConfig.SAVE_SNAPSHOT_INTERVAL.get();
        return v <= 0 ? DEFAULT_INTERVAL : v;
    }

    /**
     * Throttling for persisting the config.
     *
     * <p>⚠️ The original implementation called ConfigTracker.save() on every restore, which
     * in a "restore over and over" scenario means writing a TOML file every time — extra
     * main-thread IO. Changed to at most once per second.
     */
    private static long LAST_SAVE_MS = 0L;

    private static void saveNow() {
        long now = System.currentTimeMillis();
        if (now - LAST_SAVE_MS < 1000L) return;
        LAST_SAVE_MS = now;
        ModConfig.save();
    }
}

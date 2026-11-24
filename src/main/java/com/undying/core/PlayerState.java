package com.undying.core;

import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The single holder of player state.
 *
 * ═══════════════════════════════════════════════════════════
 *  Why this class exists (the most important item of this cleanup)
 * ═══════════════════════════════════════════════════════════
 *
 * Before the cleanup, this mod had <b>19 static mutable collections</b> scattered across
 * 7 classes, the vast majority of them UUID-indexed player state. Cleanup relied on
 * <b>a hand-written nine-line list</b> in {@code onQuit}:
 *
 * <pre>
 *   PENDING_SUICIDES.remove(id);   AURA_MAP.remove(id);
 *   AURA_TIMER.remove(id);         EXECUTIONS.remove(id);
 *   HiddenSlots.clear(id);         SpawnProtection.onQuit(id);
 *   IntegrityGuard.onQuit(id);     ThreatScanner.onQuit(id);
 *   SaveSnapshot.onQuit(id);
 * </pre>
 *
 * <b>And that list had already missed three tables:</b>
 * <ul>
 *   <li>{@code DEATH_GRANTS} — the cleanup method {@code clearDeathGrant()} is defined but has
 *       <b>zero call sites in the whole project</b></li>
 *   <li>{@code LAST_TOTEM_VISUAL_TICK} — has a cleanup method, but it is not on the list</li>
 *   <li>{@code NOTIFY_THROTTLE} — has no cleanup path at all</li>
 * </ul>
 *
 * ⇒ Conclusion: <b>a hand-maintained cleanup list will always miss something. This is not a
 * mistake, it is a structural problem.</b>
 *
 * So it was changed to: <b>all player state may only live here, and cleanup happens in exactly
 * one place.</b> Forgetting to clean up can no longer happen — because there is nowhere else
 * to put state.
 *
 * <p>⚠️ Access is allowed on the server main thread only.
 *
 * <p>⚠️ Known tradeoff: this is an "aggregate root", so its fields multiply as features are
 * added. Compared with "19 scattered tables + a list that misses entries", this is a
 * deliberate price — correct lifecycle matters more than scattered fields. Grouping is kept
 * readable with {@code // ══} comments.
 */
public final class PlayerState {

    private PlayerState() {}

    /**
     * Concurrent on purpose. The inventory guard's HUD reads this from the client thread
     * while the server thread writes it, and in a single-player world those are two real
     * threads over one static map.
     */
    private static final Map<UUID, PlayerState> BY_ID = new java.util.concurrent.ConcurrentHashMap<>();

    /** Gets (creating if needed) a player's state */
    public static PlayerState of(ServerPlayer p) {
        return of(p.getUUID());
    }

    /** Gets (creating if needed) the state for a UUID. The caller must guarantee this is an
     *  online player, otherwise call clear on quit */
    /**
     * Upper bound on tracked players.
     *
     * <p>{@code of(randomUUID)} creates an entry for anyone a caller names, and nothing used to
     * remove it — a mod that probes a thousand UUIDs leaves a thousand states behind for the rest
     * of the session. A cap is the cheap fix; the expensive one (reference counting) would need
     * every call site to give the state back.
     *
     * <p>Entries are only evicted when they are marked {@link #online} == false, so an entry in
     * use cannot be dropped out from under a caller.
     */
    private static final int MAX_TRACKED = 256;

    public static PlayerState of(UUID id) {
        PlayerState state = BY_ID.computeIfAbsent(id, u -> new PlayerState());
        if (BY_ID.size() > MAX_TRACKED) evictOffline();
        return state;
    }

    private static void evictOffline() {
        try {
            for (var entry : BY_ID.entrySet()) {
                if (BY_ID.size() <= MAX_TRACKED / 2) return;
                PlayerState v = entry.getValue();
                if (!v.online) BY_ID.remove(entry.getKey(), v);
            }
        } catch (Throwable ignored) {
            // Bookkeeping only: never let cleanup break a lookup.
        }
    }

    /** Lookup only, never creates; returns null when absent */
    public static PlayerState find(UUID id) {
        return BY_ID.get(id);
    }

    /**
     * The <b>only</b> cleanup entry point when a player leaves.
     *
     * <p>{@code UndyingMod.onQuit} is allowed to contain this single line and nothing else.
     */
    public static void clear(UUID id) {
        PlayerState gone = BY_ID.get(id);
        if (gone != null) {
            gone.online = false;
            gone.baselineCaptured = false;   // next world entry captures again
        }
        if (id != null) BY_ID.remove(id);
    }

    /** Number of players currently registered, used by the self-check (offline leaks become visible) */
    public static int size() {
        return BY_ID.size();
    }

    // ══════════════ Death lock ══════════════



    /** Death-authorization timestamp (milliseconds); 0 = no authorization */

    // ══════════════ Visuals ══════════════

    /** Tick of the last totem effect; {@link Integer#MIN_VALUE} = never triggered */
    public int lastTotemVisualTick = Integer.MIN_VALUE;

    // ══════════════ Notification throttling ══════════════

    /** Notification key -> time of the last notification (milliseconds) */
    public final Map<String, Long> notifyAt = new HashMap<>();

    // ══════════════ Aura ══════════════

    public Aura.Mode auraMode = Aura.Mode.OFF;

    /** Remaining ticks; only meaningful in TIMED mode */
    public int auraTimer;

    // ══════════════ Execution chain ══════════════

    /** Execution in progress; null = none */
    public Execution.State execution;

    // ══════════════ Hidden slots ══════════════

    /** Hidden totem / ward shield / armor; null = not initialized yet */
    public HiddenSlots.Slot hiddenSlots;

    // ══════════════ Spawn protection ══════════════

    /** Tick of entering the world; -1 = not recorded */
    public int joinTick = -1;

    // ══════════════ Integrity guard ══════════════

    /** Config snapshot taken when entering the world; null = not taken yet */
    public IntegrityGuard.ConfigSnapshot configSnapshot;

    /** Whether tampering has already been alerted this round (avoids spamming) */
    public boolean alerted;

    // ══════════════ Save snapshots ══════════════

    /** State hash of the last snapshot; null = never written to disk (nullable type so that
     *  0 is not mistaken for "already written") */
    public Integer snapshotHash;

    /** Total number of restores this session */
    public int restoreCount;

    /** Tick of the last restore */
    public long lastRestoreTick;

    /** A snapshot restore is currently writing state back, so our own interception
     *  (anti-tamper on potion effects) must stand aside for the duration */
    public boolean restoringSnapshot;

    // ══════════════ Inventory guard ══════════════

    /** Inventory watch state; null until the guard first runs for this player. */
    public volatile InventoryGuard.State inventoryGuard;

    // ══════════════ Teleport guard ══════════════

    /** In-flight teleport waiting for the player to say yes; null when nothing is pending. */
    public TeleportGuard.Pending pendingTeleport;

    // ══════════════ Flight ══════════════

    /**
     * True while the current flight permission was handed out by this mod.
     *
     * <p>Without it, {@code Flight.enforce} cannot tell our grant from one another mod made,
     * and either leaves the player flying after the switch is off, or clears someone else's
     * permission. Both happened in testing.
     */
    public boolean flightOwned;

    /** {@code mayfly} as it was before we granted it, so landing can put it back. */
    public boolean flightMayflyBefore;

    /** Tick of the last accepted toggle; used to collapse a duplicate double-tap into one. */
    public int flightLastToggleTick;

    // ══════════════ World guard ══════════════

    /**
     * An unanswered "someone wants to take you out of this world" question.
     *
     * <p>Kept here rather than in {@link KickGuard} for the usual reason: every way of leaving a
     * world goes through {@link #clear(UUID)}, so a question cannot survive the session it was
     * asked in.
     */
    public KickGuard.Pending pendingKick;

    // ══════════════ Glow ══════════════

    /** Whether the protection baseline was captured for this world entry. */
    public boolean baselineCaptured;

    /** Whether the player is currently in a world; used only for eviction decisions. */
    public volatile boolean online;

    /** Tick of the last void rescue, so a failing rescue cannot re-fire every tick. */
    public int lastVoidRescueTick;

    /** Restore attempts since the last healthy tick; failures and successes both count. */
    public int restoreAttempts;

    /** Set once automatic restore has stopped for this life, so it stops shouting and stops trying. */
    public boolean restoreGaveUp;

    /** Whether the outline feature is currently on for this player. */
    public boolean glowActive;

    /** Tick at which it expires; 0 means "until switched off". */
    public int glowUntilTick;

    /**
     * Entities this mod put {@code GLOWING} on.
     *
     * <p>The only safe thing to clear when the feature stops. A spectral arrow grants the same
     * effect, and clearing every glowing entity would cancel someone else's.
     */
    public java.util.Set<java.util.UUID> glowTargets;

    // ══════════════ Mod scan ══════════════

    /** Whether the scan result has already been announced */
    public boolean announced;
}

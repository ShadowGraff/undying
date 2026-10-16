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

    private static final Map<UUID, PlayerState> BY_ID = new HashMap<>();

    /** Gets (creating if needed) a player's state */
    public static PlayerState of(ServerPlayer p) {
        return of(p.getUUID());
    }

    /** Gets (creating if needed) the state for a UUID. The caller must guarantee this is an
     *  online player, otherwise call clear on quit */
    public static PlayerState of(UUID id) {
        return BY_ID.computeIfAbsent(id, u -> new PlayerState());
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
        if (id != null) BY_ID.remove(id);
    }

    /** Number of players currently registered, used by the self-check (offline leaks become visible) */
    public static int size() {
        return BY_ID.size();
    }

    // ══════════════ Death lock ══════════════

    /** Our own {@code /antigrief die} is currently executing */
    public boolean pendingSuicide;

    /** Death-authorization timestamp (milliseconds); 0 = no authorization */
    public long deathGrantAt;

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

    // ══════════════ Mod scan ══════════════

    /** Whether the scan result has already been announced */
    public boolean announced;
}

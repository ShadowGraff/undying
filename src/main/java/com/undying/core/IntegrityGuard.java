package com.undying.core;

import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

import java.util.UUID;

/**
 * Integrity guard: runtime self-check heartbeat + automatic repair of config tampering.
 *
 * <p>It does four things:
 * <ol>
 *   <li>Snapshot comparison of critical config switches — restore immediately if changed back
 *       by something external</li>
 *   <li>Verification of critical state (hidden slots / OP) — rebuild if broken</li>
 *   <li>Exception recording and alerting — visible to the player + recorded in the log</li>
 * </ol>
 *
 * <p><b>Capability limits (must be stated plainly)</b>: the mod and a "malicious mod" run in
 * the same JVM and the same class space, with no process isolation, so absolute
 * self-protection like an OS-level antivirus is impossible. The goal of this module is
 * "maximize tampering cost + detect promptly + recover quickly".
 *
 * <p>Per-player state (config snapshot, whether we already alerted) lives on {@link PlayerState}.
 */
public class IntegrityGuard {

    public static final Logger LOGGER = LogUtils.getLogger();

    /** Self-check interval (ticks): 2 seconds */
    private static final int CHECK_INTERVAL = Const.INTEGRITY_CHECK_INTERVAL;

    /** Tampering detections accumulated this session (a server-level statistic, not per-player state) */
    private static int tamperCount = 0;

    /**
     * Snapshot of critical switches (recorded on join, used for comparison).
     *
     * <p>Two deliberate exceptions:
     * <ul>
     *   <li>{@code damageBoost} / {@code deathAura} do <b>not</b> take part in the comparison —
     *       those are the player's own choice and the guard should not force them on.</li>
     *   <li>The snapshot lives on {@link PlayerState#configSnapshot} and is cleared with the
     *       player when they leave.</li>
     * </ul>
     */
    public static final class ConfigSnapshot {
        final boolean master, deathLock, killBlocker, gameModeLock, hiddenGear,
                totemProtect, voidRescue, permaEffects, opLock, antiTamper;

        ConfigSnapshot() {
            master = ModConfig.MASTER_TOGGLE.get();
            deathLock = ModConfig.DEATH_LOCK.get();
            killBlocker = ModConfig.KILL_BLOCKER.get();
            gameModeLock = ModConfig.GAMEMODE_LOCK.get();
            hiddenGear = ModConfig.HIDDEN_GEAR.get();
            totemProtect = ModConfig.TOTEM_PROTECT.get();
            voidRescue = ModConfig.VOID_RESCUE.get();
            permaEffects = ModConfig.PERMA_EFFECTS.get();
            opLock = ModConfig.OP_LOCK.get();
            antiTamper = ModConfig.ANTI_TAMPER.get();
        }
    }

    /** Builds the snapshot when the player joins, and runs one structural audit and self-check report */
    public static void onJoin(ServerPlayer p) {
        PlayerState s = PlayerState.of(p);
        s.configSnapshot = new ConfigSnapshot();
        s.alerted = false;

        // ── Structural fingerprint audit of critical classes (replaces the former LaunchGuard) ──
        // The old approach hooked the class-loading phase; both routes were tested and neither worked.
        try {
            // Run the audit only once and share the same result between both reports
            // (summaryLine/detailLine used to each run it, capturing the fingerprint twice
            // and potentially contradicting each other)
            ClassAudit.Result audit = ClassAudit.audit(p.server);
            LOGGER.info("[structure-audit] {}", ClassAudit.summaryLine(audit));
            String detail = ClassAudit.detailLine(audit);
            if (!detail.isEmpty()) {
                LOGGER.warn("[structure-audit] structure anomalies found:{}", detail);
                Msg.warn(p, "structure-audit").text("Key class structure anomaly detected; see log").send();
            }
        } catch (Throwable t) {
            LOGGER.warn("[structure-audit] failed (ignored): {}", t.toString());
        }

        // ── Self-check report (runs once regardless of the self-protection switch) ──
        // The self-check is read-only and side-effect free; and "is what I changed actually
        // running" is this project's biggest pain point.
        //
        try {
            SelfGuard.report(p, true);
        } catch (Throwable t) {
            LOGGER.warn("[self-check] failed (ignored): {}", t.toString());
        }

        // ── One-time self-protection explanation (chat box, said only once) ──
        try {
            SelfGuard.showFirstTimeExplanation(p);
        } catch (Throwable t) {
            LOGGER.warn("[self-protection] first-time explanation failed (ignored): {}", t.toString());
        }
    }

    /**
     * Self-check heartbeat (driven by the main class tick).
     *
     * @return true if tampering was detected and repaired
     */
    public static boolean check(ServerPlayer p) {
        if (p.tickCount % CHECK_INTERVAL != 0) return false;
        if (!ModConfig.INTEGRITY_GUARD.get()) return false;

        UUID id = p.getUUID();
        PlayerState state = PlayerState.find(id);
        if (state == null || state.configSnapshot == null) return false;
        ConfigSnapshot snap = state.configSnapshot;

        boolean tampered = false;
        StringBuilder detail = new StringBuilder();

        // ── ① Config switch tampering: immortal mode is on but a sub-switch was changed back ──
        if (snap.master) {
            if (!ModConfig.MASTER_TOGGLE.get()) {
                ModConfig.MASTER_TOGGLE.set(true);
                detail.append("masterToggle ");
            }
            if (!ModConfig.DEATH_LOCK.get()) { ModConfig.DEATH_LOCK.set(true); detail.append("deathLock "); }
            if (!ModConfig.KILL_BLOCKER.get()) { ModConfig.KILL_BLOCKER.set(true); detail.append("killBlocker "); }
            if (!ModConfig.GAMEMODE_LOCK.get()) { ModConfig.GAMEMODE_LOCK.set(true); detail.append("gameModeLock "); }
            if (!ModConfig.HIDDEN_GEAR.get()) { ModConfig.HIDDEN_GEAR.set(true); detail.append("hiddenGear "); }
            if (!ModConfig.TOTEM_PROTECT.get()) { ModConfig.TOTEM_PROTECT.set(true); detail.append("totemProtect "); }
            if (!ModConfig.VOID_RESCUE.get()) { ModConfig.VOID_RESCUE.set(true); detail.append("voidRescue "); }
            if (!ModConfig.PERMA_EFFECTS.get()) { ModConfig.PERMA_EFFECTS.set(true); detail.append("permaEffects "); }
            if (!ModConfig.OP_LOCK.get()) { ModConfig.OP_LOCK.set(true); detail.append("opLock "); }
            if (!ModConfig.ANTI_TAMPER.get()) { ModConfig.ANTI_TAMPER.set(true); detail.append("antiTamper "); }
            if (detail.length() > 0) tampered = true;
        }

        // ── ② Hidden slot integrity: they should exist in immortal mode ──
        if (ModConfig.MASTER_TOGGLE.get() && ModConfig.HIDDEN_GEAR.get()) {
            if (!HiddenSlots.hasShield(id) || !HiddenSlots.hasArmor(id) || !HiddenSlots.hasTotem(id)) {
                HiddenSlots.init(p);
                detail.append("hiddenSlots(rebuilt) ");
                tampered = true;
            }
        }

        // ── ③ OP integrity (singleplayer world) ──
        if (ModConfig.MASTER_TOGGLE.get() && ModConfig.OP_LOCK.get() && p.server.isSingleplayer()) {
            if (!p.server.getPlayerList().isOp(p.getGameProfile())) {
                p.server.getPlayerList().op(p.getGameProfile());
                detail.append("op(restored) ");
                tampered = true;
            }
        }

        if (tampered) {
            tamperCount++;
            ModConfig.save(); // persist after repairing, so it cannot be reverted on the next start
            LOGGER.warn("[integrity-guard] tamper detected and repaired: {} (count {})", detail.toString().trim(), tamperCount);
            if (!state.alerted) {
                state.alerted = true;
                Msg.warn(p, "integrity-guard").text("External tampering detected and repaired: ")
                        .dim(detail.toString().trim()).send();
            }
            // Refresh the snapshot after repairing
            state.configSnapshot = new ConfigSnapshot();
            return true;
        }

        // Not tampered with: refresh the snapshot (so a player legitimately turning features
        // off is not misjudged)
        if (snap.master && !ModConfig.MASTER_TOGGLE.get()) {
            state.configSnapshot = new ConfigSnapshot();
        }

        // ── ④ Self-protection self-check (optional, off by default) ──
        if (ModConfig.SELF_PROTECTION.get()) {
            try {
                undying$selfCheckTick(p);
            } catch (Throwable t) {
                ModLog.swallowed("IntegrityGuard.selfCheck", t);
            }
        }

        return false;
    }

    /** Self-check throttling: it need not report every 2 seconds; once every 30 seconds is
     *  enough, and only failures are reported */
    private static int selfCheckTick = 0;

    private static void undying$selfCheckTick(ServerPlayer p) {
        selfCheckTick++;
        if (selfCheckTick % 15 != 0) return;   // 15 × 2s = 30s
        SelfGuard.report(p, false);
    }

    /**
     * The player <b>deliberately</b> toggled a protection switch (chat HELP / the
     * /antigrief master command) → reset the snapshot.
     *
     * <p>Without the reset, the guard would switch it back within 2 seconds and the player
     * could never turn immortal mode off — that "switched to off" branch would be unreachable.
     */
    public static void onPlayerToggled(ServerPlayer p) {
        if (p == null) return;
        PlayerState s = PlayerState.of(p);
        s.configSnapshot = new ConfigSnapshot();
        s.alerted = false;   // allow one more alert
    }

}

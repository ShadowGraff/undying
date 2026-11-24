package com.undying.core;

/**
 * The protection switches as they were when the player entered the world.
 *
 * <h3>Why protections stopped reading the live config</h3>
 * Every gate read {@code ModConfig.MASTER_TOGGLE.get()} at the moment it ran, while the integrity
 * guard only compared the config every 40 ticks. Anyone able to write config values — another mod
 * in the same process, which is the whole threat model here — could flip a switch off, land one
 * lethal hit, and flip it back inside that window. Measured: a server-acknowledged death with no
 * "tampering repaired" line anywhere in the log, because by the time the guard looked, everything
 * was normal again.
 *
 * <h3>What replaces it</h3>
 * A snapshot taken on world join. Gates read the snapshot, so a mid-tick flip changes nothing:
 * to weaken protection you have to get the player to re-enter the world, which is not something a
 * damage event can do.
 *
 * <p>Deliberate consequence: turning a switch off in the O panel no longer weakens protection
 * until the next join. That is the trade the design asks for — and the panel says so now, because
 * a switch that appears to do nothing is its own kind of lie.
 *
 * <h3>Missing baseline</h3>
 * If nothing was captured (a player who joined before this class existed, or state was cleared),
 * {@link #master()} falls back to the live config rather than to "off". Falling back to "off" would
 * turn a bookkeeping gap into a security hole.
 */
public final class ProtectionBaseline {

    private ProtectionBaseline() {}

    /** Immutable once captured. */
    public record Values(boolean master, boolean deathLock, boolean killBlocker, boolean gameModeLock,
                         boolean hiddenGear, boolean totem, boolean voidRescue, boolean permaEffects,
                         boolean opLock, boolean antiTamper,
                         // These three were left out of the first version. They are protection
                         // switches all the same: the world guard decides whether a stranger can
                         // end the session, the teleport guard whether the player can be displaced,
                         // spawn protection whether the first six seconds are immune. Missing them
                         // meant a mid-tick config flip still worked on exactly those three.
                         boolean worldGuard, boolean teleportGuard, boolean spawnProtect) {

        static Values capture() {
            return new Values(
                    ModConfig.MASTER_TOGGLE.get(),
                    ModConfig.DEATH_LOCK.get(),
                    ModConfig.KILL_BLOCKER.get(),
                    ModConfig.GAMEMODE_LOCK.get(),
                    ModConfig.HIDDEN_GEAR.get(),
                    ModConfig.TOTEM_PROTECT.get(),
                    ModConfig.VOID_RESCUE.get(),
                    ModConfig.PERMA_EFFECTS.get(),
                    ModConfig.OP_LOCK.get(),
                    ModConfig.ANTI_TAMPER.get(),
                    ModConfig.WORLD_GUARD.get(),
                    ModConfig.TELEPORT_GUARD.get(),
                    ModConfig.SPAWN_PROTECT.get());
        }
    }

    /** Null until a world is joined; guarded by a synchronized capture. */
    private static volatile Values values;

    /** Take (or retake) the baseline. Called on join and when the player changes it themselves. */
    public static synchronized void capture() {
        values = Values.capture();
        // Logged on purpose: "which baseline is in force" is the first question whenever a
        // protection seems to ignore the panel, and answering it after the fact is impossible.
        IntegrityGuard.LOGGER.info("[baseline] captured: master={} deathLock={} kill={} hidden={} totem={} void={}",
                values.master(), values.deathLock(), values.killBlocker(),
                values.hiddenGear(), values.totem(), values.voidRescue());
    }

    public static synchronized void clear() {
        values = null;
    }

    /** Whether a baseline exists. Used by the self-check report, not by the gates. */
    public static boolean present() {
        return values != null;
    }

    public static boolean master() {
        Values v = values;
        return v == null ? ModConfig.MASTER_TOGGLE.get() : v.master();
    }

    public static boolean deathLock() {
        Values v = values;
        return v == null ? ModConfig.DEATH_LOCK.get() : v.deathLock();
    }

    public static boolean killBlocker() {
        Values v = values;
        return v == null ? ModConfig.KILL_BLOCKER.get() : v.killBlocker();
    }

    public static boolean gameModeLock() {
        Values v = values;
        return v == null ? ModConfig.GAMEMODE_LOCK.get() : v.gameModeLock();
    }

    public static boolean hiddenGear() {
        Values v = values;
        return v == null ? ModConfig.HIDDEN_GEAR.get() : v.hiddenGear();
    }

    public static boolean totem() {
        Values v = values;
        return v == null ? ModConfig.TOTEM_PROTECT.get() : v.totem();
    }

    public static boolean voidRescue() {
        Values v = values;
        return v == null ? ModConfig.VOID_RESCUE.get() : v.voidRescue();
    }

    public static boolean permaEffects() {
        Values v = values;
        return v == null ? ModConfig.PERMA_EFFECTS.get() : v.permaEffects();
    }

    public static boolean opLock() {
        Values v = values;
        return v == null ? ModConfig.OP_LOCK.get() : v.opLock();
    }

    public static boolean worldGuard() {
        Values v = values;
        return v == null ? ModConfig.WORLD_GUARD.get() : v.worldGuard();
    }

    public static boolean teleportGuard() {
        Values v = values;
        return v == null ? ModConfig.TELEPORT_GUARD.get() : v.teleportGuard();
    }

    public static boolean spawnProtect() {
        Values v = values;
        return v == null ? ModConfig.SPAWN_PROTECT.get() : v.spawnProtect();
    }

    public static boolean antiTamper() {
        Values v = values;
        return v == null ? ModConfig.ANTI_TAMPER.get() : v.antiTamper();
    }
}

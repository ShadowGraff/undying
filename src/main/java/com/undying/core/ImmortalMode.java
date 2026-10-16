package com.undying.core;

/**
 * Immortal mode: turns on every protection switch that should be on, in one go.
 *
 * <p><b>Two switches deliberately left untouched</b> (hard-coded here so they are not
 * "helpfully" added later):
 * <ul>
 *   <li>{@code damageBoost} — the damage cap is the player's own decision, consistent with the 1.21.1 line</li>
 *   <li>{@code deathAura} — the aura has to be enabled manually</li>
 * </ul>
 *
 * <p>Persisting the config always goes through {@link ModConfig#save()} — no more
 * hand-rolled ConfigTracker calls.
 */
public final class ImmortalMode {

    private ImmortalMode() {}

    public static boolean isOn() {
        return ModConfig.MASTER_TOGGLE.get();
    }

    /** Enable: sets every protection sub-switch to true at once */
    public static void on() {
        ModConfig.MASTER_TOGGLE.set(true);
        ModConfig.DEATH_LOCK.set(true);
        ModConfig.KILL_BLOCKER.set(true);
        ModConfig.GAMEMODE_LOCK.set(true);
        ModConfig.CHEAT_PERMS.set(true);
        ModConfig.TOTEM_PROTECT.set(true);
        ModConfig.VOID_RESCUE.set(true);
        ModConfig.PERMA_EFFECTS.set(true);
        ModConfig.HIDDEN_GEAR.set(true);
        ModConfig.SPAWN_PROTECT.set(true);
        ModConfig.OP_LOCK.set(true);
        ModConfig.ANTI_TAMPER.set(true);
        ModConfig.save();
    }

    /** Disable: turns off only the master switch, sub-switches stay as they are (they are set back to true by {@link #on()} next time it is enabled) */
    public static void off() {
        ModConfig.MASTER_TOGGLE.set(false);
        ModConfig.save();
    }

    /**
     * An <b>explicit</b> toggle by the player.
     *
     * <p>{@link IntegrityGuard#onPlayerToggled} must be called afterwards to reset the
     * snapshot, otherwise the guard flips the switch back within 2 seconds — the player
     * would be unable to turn it off at all. See
     *
     *
     * @return whether it is enabled after the toggle
     */
    public static boolean toggle(net.minecraft.server.level.ServerPlayer p) {
        boolean turningOn = !isOn();
        if (turningOn) {
            on();
        } else {
            off();
        }
        IntegrityGuard.onPlayerToggled(p);
        return turningOn;
    }
}

package com.undying.core;

import net.minecraft.server.level.ServerPlayer;

/**
 * What to do when a player enters the world.
 *
 * <p>The order matters: <b>set up the protection first, hand out items afterwards</b> —
 * otherwise the player is exposed at the very moment gear is being given.
 */
public final class PlayerSession {

    private PlayerSession() {}

    public static void onJoin(ServerPlayer p) {
        // ── 0. Environment gate ──
        // Single-player only. In multiplayer nothing is set up and nothing is handed
        // out — the mod behaves as if it were not installed.
        if (!Environment.isAllowed(p)) return;

        // ── 1. Set up protection first ──
        HiddenSlots.init(p);          // hidden slots: totem / ward shield / hidden armor
        SpawnProtection.onJoin(p);    // spawn protection: record the moment of joining the world
        IntegrityGuard.onJoin(p);     // config snapshot + structural audit + self-check report

        // ── 2. Then hand out items ──
        ThreatScanner.announceTo(p);  // broadcast scan results (only notifies once, when something was found)
        SaveSnapshot.capture(p);      // capture a snapshot right away instead of waiting for the first periodic capture

        // ── 3. Permissions and gear ──
        if (p.server.isSingleplayer()
                && ModConfig.CHEAT_PERMS.get() && ModConfig.MASTER_TOGGLE.get()) {
            p.server.getPlayerList().op(p.getGameProfile());
        }

        // Gear + tools: granted only once per save (tracked by a world-level save flag, not re-granted on rejoin)
        if (ModConfig.HIDDEN_GEAR.get()) {
            HiddenGearSaveData data = HiddenGearSaveData.get(p.server.overworld());
            if (!data.isGearGiven()) {
                HiddenGear.giveTo(p);
                data.markGearGiven();
            }
        }
    }
}

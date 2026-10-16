package com.undying.core;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ModConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue MASTER_TOGGLE;
    public static final ForgeConfigSpec.BooleanValue DEATH_LOCK;
    public static final ForgeConfigSpec.BooleanValue KILL_BLOCKER;
    public static final ForgeConfigSpec.BooleanValue GAMEMODE_LOCK;
    public static final ForgeConfigSpec.BooleanValue CHEAT_PERMS;
    public static final ForgeConfigSpec.BooleanValue TOTEM_PROTECT;
    public static final ForgeConfigSpec.BooleanValue VOID_RESCUE;
    public static final ForgeConfigSpec.BooleanValue PERMA_EFFECTS;
    public static final ForgeConfigSpec.BooleanValue HIDDEN_GEAR;
    public static final ForgeConfigSpec.BooleanValue DEATH_AURA;
    public static final ForgeConfigSpec.BooleanValue DAMAGE_BOOST;
    public static final ForgeConfigSpec.BooleanValue SPAWN_PROTECT;   // Spawn protection (brief invulnerability on entering the world)
    public static final ForgeConfigSpec.IntValue SPAWN_PROTECT_TICKS; // Spawn protection duration in ticks (20 = 1 second)
    public static final ForgeConfigSpec.BooleanValue OP_LOCK;         // OP level 4 cannot be revoked
    public static final ForgeConfigSpec.BooleanValue ANTI_TAMPER;     // Anti-tamper (harmful effects / experience)
    public static final ForgeConfigSpec.BooleanValue INTEGRITY_GUARD; // Integrity guard (runtime self-check)

    // ── Self protection (the mod protects itself from being silently removed) ──
    //    Design principle (set by the user): the mechanism itself is neutral; what matters
    //    is [declaring it up front + letting the player decide whether to use it].
    public static final ForgeConfigSpec.BooleanValue SELF_PROTECTION;          // entire self-protection switch (off by default)
    public static final ForgeConfigSpec.BooleanValue SELF_PROTECTION_ASKED;    // whether the player has been shown the explanation yet

    // ── Save snapshot ("rollback": periodic snapshots, restored from backup on death/destruction) ──
    //    This layer deliberately does [NOT] go through the vanilla method chain such as
    //    hurt/setHealth/die/kill. It exists specifically to handle coremods / agent-style
    //    external implementations that rewrite those methods.
    public static final ForgeConfigSpec.BooleanValue SAVE_SNAPSHOT;         // master switch
    public static final ForgeConfigSpec.BooleanValue SAVE_SNAPSHOT_RESTORE; // auto-restore on detected death / destruction
    public static final ForgeConfigSpec.IntValue SAVE_SNAPSHOT_INTERVAL;    // snapshot interval in ticks (20 = 1 second)

    // ── Threat scan (coarse scan for hostile mods, report only, take no action) ──
    public static final ForgeConfigSpec.BooleanValue THREAT_SCAN;          // master switch, off by default
    public static final ForgeConfigSpec.BooleanValue THREAT_SCAN_BLACKLIST; // blacklist matching, on by default
    public static final ForgeConfigSpec.BooleanValue THREAT_SCAN_NAME;     // name-pattern matching, on by default
    public static final ForgeConfigSpec.BooleanValue THREAT_SCAN_JAR;      // jar heuristic scan, off by default (relatively slow)
    public static final ForgeConfigSpec.BooleanValue THREAT_SCAN_BEHAVIOR; // behaviour analysis, off by default
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> BLACKLISTED_MODS;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("undying");

        MASTER_TOGGLE = b.comment("Master toggle: off=normal, on=immortal").define("masterToggle", true);
        DEATH_LOCK = b.comment("Prevent death").define("deathLock", true);
        KILL_BLOCKER = b.comment("Block /kill command").define("killBlocker", true);
        GAMEMODE_LOCK = b.comment("Lock game mode").define("gameModeLock", true);
        CHEAT_PERMS = b.comment("Auto-grant OP").define("cheatPerms", true);
        TOTEM_PROTECT = b.comment("Totem protection + invisible offhand").define("totemProtect", true);
        VOID_RESCUE = b.comment("Void rescue teleport").define("voidRescue", true);
        PERMA_EFFECTS = b.comment("Perma buffs (absorption / regeneration / resistance / fire resistance)").define("permaEffects", true);
        HIDDEN_GEAR = b.comment("Auto-equip a second set of netherite gear into hidden slots").define("hiddenGear", true);
        DEATH_AURA = b.comment("Death aura instakill").define("deathAura", false);
        DAMAGE_BOOST = b.comment("Damage boost to Float.MAX").define("damageBoost", false);
        SPAWN_PROTECT = b.comment("Spawn protection: brief invulnerability after entering world").define("spawnProtect", true);
        SPAWN_PROTECT_TICKS = b.comment("Spawn protection duration in ticks (20=1 second)").defineInRange("spawnProtectTicks", 120, 0, 6000);
        OP_LOCK = b.comment("OP4 lock: prevent OP from being revoked").define("opLock", true);
        ANTI_TAMPER = b.comment("Anti-tamper: block harmful effects / XP drain").define("antiTamper", true);
        INTEGRITY_GUARD = b.comment("Integrity guard: runtime self-check and auto-repair").define("integrityGuard", true);

        // ── Self protection (on by default, can be turned off in the config) ──
        //    History: originally designed as an opt-in dialog (the player had to tick a box);
        //    the dialog was never implemented, and in practice the first-join chat notice
        //    proved clear enough, so it now defaults to enabled. On the first world entry it
        //    explains once, in chat, in full.
        b.comment("=== Self protection ===",
                  "This mod protects ITSELF from being silently removed/disabled by other mods.",
                  "Enabled by default. Set false to turn the whole thing off.",
                  "On the FIRST world you join, the full list of what it does (and does not do)",
                  "is printed to chat once. It only reads state; it never modifies other mods",
                  "and never hides itself.")
                .push("selfProtection");
        SELF_PROTECTION = b.comment("Master switch.")
                .define("enabled", true);
        SELF_PROTECTION_ASKED = b.comment("Whether the one-time explanation has been shown.")
                .define("explained", false);
        b.pop();

        // ── Save snapshot ("rollback") ──
        // Handles coremods / agent-style external implementations that rewrite vanilla
        // methods such as hurt/setHealth/die/kill. Snapshots and restores [never] go through
        // those methods; they use a separate channel: periodic sampling plus direct writes to
        // the underlying fields.
        b.comment("=== Save snapshot (undo-death backup) ===").push("saveSnapshot");
        SAVE_SNAPSHOT = b.comment(
                "Master switch. Periodically snapshot player state to the world folder",
                "(_undying_backup/<uuid>.dat) and restore it when the player is killed",
                "by something that bypasses the normal damage chain.",
                "This layer deliberately does NOT go through hurt/setHealth/die/kill,",
                "so it still works against coremods that rewrite those methods.")
                .define("enabled", true);
        SAVE_SNAPSHOT_RESTORE = b.comment(
                "Auto-restore when health<=0 / dead / removed. Turn off to only keep backups.")
                .define("autoRestore", true);
        SAVE_SNAPSHOT_INTERVAL = b.comment(
                "Snapshot interval in ticks (20=1 second). Lower = safer, more disk IO.")
                .defineInRange("intervalTicks", 100, 20, 1200);
        b.pop();

        // ── Threat scan ──
        // The main mod only [reports]; it never takes action. Any handling is performed by the companion mod.
        b.comment("=== Threat scan (report only, no action) ===").push("threatScan");
        THREAT_SCAN = b.comment(
                "Master switch. When off, no scanning happens at all.")
                .define("enabled", false);
        THREAT_SCAN_BLACKLIST = b.comment(
                "Match loaded mods against the blacklist below.")
                .define("blacklist", true);
        THREAT_SCAN_NAME = b.comment(
                "Match loaded mods against suspicious name keywords.")
                .define("namePatterns", true);
        THREAT_SCAN_JAR = b.comment(
                "Inspect mod jar files (size / class-name heuristics).",
                "Slower on startup - disabled by default.")
                .define("jarHeuristics", false);
        THREAT_SCAN_BEHAVIOR = b.comment(
                "Behavioural heuristics (registry tampering, forced kills, etc).",
                "Experimental - disabled by default.")
                .define("behaviorAnalysis", false);
        BLACKLISTED_MODS = b.comment(
                "Mod ids considered hostile. Reported on world join.")
                .defineList("blacklistedMods", Arrays.asList(), o -> o instanceof String);
        b.pop();

        b.pop();
        SPEC = b.build();
    }

    /**
     * Check the blacklist at startup: report any loaded "isolated mods".
     *
     * Warning, added 2026-09-19: the original implementation only wrote, never read - the
     * ISOLATE logic recorded mods into blacklistedMods, but no code ever read that list on
     * the next startup, so the promise of a "next-startup warning" was empty. This adds the
     * reading side.
     */
    public static void warnBlacklistedMods() {
        try {
            List<String> list = new ArrayList<>();
            for (Object o : BLACKLISTED_MODS.get()) list.add(String.valueOf(o));
            if (list.isEmpty()) return;

            List<String> loaded = new ArrayList<>();
            for (net.minecraftforge.forgespi.language.IModInfo info
                    : net.minecraftforge.fml.ModList.get().getMods()) {
                if (list.contains(info.getModId())) loaded.add(info.getModId());
            }
            if (loaded.isEmpty()) return;

            UndyingMod.LOGGER.warn("[protection] blacklisted mods still loading at startup: {}", loaded);
        } catch (Throwable t) {
            ModLog.swallowed("ModConfig.warnBlacklistedMods", t);
        }
    }

    /** Persist the config to disk */
    public static void save() {
        try {
            net.minecraftforge.fml.config.ConfigTracker.INSTANCE.configSets()
                    .get(net.minecraftforge.fml.config.ModConfig.Type.COMMON).stream()
                    .filter(c -> c.getModId().equals(UndyingMod.MODID))
                    .findFirst()
                    .ifPresent(net.minecraftforge.fml.config.ModConfig::save);
        } catch (Exception t) {
            ModLog.swallowed("ModConfig.save", t);
        }
    }
}

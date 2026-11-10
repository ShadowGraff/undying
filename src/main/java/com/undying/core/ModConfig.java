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
    public static final ForgeConfigSpec.BooleanValue THREAT_SCAN_RESOURCE_OVERRIDE; // vanilla resource override scan, on by default
    public static final ForgeConfigSpec.BooleanValue THREAT_SCAN_ASKED;    // whether the one-time in-game prompt has been shown
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> BLACKLISTED_MODS;

    // ── Domain (repel mobs that are not on the allow-list) ──
    public static final ForgeConfigSpec.BooleanValue DOMAIN_ENABLED;
    public static final ForgeConfigSpec.IntValue DOMAIN_RANGE;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> DOMAIN_ALLOW_CATEGORIES;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> DOMAIN_ALLOW_ENTITIES;

    // ── Inventory guard (watch for inventory changes the player did not cause) ──
    public static final ForgeConfigSpec.BooleanValue INVENTORY_GUARD;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("undying");

        // 注释一律「英文在前，中文在后」。Forge 的 TOML 注释没有本地化机制，
        // 所以双语只能写死在注释里，而不是像 GUI 那样走语言文件。
        MASTER_TOGGLE = b.comment("Master toggle: off=normal, on=immortal",
                        "总开关：关闭=普通模式，开启=不朽模式")
                .define("masterToggle", true);
        DEATH_LOCK = b.comment("Prevent death", "防止死亡").define("deathLock", true);
        KILL_BLOCKER = b.comment("Block /kill command", "拦截 /kill 指令").define("killBlocker", true);
        GAMEMODE_LOCK = b.comment("Lock game mode", "锁定游戏模式").define("gameModeLock", true);
        CHEAT_PERMS = b.comment("Auto-grant OP", "自动授予 OP").define("cheatPerms", true);
        TOTEM_PROTECT = b.comment("Totem protection + invisible offhand",
                        "图腾保护 + 隐形副手")
                .define("totemProtect", true);
        VOID_RESCUE = b.comment("Void rescue teleport", "虚空救援传送").define("voidRescue", true);
        PERMA_EFFECTS = b.comment("Perma buffs (absorption / regeneration / resistance / fire resistance)",
                        "常驻增益（伤害吸收 / 生命恢复 / 抗性提升 / 抗火）")
                .define("permaEffects", true);
        HIDDEN_GEAR = b.comment("Auto-equip a second set of netherite gear into hidden slots",
                        "自动把一整套下界合金装备塞进隐藏槽位")
                .define("hiddenGear", true);
        DEATH_AURA = b.comment("Death aura instakill", "死亡光环：秒杀周围敌对生物")
                .define("deathAura", false);
        DAMAGE_BOOST = b.comment("Damage boost to Float.MAX", "伤害提升到 Float.MAX")
                .define("damageBoost", false);
        SPAWN_PROTECT = b.comment("Spawn protection: brief invulnerability after entering world",
                        "出生保护：进入世界后短暂无敌")
                .define("spawnProtect", true);
        SPAWN_PROTECT_TICKS = b.comment("Spawn protection duration in ticks (20=1 second)",
                        "出生保护时长（tick，20 = 1 秒）")
                .defineInRange("spawnProtectTicks", 120, 0, 6000);
        OP_LOCK = b.comment("OP4 lock: prevent OP from being revoked",
                        "OP4 锁定：防止 OP 被撤销")
                .define("opLock", true);
        ANTI_TAMPER = b.comment("Anti-tamper: block harmful effects / XP drain",
                        "反篡改：拦截有害状态效果与大额经验扣除")
                .define("antiTamper", true);
        INTEGRITY_GUARD = b.comment("Integrity guard: runtime self-check and auto-repair",
                        "完整性守卫：运行时自检与自动修复")
                .define("integrityGuard", true);

        // ── Self protection (on by default, can be turned off in the config) ──
        //    History: originally designed as an opt-in dialog (the player had to tick a box);
        //    the dialog was never implemented, and in practice the first-join chat notice
        //    proved clear enough, so it now defaults to enabled. On the first world entry it
        //    explains once, in chat, in full.
        b.comment("=== Self protection ===",
                  "自身保护",
                  "This mod protects ITSELF from being silently removed/disabled by other mods.",
                  "本模组保护「它自己」不被其他模组悄悄摘掉或关掉。",
                  "Enabled by default. Set false to turn the whole thing off.",
                  "默认开启。设为 false 可整块关闭。",
                  "On the FIRST world you join, the full list of what it does (and does not do)",
                  "is printed to chat once. It only reads state; it never modifies other mods",
                  "and never hides itself.",
                  "首次进入世界时，会在聊天框完整说明它做什么、不做什么，只说明一次。",
                  "它只读取状态，不修改其他模组，也从不隐藏自己。")
                .push("selfProtection");
        SELF_PROTECTION = b.comment("Master switch.", "总开关。")
                .define("enabled", true);
        SELF_PROTECTION_ASKED = b.comment("Whether the one-time explanation has been shown.",
                        "是否已经展示过那一次说明。")
                .define("explained", false);
        b.pop();

        // ── Save snapshot ("rollback") ──
        // Handles coremods / agent-style external implementations that rewrite vanilla
        // methods such as hurt/setHealth/die/kill. Snapshots and restores [never] go through
        // those methods; they use a separate channel: periodic sampling plus direct writes to
        // the underlying fields.
        b.comment("=== Save snapshot (undo-death backup) ===", "=== 状态回滚（死亡撤销备份）===")
                .push("saveSnapshot");
        SAVE_SNAPSHOT = b.comment(
                "Master switch. Periodically snapshot player state to the world folder",
                "总开关。定期把玩家状态快照到世界目录",
                "(_undying_backup/<uuid>.dat) and restore it when the player is killed",
                "（_undying_backup/<uuid>.dat），并在玩家被绕过伤害链的方式杀死时还原。",
                "by something that bypasses the normal damage chain.",
                "This layer deliberately does NOT go through hurt/setHealth/die/kill,",
                "这一层刻意不经过 hurt/setHealth/die/kill，",
                "so it still works against coremods that rewrite those methods.",
                "所以即使有 coremod 重写了这些方法，它照样生效。")
                .define("enabled", true);
        SAVE_SNAPSHOT_RESTORE = b.comment(
                "Auto-restore when health<=0 / dead / removed. Turn off to only keep backups.",
                "血量 <=0 / 已死亡 / 已被移除时自动还原。关闭则只保留备份、不自动还原。")
                .define("autoRestore", true);
        SAVE_SNAPSHOT_INTERVAL = b.comment(
                "Snapshot interval in ticks (20=1 second). Lower = safer, more disk IO.",
                "快照间隔（tick，20 = 1 秒）。越小越安全，磁盘写入越多。")
                .defineInRange("intervalTicks", 100, 20, 1200);
        b.pop();

        // ── Threat scan ──
        // The main mod only [reports]; it never takes action. Any handling is performed by the companion mod.
        b.comment("=== Threat scan (report only, no action) ===",
                        "=== 威胁扫描（只报告，不处置）===")
                .push("threatScan");
        THREAT_SCAN = b.comment(
                "Master switch. When off, no scanning happens at all.",
                "总开关。关闭时完全不扫描。")
                .define("enabled", false);
        THREAT_SCAN_BLACKLIST = b.comment(
                "Match loaded mods against the blacklist below.",
                "把已加载模组与下面的黑名单比对。")
                .define("blacklist", true);
        THREAT_SCAN_NAME = b.comment(
                "Match loaded mods against suspicious name keywords.",
                "把已加载模组与可疑名称关键词比对。")
                .define("namePatterns", true);
        THREAT_SCAN_JAR = b.comment(
                "Inspect mod jar files (size / class-name heuristics).",
                "Slower on startup - disabled by default.",
                "检查模组 jar 文件（体积 / 类名启发式）。启动更慢，默认关闭。")
                .define("jarHeuristics", false);
        THREAT_SCAN_BEHAVIOR = b.comment(
                "Behavioural heuristics (registry tampering, forced kills, etc).",
                "Experimental - disabled by default.",
                "行为启发式（篡改注册表、强制击杀等）。实验性，默认关闭。")
                .define("behaviorAnalysis", false);
        THREAT_SCAN_RESOURCE_OVERRIDE = b.comment(
                "Detect mod jars that ship resources under assets/minecraft/, i.e. mods that",
                "检测 jar 内带有 assets/minecraft/ 资源的模组，也就是那些",
                "REPLACE vanilla sounds / gui textures / shaders rather than adding new ones.",
                "替换（而非新增）原版音效 / GUI 贴图 / 着色器的模组。",
                "Aggregated per mod by category. Read-only zip scan; no file is modified.",
                "按模组聚合、按类别分类。只读 zip 扫描，不修改任何文件。")
                .define("resourceOverrides", true);
        THREAT_SCAN_ASKED = b.comment(
                "Whether the player has already been asked, in game, whether to enable the scan.",
                "Set automatically; do not edit unless you want the prompt to appear again.",
                "是否已在游戏内询问过玩家要不要启用扫描。自动写入；",
                "除非你想让弹窗再出现一次，否则不要手改。")
                .define("asked", false);
        BLACKLISTED_MODS = b.comment(
                "Mod ids considered hostile. Reported on world join.",
                "被视为敌对的模组 id，进入世界时报告。")
                .defineList("blacklistedMods", Arrays.asList(), o -> o instanceof String);
        b.pop();

        // ── Domain ──
        b.comment("=== Domain (mobs outside the allow-list cannot approach) ===",
                        "=== 领域（不在放行名单内的生物无法靠近）===")
                .push("domain");
        DOMAIN_ENABLED = b.comment(
                "Master switch. Mobs are pushed away, never killed: nothing dies and no drops appear.",
                "总开关。生物只会被推开，永远不会被杀死：不掉血、不掉落。",
                "Runs alongside the aura, which is the one that clears hostile mobs.",
                "与死亡光环并存，清除敌对生物是光环负责的事。")
                .define("enabled", false);
        DOMAIN_RANGE = b.comment("Radius in blocks. Anything closer than this is pushed out.",
                        "半径（格）。比这更近的一律被推开。")
                .defineInRange("range", 8, 1, 32);
        DOMAIN_ALLOW_CATEGORIES = b.comment(
                "Mob categories that may approach. Defaults are the vanilla friendly ones.",
                "允许靠近的生物类别。默认是原版的友好类别。",
                "MONSTER is absent on purpose: it also covers neutral mobs such as endermen.",
                "MONSTER 是有意不在默认值里的：它同时包含末影人这类中立生物。")
                .defineList("allowedCategories",
                        new ArrayList<>(DomainFilter.DEFAULT_ALLOWED_CATEGORIES),
                        o -> o instanceof String);
        DOMAIN_ALLOW_ENTITIES = b.comment(
                "Exact entity ids allowed as well, e.g. minecraft:cow. Case-insensitive.",
                "额外放行的具体实体 id，例如 minecraft:cow。不区分大小写。")
                .defineList("allowedEntities", new ArrayList<>(), o -> o instanceof String);
        b.pop();

        // ── Inventory guard ──
        b.comment("=== Inventory guard (ask before accepting a suspicious bulk change) ===",
                        "=== 物品栏快照（发现可疑的大额变动时先问一句）===")
                .push("inventoryGuard");
        INVENTORY_GUARD = b.comment(
                "Watches the inventory for bulk changes with no visible cause (no open container,",
                "监视物品栏：一次 5 格以上变动、且看不到合理原因（没开容器、",
                "no drop, no pickup) and asks whether to roll them back. Nothing is blocked:",
                "没丢弃、没拾取）时，询问是否回滚。它什么都不拦——",
                "the change is accepted first, and only reverted if the player says so.",
                "变动先被接受，只有玩家确认才还原。",
                "An unanswered prompt does nothing.",
                "不作答就等于什么都不做。")
                .define("enabled", true);
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

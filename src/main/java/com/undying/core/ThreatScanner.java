package com.undying.core;

import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.jar.JarFile;

/**
 * Mod scan: a coarse check for "possibly malicious mods".
 *
 * ⚠️ Scope (after the 2026-09-20 architecture change):
 *   In this step the main mod <b>only reports</b> and takes no action.
 *   Handling actions (quarantine, cleanup) are performed by a separate companion mod.
 *   This keeps responsibilities clean: main mod = detect, companion = act.
 *
 * Four detection dimensions, each individually switchable in the config file
 * (the master switch defaults to off):
 *   ① Blacklist      — exact modid match against blacklistedMods
 *   ② Name pattern   — modid / display name hits a suspicious keyword
 *   ③ jar features   — scans the jar file (size, class-name patterns); slower, off by default
 *   ④ Behavior analysis — runtime marking of suspicious behavior; experimental, off by default
 *   ⑤ Resource override — jar ships assets/minecraft/..., i.e. it replaces vanilla content
 *
 * Scan timing: once after mod loading completes (the result is cached), announced when a
 * player enters the world. It is not announced at startup because no player is online then
 * and nobody would see the message.
 *
 * Honest limits: these are all <b>heuristic</b> judgments and will produce false positives.
 * A hit does not mean malicious, only "worth noting". The real decision always rests with
 * the player.
 */
public final class ThreatScanner {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Suspicious name keywords (matched in lower case). A hit only marks the mod; no
     *  automatic action is taken. */
    private static final String[] SUSPICIOUS_KEYWORDS = {
            "error422", "err422", "malware", "virus", "trojan", "backdoor",
            "cheat", "hack", "exploit", "parasite", "spore", "grief",
            "wipe", "destroyer", "corrupt", "ransom",
    };

    /** A single finding */
    public record Finding(String modId, String displayName, String dimension, String detail) {
        public String describe() {
            return displayName + " (" + modId + ") — " + dimension + ": " + detail;
        }
    }

    /**
     * Scan result (cached in-process).
     *
     * <p>Copy-on-write because the in-game prompt can trigger a rescan from the client
     * thread while the server thread is walking this list to report it. A plain ArrayList
     * would throw ConcurrentModificationException at exactly the wrong moment.
     */
    private static final List<Finding> FINDINGS = new java.util.concurrent.CopyOnWriteArrayList<>();
    private static volatile boolean scanned = false;

    private ThreatScanner() {}

    // ═══════════ Scan entry point ═══════════

    /**
     * Called once after mod loading completes. Idempotent — repeated calls only scan the first time.
     * Every exception is swallowed: the scanner must never affect game startup.
     */
    public static void scanOnce() {
        if (scanned) return;
        scanned = true;

        if (!ModConfig.THREAT_SCAN.get()) {
            LOGGER.debug("[mod-scan] master switch off, skipped");
            return;
        }

        try {
            long t0 = System.currentTimeMillis();
            for (var info : net.minecraftforge.fml.ModList.get().getMods()) {
                String id = info.getModId();
                String name = info.getDisplayName();

                if (ModConfig.THREAT_SCAN_BLACKLIST.get()) checkBlacklist(id, name);
                if (ModConfig.THREAT_SCAN_NAME.get())      checkNamePattern(id, name);
                if (ModConfig.THREAT_SCAN_JAR.get())       checkJar(id, name, info);
                if (ModConfig.THREAT_SCAN_RESOURCE_OVERRIDE.get()) checkResourceOverride(id, name, info);
            }
            long cost = System.currentTimeMillis() - t0;

            if (FINDINGS.isEmpty()) {
                LOGGER.info("[mod-scan] done, nothing suspicious ({} ms)", cost);
            } else {
                LOGGER.warn("[mod-scan] done, {} suspicious item(s) ({} ms)", FINDINGS.size(), cost);
                for (Finding f : FINDINGS) LOGGER.warn("[mod-scan]   {}", f.describe());
            }
        } catch (Throwable t) {
            LOGGER.warn("[mod-scan] scan error (ignored): {}", t.toString());
        }
    }

    /**
     * Re-run the scan after the switch was turned on at runtime.
     *
     * <p>Without this the scan would only ever run at startup: a player who answers "yes" to
     * the in-game prompt would see no report until the next launch, which reads exactly like
     * a broken feature. Cost is one pass over the mod jars' central directories — tens of
     * milliseconds, once, on an explicit user action.
     */
    public static void rescan() {
        FINDINGS.clear();
        scanned = false;
        vanillaTried = false;   // the vanilla list may have been unavailable before; retry once
        scanOnce();
    }

    // ═══════════ The four dimensions ═══════════

    /** ① Exact blacklist match */
    private static void checkBlacklist(String id, String name) {
        try {
            for (Object o : ModConfig.BLACKLISTED_MODS.get()) {
                if (String.valueOf(o).equalsIgnoreCase(id)) {
                    add(new Finding(id, name, "blacklist", "modid is in the configured blacklist"));
                    return;
                }
            }
        } catch (Throwable t) {
            ModLog.swallowed("ThreatScanner.checkBlacklist", t);
        }
    }

    /** ② Name pattern match (both modid and display name are checked) */
    private static void checkNamePattern(String id, String name) {
        String hay = (id + " " + name).toLowerCase(Locale.ROOT);
        for (String kw : SUSPICIOUS_KEYWORDS) {
            if (hay.contains(kw)) {
                add(new Finding(id, name, "name-pattern", "matched keyword \"" + kw + "\""));
                return;
            }
        }
    }

    /**
     * Resolve a mod's jar to a real file on disk.
     *
     * <p>Forge 1.20.1 mounts every mod through a {@code SecureJar} on an in-memory file
     * system, so {@code getFilePath()} is a jimfs path and {@code Path.toFile()} throws
     * {@code UnsupportedOperationException: Path not associated with default file system}.
     * That is exactly how the first version of the resource scan failed on <i>every single
     * mod</i> while its unit tests passed — the tests used real files.
     *
     * <p>Two attempts, cheapest first. Null means "skip this mod", never "no findings".
     */
    private static Path resolveRealJar(net.minecraftforge.forgespi.language.IModInfo info) {
        try {
            Path p = info.getOwningFile().getFile().getFilePath();
            // isRegularFile() alone is not enough: it answers correctly for jimfs paths
            // too, so a SecureJar path would pass this test and only explode later in
            // Path.toFile(). The file system identity is the actual question.
            if (p != null
                    && p.getFileSystem() == java.nio.file.FileSystems.getDefault()
                    && Files.isRegularFile(p)) return p;
        } catch (Throwable t) {
            ModLog.swallowed("ThreatScanner.resolveRealJar.direct", t);
        }
        try {
            String fileName = info.getOwningFile().getFile().getFileName();
            if (fileName != null && !fileName.isEmpty()) {
                Path p = net.minecraftforge.fml.loading.FMLPaths.MODSDIR.get().resolve(fileName);
                if (Files.isRegularFile(p)) return p;
            }
        } catch (Throwable t) {
            ModLog.swallowed("ThreatScanner.resolveRealJar.modsdir", t);
        }
        return null;
    }

    /** ③ jar features: abnormal size + suspicious class names */
    private static void checkJar(String id, String name, net.minecraftforge.forgespi.language.IModInfo info) {
        try {
            Path jar = resolveRealJar(info);
            if (jar == null) return;

            long size = Files.size(jar);
            // Abnormal size: extremely small (< 1 KB, possibly a stub) or extremely large
            // (> 200 MB, unusual)
            if (size < 1024) {
                add(new Finding(id, name, "jar-heuristic", "unusually small jar (" + size + " bytes)"));
                return;
            }
            if (size > 200L * 1024 * 1024) {
                add(new Finding(id, name, "jar-heuristic", "unusually large jar (" + (size / 1024 / 1024) + " MB)"));
                return;
            }

            // Suspicious class names: scan jar entry names for suspicious keywords
            try (JarFile jf = new JarFile(jar.toFile())) {
                var e = jf.entries();
                int inspected = 0;
                while (e.hasMoreElements() && inspected < 2000) {
                    String entry = e.nextElement().getName().toLowerCase(Locale.ROOT);
                    inspected++;
                    for (String kw : SUSPICIOUS_KEYWORDS) {
                        if (entry.contains(kw)) {
                            add(new Finding(id, name, "jar-heuristic", "jar contains suspicious entry \"" + entry + "\""));
                            return;
                        }
                    }
                }
            }
        } catch (Throwable t) {
            ModLog.swallowed("ThreatScanner.checkJar", t);
        }
    }

    /**
     * ⑤ Vanilla resource override: the jar ships files under {@code assets/minecraft/}.
     *
     * <p>This is the dimension that catches resource-pack scares. A mod that wants to replace
     * vanilla sounds or gui textures never touches a rendering or audio API — it ships the
     * files and lets the mod resource pack outrank vanilla, so watching the render pipeline
     * would never see it.
     */
    private static void checkResourceOverride(String id, String name,
                                              net.minecraftforge.forgespi.language.IModInfo info) {
        // Vanilla and Forge legitimately own that namespace. Reporting them would bury every
        // real finding under thousands of vanilla entries.
        if ("minecraft".equals(id) || "forge".equals(id) || UndyingMod.MODID.equals(id)) return;
        try {
            Path jar = resolveRealJar(info);
            if (jar == null) return;

            Set<String> declared = ResourceOverrides.readDeclaredPaths(jar);
            if (declared.isEmpty()) return;

            Set<String> vanilla = vanillaPaths();
            if (vanilla == null) {
                // Degraded, and said so out loud: without the vanilla list we know what the
                // jar DECLARES, not what it actually replaces. Reporting a declaration as an
                // override would accuse mods that merely add content.
                add(new Finding(id, name, "resource-override",
                        ResourceOverrides.summarizeUnverified(declared)));
                return;
            }
            Set<String> actual = ResourceOverrides.actualOverrides(declared, vanilla);
            if (actual.isEmpty()) return;
            add(new Finding(id, name, "resource-override", ResourceOverrides.summarize(actual)));
        } catch (Throwable t) {
            ModLog.swallowed("ThreatScanner.checkResourceOverride", t);
        }
    }

    /** Vanilla resource paths, resolved once per process; null = "could not be determined". */
    private static Set<String> VANILLA_PATHS;
    private static boolean vanillaTried = false;

    /** Smallest plausible size of the real vanilla resource tree. Below this we did not find it. */
    private static final int VANILLA_LIST_MIN_PLAUSIBLE = 1000;

    /**
     * Locate the vanilla jar through a class that exists on BOTH sides.
     *
     * <p>Naming {@code net.minecraft.client.Minecraft} here would make this class fail to link
     * on a dedicated server; {@code net.minecraft.world.level.Level} is present in either
     * distribution, so the lookup is safe wherever the scan runs.
     */
    private static Set<String> vanillaPaths() {
        if (vanillaTried) return VANILLA_PATHS;
        vanillaTried = true;
        try {
            var src = net.minecraft.world.level.Level.class.getProtectionDomain().getCodeSource();
            if (src == null) return null;
            Path jar = Path.of(src.getLocation().toURI());
            // A classes directory (development runs) is not a resource jar: treat it as
            // "unknown" rather than as an empty vanilla, which would silently hide everything.
            if (!Files.isRegularFile(jar)) return null;

            Set<String> found = ResourceOverrides.readDeclaredPaths(jar);
            if (found.size() < VANILLA_LIST_MIN_PLAUSIBLE) {
                // A wrong or partial jar would make every intersection empty, i.e. the whole
                // dimension would report "nothing overridden" and look healthy. Fail loudly.
                LOGGER.warn("[mod-scan] vanilla resource list looks wrong ({} entries from {}); "
                        + "falling back to unverified declarations", found.size(), jar);
                return null;
            }
            VANILLA_PATHS = found;
            LOGGER.info("[mod-scan] vanilla resource list: {} entries", found.size());
        } catch (Throwable t) {
            ModLog.swallowed("ThreatScanner.vanillaPaths", t);
            VANILLA_PATHS = null;
        }
        return VANILLA_PATHS;
    }

    // ═══════════ Results ═══════════

    private static void add(Finding f) {
        for (Finding old : FINDINGS) {
            if (old.modId().equals(f.modId()) && old.dimension().equals(f.dimension())) return;
        }
        FINDINGS.add(f);
    }

    /**
     * Announces the scan result when a player enters the world.
     * Announced only once and only when there are findings, to avoid spamming (a set is used
     * to deduplicate down to "once per player").
     */
    public static void announceTo(ServerPlayer p) {
        if (p == null || FINDINGS.isEmpty()) return;
        PlayerState s = PlayerState.of(p);
        if (s.announced) return;
        s.announced = true;

        // Detail goes to the report file; the chat box gets one line, the mod ids, and a link.
        StringBuilder block = new StringBuilder("[mod-scan] ").append(FINDINGS.size()).append(" finding(s):");
        for (Finding f : FINDINGS) block.append("\n  · ").append(f.describe());
        ReportLog.writeBlock(block.toString());

        java.util.StringJoiner names = new java.util.StringJoiner(", ");
        for (Finding f : FINDINGS) names.add(f.modId());

        Msg.info(p, "mod-scan")
                .bi("发现 " + FINDINGS.size() + " 个值得注意的模组",
                        "detected " + FINDINGS.size() + " mod(s) worth noting")
                .link("[打开报告 / Report]", "/undying report").send();
        Msg.plain(p, null).text("  ").text(names.toString()).send();
        Msg.plain(p, null)
                .bi("  （仅为启发式线索，不代表恶意）",
                        "  (heuristic hints only; not proof of malicious intent)").send();
    }

}

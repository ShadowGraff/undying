package com.undying.core;

import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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

    /** Scan result (cached in-process) */
    private static final List<Finding> FINDINGS = new ArrayList<>();
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

    /** ③ jar features: abnormal size + suspicious class names */
    private static void checkJar(String id, String name, net.minecraftforge.forgespi.language.IModInfo info) {
        try {
            Path jar = info.getOwningFile().getFile().getFilePath();
            if (jar == null || !Files.exists(jar)) return;

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

        Msg.info(p, "mod-scan").text("detected ").num(FINDINGS.size())
                .text(" mod(s) worth noting:").send();
        for (Finding f : FINDINGS) {
            Msg.plain(p, null).text("  · ").text(f.describe()).send();
        }
        Msg.plain(p, null).text("  (heuristic hints only; not proof of malicious intent)").send();
    }

}

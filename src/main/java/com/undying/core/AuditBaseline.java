package com.undying.core;

import net.minecraftforge.forgespi.language.IModFileInfo;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reading and writing of the baseline file + runtime environment fingerprint.
 *
 * <p>Why it is its own class: this is about the <i>file format</i>, which is a
 * different concern from "how structure fingerprints are compared". Mixing them
 * together would mean "changing one line of file format requires trawling three
 * hundred lines of audit logic".
 *
 * <p><b>Why the baseline carries an environment fingerprint</b>: the numbers in the
 * fingerprint were counted while "our own mixins were also in there". Upgrading the
 * mod itself, or a player adding/removing other mods, naturally changes those
 * numbers, yet it would be treated as "someone stripped our methods" → crying wolf on
 * every update. By recording {@code <this mod's jar hash> <hash of the loaded mod
 * list>}, either of them changing triggers a quiet rebuild.
 *
 *
 * <p><b>File format</b> (plain text; players can delete it themselves to reset):
 * <pre>
 *   #env &lt;jar hash&gt; &lt;mod list hash&gt;
 *   net.minecraft.world.entity.LivingEntity 340 120
 *   net.minecraft.world.entity.Entity 510 138
 *   ...
 * </pre>
 */
public final class AuditBaseline {

    private AuditBaseline() {}

    /** Baseline file location (same level as the config file, so players can delete it themselves to reset) */
    private static final String FILE_NAME = "undying_classbaseline.txt";

    /** Prefix of the environment fingerprint line */
    private static final String ENV_PREFIX = "#env ";

    /** Baseline on disk: environment fingerprint + [method count, field count] per class */
    public record Snapshot(String env, Map<String, int[]> counts) {
        public boolean empty() {
            return counts.isEmpty();
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Runtime environment fingerprint
    // ═══════════════════════════════════════════════════════════

    /** Hash of this mod's jar (computed once, then cached) */
    private static volatile String cachedJarHash;

    /** This mod's jar hash + the loaded mod list hash, joined into one environment fingerprint line */
    public static String envSignature() {
        return ourJarHash() + " " + modSetHash();
    }

    private static String ourJarHash() {
        String v = cachedJarHash;
        if (v != null) return v;
        v = "unknown";
        try {
            IModFileInfo mi = ModList.get().getModFileById(UndyingMod.MODID);
            if (mi != null) {
                java.nio.file.Path p = mi.getFile().getFilePath();
                v = shortHash(Files.readAllBytes(p));
            }
        } catch (Throwable t) {
            ModLog.swallowed("AuditBaseline.ourJarHash", t);
        }
        cachedJarHash = v;
        return v;
    }

    /** Hash of the loaded mod list: changes whenever any mod is added or removed */
    private static String modSetHash() {
        try {
            List<String> ids = new ArrayList<>();
            for (var info : ModList.get().getMods()) ids.add(info.getModId());
            Collections.sort(ids);
            return shortHash(String.join(",", ids).getBytes(StandardCharsets.UTF_8));
        } catch (Throwable t) {
            ModLog.swallowed("AuditBaseline.modSetHash", t);
            return "unknown";
        }
    }

    /** First 8 bytes of SHA-256: sufficient and short */
    private static String shortHash(byte[] data) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(data);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 8; i++) sb.append(String.format("%02x", d[i]));
            return sb.toString();
        } catch (Throwable t) {
            ModLog.swallowed("AuditBaseline.shortHash", t);
            return "unknown";
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Reading and writing
    // ═══════════════════════════════════════════════════════════

    private static File baselineFile() {
        try {
            return FMLPaths.CONFIGDIR.get().resolve(FILE_NAME).toFile();
        } catch (Throwable t) {
            ModLog.swallowed("AuditBaseline.baselineFile", t);
            return new File(FILE_NAME);
        }
    }

    public static Snapshot load() {
        Map<String, int[]> out = new LinkedHashMap<>();
        String env = "";
        try {
            File f = baselineFile();
            if (!f.exists()) return new Snapshot("", out);
            for (String line : Files.readAllLines(f.toPath(), StandardCharsets.UTF_8)) {
                String s = line.trim();
                if (s.isEmpty()) continue;
                if (s.startsWith(ENV_PREFIX)) {
                    env = s.substring(ENV_PREFIX.length()).trim();
                    continue;
                }
                if (s.startsWith("#")) continue;
                // Format: class name method count field count
                String[] p = s.split("\\s+");
                if (p.length == 3) {
                    out.put(p[0], new int[]{Integer.parseInt(p[1]), Integer.parseInt(p[2])});
                }
            }
        } catch (Throwable t) {
            ModLog.swallowed("AuditBaseline.load", t);
        }
        return new Snapshot(env, out);
    }

    public static void save(Map<String, int[]> counts, String env) {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append(ENV_PREFIX).append(env).append('\n');
            for (Map.Entry<String, int[]> e : counts.entrySet()) {
                sb.append(e.getKey()).append(' ')
                  .append(e.getValue()[0]).append(' ')
                  .append(e.getValue()[1]).append('\n');
            }
            Files.write(baselineFile().toPath(),
                    sb.toString().getBytes(StandardCharsets.UTF_8));
        } catch (Throwable t) {
            ModLog.swallowed("AuditBaseline.save", t);
        }
    }
}

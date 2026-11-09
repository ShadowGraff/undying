package com.undying.core;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.TreeSet;
import java.util.jar.JarFile;

/**
 * Finds mod jars that ship resources under {@code assets/minecraft/}.
 *
 * <p>Why this dimension exists: a mod that wants to replace vanilla textures, sounds or
 * post-processing shaders does not need to touch a single rendering or audio API. It only
 * has to put files under {@code assets/minecraft/} in its own jar, and Forge's mod resource
 * pack outranks the vanilla one. Intercepting at render time therefore misses the whole
 * class of attack — the resource was already swapped at load time.
 *
 * <p><b>Two different facts, never conflated</b>:
 * <ul>
 *   <li><b>declared</b> — the jar contains paths below {@code assets/minecraft/};</li>
 *   <li><b>overridden</b> — those paths also exist in vanilla, so vanilla content really
 *       is being replaced rather than merely extended.</li>
 * </ul>
 * Reporting the first as if it were the second would accuse ordinary mods that only add
 * namespaced content of tampering, so the caller is expected to pass the vanilla path set
 * whenever it can be obtained, and to say so plainly when it cannot.
 *
 * <p>Everything here is pure path logic plus reading zip central directories. No Minecraft
 * or Forge type is referenced, which is what lets the unit tests run without the game.
 */
public final class ResourceOverrides {

    /** Prefix that makes a jar entry a claim on vanilla's namespace. */
    public static final String PREFIX = "assets/minecraft/";

    /** Reporting order: the categories that most often carry a scare come first. */
    private static final String[] CATEGORY_ORDER = {
            "sounds", "shaders", "textures/gui", "textures", "models", "other"
    };

    private ResourceOverrides() {}

    /**
     * Read the resource paths a jar declares below {@code assets/minecraft/}.
     *
     * <p>Only the zip central directory is read: nothing is decompressed, no class is loaded,
     * and a jar of any size costs about the same. Directory entries are skipped because they
     * name folders, not content.
     *
     * @throws IOException when the file is not a readable zip — deliberately propagated so
     *         the caller decides how to degrade, instead of a corrupt jar being reported as
     *         "this mod overrides nothing".
     */
    public static Set<String> readDeclaredPaths(Path jar) throws IOException {
        Set<String> out = new HashSet<>();
        try (JarFile jf = new JarFile(jar.toFile())) {
            var entries = jf.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                if (entry.isDirectory()) continue;
                String name = entry.getName();
                if (name.startsWith(PREFIX)) out.add(name);
            }
        }
        return out;
    }

    /** Keep only the declared paths that also exist in vanilla: declared &ne; overridden. */
    public static Set<String> actualOverrides(Collection<String> declared, Set<String> vanillaPaths) {
        Set<String> out = new TreeSet<>();
        for (String p : declared) {
            if (vanillaPaths.contains(p)) out.add(p);
        }
        return out;
    }

    /**
     * Bucket a path for reporting.
     *
     * <p>The categories are about <i>presentation</i>, not about suspicion: sounds, shaders
     * and gui textures are the paths a jumpscare is usually built from, while models and
     * blockstates are everyday modding. The report lists them, it does not judge them.
     */
    public static String categoryOf(String path) {
        String p = path.startsWith(PREFIX) ? path.substring(PREFIX.length()) : path;
        if (p.equals("sounds.json") || p.startsWith("sounds/")) return "sounds";
        if (p.startsWith("shaders/")) return "shaders";
        if (p.startsWith("textures/gui/")) return "textures/gui";
        if (p.startsWith("textures/")) return "textures";
        if (p.startsWith("models/") || p.startsWith("blockstates/")) return "models";
        return "other";
    }

    /** Per-category counts, zero-count buckets removed, in {@link #CATEGORY_ORDER}. */
    public static Map<String, Integer> categorize(Collection<String> paths) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String c : CATEGORY_ORDER) counts.put(c, 0);
        for (String p : paths) counts.merge(categoryOf(p), 1, Integer::sum);
        counts.values().removeIf(v -> v == 0);
        return counts;
    }

    /**
     * One line for the chat report, e.g.
     * {@code "37 vanilla resource(s) overridden (sounds x21, textures/gui x12, shaders x4)"}.
     * Empty input yields an empty string so the caller can skip reporting entirely.
     */
    public static String summarize(Collection<String> paths) {
        if (paths.isEmpty()) return "";
        StringBuilder sb = new StringBuilder()
                .append(paths.size()).append(" vanilla resource(s) overridden");
        Map<String, Integer> counts = categorize(paths);
        if (!counts.isEmpty()) {
            StringJoiner join = new StringJoiner(", ", " (", ")");
            counts.forEach((k, v) -> join.add(k + " x" + v));
            sb.append(join);
        }
        return sb.toString();
    }

    /** Summary for the degraded case, where no vanilla list was available to compare against. */
    public static String summarizeUnverified(Collection<String> declared) {
        return declared.size() + " resource(s) declared under " + PREFIX
                + " (vanilla list unavailable: not verified as actual overrides)";
    }
}

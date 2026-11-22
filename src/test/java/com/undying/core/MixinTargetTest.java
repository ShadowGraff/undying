package com.undying.core;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Every mixin method name must exist in the mapping the game will actually run against.
 *
 * <h3>The failure this prevents</h3>
 * Mixins here are written with SRG names and {@code remap = false}: no annotation processor, no
 * refmap. That means a wrong name is not a compile error — it is a crash at class load, or worse,
 * a silently absent hook. Two of the features in this project were measured to be inactive for a
 * whole round because of exactly that, and the discovery came from a player noticing behaviour
 * that did not match the README.
 *
 * <p>The mapping file that ForgeGradle generates ({@code build/createMcpToSrg/output.tsrg}) is the
 * same one reobfuscation uses. Checking our names against it is a static question with a static
 * answer, and it can run on every build instead of waiting for someone to launch the game.
 *
 * <h3>What it does not prove</h3>
 * That the method has the parameters we assume, or that the injection point exists. It proves the
 * name is real — which is the mistake that is both most common and most invisible.
 */
class MixinTargetTest {

    private static final Path MAIN = Path.of("src", "main", "java", "com", "undying", "core");
    private static final Path TSRG = Path.of("build", "createMcpToSrg", "output.tsrg");

    /** e.g. {@code @Mixin(ServerPlayer.class)} */
    private static final Pattern MIXIN_TARGET =
            Pattern.compile("@Mixin\\(\\s*([A-Za-z0-9_.]+)\\.class");

    /** e.g. {@code method = "m_6074_"} */
    private static final Pattern METHOD_NAME =
            Pattern.compile("method\\s*=\\s*\"([a-zA-Z0-9_$]+)\"");

    /** Official class path → the SRG method names declared in it. */
    private static Map<String, Set<String>> parseTsrg(List<String> lines) {
        Map<String, Set<String>> byClass = new HashMap<>();
        Set<String> current = null;
        for (String line : lines) {
            if (line.isEmpty()) continue;

            if (!line.startsWith("\t")) {
                // Class header: "official/path srg/path"
                String official = line.split(" ", 2)[0];
                current = new HashSet<>();
                byClass.put(official, current);
                continue;
            }
            if (current == null) continue;

            // Member line: "\tname (desc) srgName" or "\tname srgName"; fields have no parens.
            String[] parts = line.strip().split("\\s+");
            if (parts.length >= 2 && !parts[0].startsWith("<")) {
                current.add(parts[parts.length - 1]);
            }
        }
        return byClass;
    }

    /** Find the section for a simple class name (e.g. ServerPlayer → .../server/level/ServerPlayer). */
    private static Set<String> sectionFor(Map<String, Set<String>> byClass, String simpleName) {
        for (var e : byClass.entrySet()) {
            String path = e.getKey();
            if (path.endsWith("/" + simpleName)) return e.getValue();
        }
        return null;
    }

    @Test
    void everyMixinMethodNameExistsInTheMapping() throws IOException {
        assumeTrue(Files.isRegularFile(TSRG), "mapping not generated yet; skipped");
        assumeTrue(Files.isDirectory(MAIN), "source tree not found; run from the project directory");

        Map<String, Set<String>> byClass = parseTsrg(Files.readAllLines(TSRG, StandardCharsets.UTF_8));

        List<Path> mixins;
        try (Stream<Path> walk = Files.walk(MAIN.resolve("mixin"))) {
            mixins = walk.filter(p -> p.toString().endsWith(".java")).toList();
        }

        List<String> problems = new ArrayList<>();

        for (Path file : mixins) {
            String src = Files.readString(file, StandardCharsets.UTF_8);
            Matcher tm = MIXIN_TARGET.matcher(src);
            if (!tm.find()) continue;                       // accessor interfaces etc.

            String target = tm.group(1);
            String simple = target.substring(target.lastIndexOf('.') + 1);
            Set<String> methods = sectionFor(byClass, simple);
            if (methods == null) {
                problems.add(file.getFileName() + ": target class not in mapping -> " + target);
                continue;
            }

            Matcher mm = METHOD_NAME.matcher(src);
            while (mm.find()) {
                String name = mm.group(1);
                if (name.startsWith("m_") && !methods.contains(name)) {
                    problems.add(file.getFileName() + ": " + simple + " has no method " + name);
                }
            }
        }

        assertTrue(problems.isEmpty(),
                "mixin names that the runtime will not find: " + problems);
    }
}

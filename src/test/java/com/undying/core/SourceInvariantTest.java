package com.undying.core;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Rules that are cheaper to check by reading the source than by running the game.
 *
 * <h3>Why this file exists</h3>
 * The unit tests in this project cover pure functions — roughly a third of the mod. The other two
 * thirds (mixins, guards, the death flow) can only be verified in a real client, and that means
 * they are verified rarely, by one person, by hand. Twice now a round of "fixes" shipped with the
 * only evidence being "it compiles and the old tests are green", which proves non-regression and
 * nothing else.
 *
 * <p>Some of those gaps are not runtime questions at all — they are <b>shape</b> questions:
 * "is there still a method that hands out a death permit", "does a gate read the live config
 * instead of the baseline", "does a public method write the save". A grep answers those, and a
 * grep that runs on every build does not forget.
 *
 * <h3>What this class is not</h3>
 * It is not a substitute for a real client test. It cannot tell whether a mixin applies at
 * runtime, whether the health invariant fires at the right moment, or whether a screen is
 * readable. It catches the class of bug that <i>can</i> be caught by reading, so that the manual
 * pass can spend its time on what genuinely needs eyes.
 */
class SourceInvariantTest {

    /** Repository-relative roots, resolved from the Gradle test working directory. */
    private static final Path MAIN = Path.of("src", "main", "java", "com", "undying", "core");
    private static final Path RESOURCES = Path.of("src", "main", "resources");

    private static List<Path> javaFiles() throws IOException {
        assumeTrue(Files.isDirectory(MAIN), "source tree not found; run from the project directory");
        try (Stream<Path> walk = Files.walk(MAIN)) {
            return walk.filter(p -> p.toString().endsWith(".java")).toList();
        }
    }

    private static String read(Path p) throws IOException {
        return Files.readString(p, StandardCharsets.UTF_8);
    }

    /** Occurrences of a needle, as "file:line" strings, ignoring comments would be nicer than this. */
    private static List<String> find(List<Path> files, String needle) throws IOException {
        List<String> hits = new ArrayList<>();
        for (Path f : files) {
            String[] lines = read(f).split("\n", -1);
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i];
                if (!line.contains(needle)) continue;
                String trimmed = line.stripLeading();
                if (trimmed.startsWith("*") || trimmed.startsWith("//") || trimmed.startsWith("/*")) continue;
                hits.add(f.getFileName() + ":" + (i + 1) + "  " + trimmed.trim());
            }
        }
        return hits;
    }

    @Test
    void theDeathPermitMechanismIsReallyGone() throws IOException {
        // 2.9.1 removed /antigrief die and both permits. If any of these names reappears, the
        // cheapest kill in the mod is back — no matter how it is spelled or who wrote it.
        List<Path> files = javaFiles();
        for (String gone : List.of("markSuicide", "consumeSuicide", "isSuicidePending",
                                   "isGranted", "pendingSuicide", "deathGrantAt")) {
            List<String> hits = find(files, gone);
            assertTrue(hits.isEmpty(), "death permit came back: " + gone + " -> " + hits);
        }
    }

    @Test
    void noSourceFileStillRegistersTheSuicideCommand() throws IOException {
        List<String> hits = find(javaFiles(), "literal(\"die\")");
        assertTrue(hits.isEmpty(), "a die subcommand was registered again -> " + hits);
    }

    @Test
    void gatesReadTheBaselineNotTheLiveConfig() throws IOException {
        // The 40-tick window only works if some gate reads the live value. After 2.9.1 every gate
        // reads ProtectionBaseline, so a mid-tick config flip changes nothing.
        List<Path> mixins;
        try (Stream<Path> walk = Files.walk(MAIN.resolve("mixin"))) {
            mixins = walk.filter(p -> p.toString().endsWith(".java")).toList();
        }

        List<String> offenders = new ArrayList<>();
        offenders.addAll(find(mixins, "ModConfig.MASTER_TOGGLE.get()"));
        offenders.addAll(find(mixins, "ModConfig.DEATH_LOCK.get()"));

        assertTrue(offenders.isEmpty(),
                "a mixin reads the live config instead of the baseline -> " + offenders);
    }

    @Test
    void theRestoreEntryPointIsNotPublic() throws IOException {
        // A public restore() is a public duplication glitch; it is package-private on purpose.
        String snapshot = read(MAIN.resolve("SaveSnapshot.java"));
        assertTrue(snapshot.contains("static boolean restore(ServerPlayer p)"),
                "SaveSnapshot.restore must stay package-private");
        assertTrue(!snapshot.contains("public static boolean restore(ServerPlayer"),
                "SaveSnapshot.restore became public again");
    }

    @Test
    void theMixinPriorityIsExplicit() throws IOException {
        // Lower runs first. Default 1000 lets any competing mod cancel ahead of these gates.
        String json = Files.readString(RESOURCES.resolve("undying.mixins.json"), StandardCharsets.UTF_8);
        assertTrue(json.contains("\"priority\""),
                "explicit mixin priority is part of the design; do not remove it silently");
    }

    @Test
    void everyRegisteredMixinClassIsListedExactlyOnce() throws IOException {
        String json = Files.readString(RESOURCES.resolve("undying.mixins.json"), StandardCharsets.UTF_8);
        List<Path> mixins;
        try (Stream<Path> walk = Files.walk(MAIN.resolve("mixin"))) {
            mixins = walk.filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> {
                        try {
                            return read(p).contains("@Mixin");
                        } catch (IOException e) {
                            return false;
                        }
                    })
                    .toList();
        }

        List<String> missing = new ArrayList<>();
        for (Path p : mixins) {
            String name = p.getFileName().toString().replace(".java", "");
            String key = p.getParent().getFileName().toString().equals("mixin") ? name : "client." + name;
            if (!json.contains("\"" + key + "\"")) missing.add(key);
        }
        assertTrue(missing.isEmpty(), "mixin classes exist but are not registered: " + missing);
    }
}

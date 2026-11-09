package com.undying.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link ResourceOverrides}.
 *
 * <p>The class deliberately references no Minecraft or Forge type, so these run on a bare
 * JUnit classpath — the same constraint the rest of the test source set works under.
 */
class ResourceOverridesTest {

    @TempDir
    Path tmp;

    /** Writes a real zip so readDeclaredPaths is exercised against actual jar parsing. */
    private Path jarWith(String... entries) throws IOException {
        Path jar = tmp.resolve("sample.jar");
        try (ZipOutputStream z = new ZipOutputStream(Files.newOutputStream(jar))) {
            for (String e : entries) {
                z.putNextEntry(new ZipEntry(e));
                if (!e.endsWith("/")) z.write(1);
                z.closeEntry();
            }
        }
        return jar;
    }

    @Test
    void onlyVanillaNamespaceFilesAreDeclared() throws IOException {
        Path jar = jarWith(
                "assets/minecraft/textures/gui/scare.png",
                "assets/minecraft/sounds/scream.ogg",
                "assets/othermod/textures/gui/other.png",
                "META-INF/MANIFEST.MF",
                "assets/minecraft/textures/"      // directory entry: not content
        );

        Set<String> declared = ResourceOverrides.readDeclaredPaths(jar);

        assertEquals(Set.of(
                "assets/minecraft/textures/gui/scare.png",
                "assets/minecraft/sounds/scream.ogg"), declared);
    }

    @Test
    void emptyJarDeclaresNothing() throws IOException {
        assertTrue(ResourceOverrides.readDeclaredPaths(jarWith("META-INF/MANIFEST.MF")).isEmpty());
    }

    @Test
    void corruptJarFailsLoudlyInsteadOfReportingNoOverrides() throws IOException {
        // A silent empty result here would read as "this mod is clean", which is exactly the
        // kind of silent failure this project treats as the worst outcome.
        Path notAZip = tmp.resolve("broken.jar");
        Files.writeString(notAZip, "this is not a zip archive");
        assertThrows(IOException.class, () -> ResourceOverrides.readDeclaredPaths(notAZip));
    }

    @Test
    void intersectionSeparatesReplacingFromMerelyAdding() {
        Set<String> declared = Set.of(
                "assets/minecraft/textures/gui/scare.png",   // exists in vanilla
                "assets/minecraft/textures/gui/brandnew.png" // does not
        );
        Set<String> vanilla = Set.of("assets/minecraft/textures/gui/scare.png");

        assertEquals(Set.of("assets/minecraft/textures/gui/scare.png"),
                ResourceOverrides.actualOverrides(declared, vanilla));
    }

    @Test
    void categoriesFollowPresentationRatherThanSuspicion() {
        assertEquals("sounds", ResourceOverrides.categoryOf("assets/minecraft/sounds.json"));
        assertEquals("sounds", ResourceOverrides.categoryOf("assets/minecraft/sounds/a.ogg"));
        assertEquals("shaders", ResourceOverrides.categoryOf("assets/minecraft/shaders/post/x.json"));
        assertEquals("textures/gui", ResourceOverrides.categoryOf("assets/minecraft/textures/gui/a.png"));
        assertEquals("textures", ResourceOverrides.categoryOf("assets/minecraft/textures/block/a.png"));
        assertEquals("models", ResourceOverrides.categoryOf("assets/minecraft/models/block/a.json"));
        assertEquals("models", ResourceOverrides.categoryOf("assets/minecraft/blockstates/a.json"));
        assertEquals("other", ResourceOverrides.categoryOf("assets/minecraft/lang/en_us.json"));
    }

    @Test
    void categorizeDropsEmptyBucketsAndKeepsReportingOrder() {
        Map<String, Integer> counts = ResourceOverrides.categorize(List.of(
                "assets/minecraft/sounds/a.ogg",
                "assets/minecraft/sounds/b.ogg",
                "assets/minecraft/shaders/post/x.json",
                "assets/minecraft/textures/gui/a.png"
        ));

        assertEquals(3, counts.size());
        assertEquals(2, counts.get("sounds"));
        assertEquals(1, counts.get("shaders"));
        assertEquals(1, counts.get("textures/gui"));
        // sounds, then shaders, then textures/gui: the order the report should present them in
        assertEquals(List.of("sounds", "shaders", "textures/gui"), List.copyOf(counts.keySet()));
    }

    @Test
    void summaryNamesTotalAndBreakdown() {
        String s = ResourceOverrides.summarize(List.of(
                "assets/minecraft/sounds/a.ogg",
                "assets/minecraft/textures/gui/a.png"
        ));
        assertEquals("2 vanilla resource(s) overridden (sounds x1, textures/gui x1)", s);
    }

    @Test
    void emptyInputProducesNoReportLine() {
        assertEquals("", ResourceOverrides.summarize(List.of()));
        assertTrue(ResourceOverrides.categorize(List.of()).isEmpty());
    }

    @Test
    void degradedSummaryAdmitsTheVanillaListWasMissing() {
        String s = ResourceOverrides.summarizeUnverified(Set.of("assets/minecraft/sounds/a.ogg"));
        assertTrue(s.contains("not verified"), s);
        assertTrue(s.contains("1 resource(s)"), s);
    }
}

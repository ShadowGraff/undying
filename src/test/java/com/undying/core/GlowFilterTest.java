package com.undying.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The glow rule: which creatures get an outline.
 *
 * <p>The rule is the whole feature. Wrong here and the outline either covers the world or
 * covers nothing, both of which look like "the feature is broken" rather than "the filter is
 * wrong" — so every branch is pinned down.
 *
 * <p>Note there is no "neutral" case: Minecraft has no such category, and the only dependable
 * marker is the Enemy interface. Anything that claims otherwise is guessing.
 */
class GlowFilterTest {

    private static boolean glow(GlowFilter.Target t, boolean monster, boolean enemy,
                               String cat, String id) {
        return GlowFilter.shouldGlow(t, monster, enemy, cat, id, List.of(), List.of());
    }

    @Test
    void allMatchesEverything() {
        assertTrue(glow(GlowFilter.Target.ALL, true, true, "monster", "minecraft:zombie"));
        assertTrue(glow(GlowFilter.Target.ALL, false, false, "creature", "minecraft:cow"));
    }

    @Test
    void friendlyIsEverythingThatIsNotAMonster() {
        assertTrue(glow(GlowFilter.Target.FRIENDLY, false, false, "creature", "minecraft:cow"));
        assertFalse(glow(GlowFilter.Target.FRIENDLY, true, true, "monster", "minecraft:zombie"));
    }

    @Test
    void hostileUsesTheEnemyInterfaceNotTheCategory() {
        // A modded hostile mob in a non-monster category should still count as hostile.
        assertTrue(glow(GlowFilter.Target.HOSTILE, false, true, "creature", "mod:angry_thing"));
        // And a monster that does not implement Enemy should not.
        assertFalse(glow(GlowFilter.Target.HOSTILE, true, false, "monster", "mod:passive_beast"));
    }

    @Test
    void customMatchesByCategory() {
        assertTrue(GlowFilter.shouldGlow(GlowFilter.Target.CUSTOM, true, true,
                "monster", "minecraft:zombie", List.of("monster"), List.of()));
    }

    @Test
    void customMatchesByEntityId() {
        assertTrue(GlowFilter.shouldGlow(GlowFilter.Target.CUSTOM, true, true,
                "monster", "minecraft:enderman", List.of(), List.of("minecraft:enderman")));
    }

    @Test
    void customIgnoresCaseAndSurroundingSpace() {
        // These values are typed into a config file by hand.
        assertTrue(GlowFilter.shouldGlow(GlowFilter.Target.CUSTOM, true, true,
                "monster", "minecraft:enderman", List.of("  MONSTER "), List.of()));
    }

    @Test
    void anEmptyCustomListMatchesNothing() {
        assertFalse(GlowFilter.shouldGlow(GlowFilter.Target.CUSTOM, true, true,
                "monster", "minecraft:zombie", List.of(), List.of()));
        assertFalse(GlowFilter.shouldGlow(GlowFilter.Target.CUSTOM, false, false,
                "creature", "minecraft:cow", null, null));
    }

    @Test
    void unknownTargetNamesFallBackToAllRatherThanNothing() {
        // A typo in the config should not silently disable the feature.
        assertTrue(GlowFilter.Target.parse("HOSTLE") == GlowFilter.Target.ALL);
        assertTrue(GlowFilter.Target.parse(null) == GlowFilter.Target.ALL);
        assertTrue(GlowFilter.Target.parse("  hostile ") == GlowFilter.Target.HOSTILE);
    }
}

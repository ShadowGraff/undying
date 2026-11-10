package com.undying.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the domain allow-list rule.
 *
 * <p>The rule decides whether a mob is pushed away or left alone, so the interesting cases
 * are the boundaries: an unknown category, a null id, case differences, and an exact id
 * that should win over a category that is not allowed.
 */
class DomainFilterTest {

    @Test
    void vanillaFriendlyCategoriesAreAllowedByDefault() {
        for (String cat : DomainFilter.DEFAULT_ALLOWED_CATEGORIES) {
            assertTrue(DomainFilter.isAllowed(cat, null,
                    DomainFilter.DEFAULT_ALLOWED_CATEGORIES, List.of()), cat);
        }
    }

    @Test
    void monstersAreNotAllowedByDefault() {
        assertFalse(DomainFilter.isAllowed("MONSTER", "minecraft:zombie",
                DomainFilter.DEFAULT_ALLOWED_CATEGORIES, List.of()));
    }

    @Test
    void exactEntityIdIsAllowedEvenWhenItsCategoryIsNot() {
        assertTrue(DomainFilter.isAllowed("MONSTER", "minecraft:enderman",
                DomainFilter.DEFAULT_ALLOWED_CATEGORIES, List.of("minecraft:enderman")));
    }

    @Test
    void matchingIsCaseInsensitiveBecausePeopleEditConfigsByHand() {
        assertTrue(DomainFilter.isAllowed("creature", null, List.of("CREATURE"), List.of()));
        assertTrue(DomainFilter.isAllowed("MONSTER", "Minecraft:Cow",
                List.of(), List.of("minecraft:cow")));
    }

    @Test
    void surroundingWhitespaceInConfigValuesIsIgnored() {
        assertTrue(DomainFilter.isAllowed("CREATURE", null, List.of("  CREATURE  "), List.of()));
    }

    @Test
    void unknownOrMissingCategoryIsNotAllowed() {
        assertFalse(DomainFilter.isAllowed(null, null, DomainFilter.DEFAULT_ALLOWED_CATEGORIES, List.of()));
        assertFalse(DomainFilter.isAllowed("SOMETHING_NEW", "mod:thing",
                DomainFilter.DEFAULT_ALLOWED_CATEGORIES, List.of()));
    }

    @Test
    void emptyListsAllowNothing() {
        assertFalse(DomainFilter.isAllowed("CREATURE", "minecraft:cow", List.of(), List.of()));
    }

    @Test
    void nullListsAreTreatedAsEmptyRatherThanCrashing() {
        assertFalse(DomainFilter.isAllowed("CREATURE", "minecraft:cow", null, null));
    }

    @Test
    void addingACategoryAllowsThatCategory() {
        List<String> allows = List.of("MONSTER");
        assertTrue(DomainFilter.isAllowed("MONSTER", "minecraft:zombie", allows, List.of()));
        // …and the other categories are now excluded, because only one list decides.
        assertFalse(DomainFilter.isAllowed("CREATURE", "minecraft:cow", allows, List.of()));
    }
}

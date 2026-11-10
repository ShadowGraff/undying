package com.undying.core;

import java.util.Collection;
import java.util.List;

/**
 * The allow-list rule for the domain, kept free of Minecraft types so it can be tested.
 *
 * <p>A mob may approach when <b>either</b> its category or its exact entity id appears in
 * one of the two configured lists. Category-level entries make the common case cheap to
 * configure ("all vanilla animals"), and exact ids cover the exceptions ("also allow
 * {@code minecraft:wolf}").
 *
 * <p>Matching is case-insensitive because these strings come from a config file people edit
 * by hand; rejecting {@code creature} because the code expected {@code CREATURE} would be a
 * pointless trap.
 */
public final class DomainFilter {

    private DomainFilter() {}

    /**
     * Vanilla mob categories considered friendly by default.
     *
     * <p>{@code MONSTER} is deliberately absent: it covers hostile mobs <i>and</i> neutral
     * ones such as endermen, so the default keeps them out while leaving every animal,
     * ambient mob and water creature free to walk in.
     */
    public static final List<String> DEFAULT_ALLOWED_CATEGORIES = List.of(
            "CREATURE",                       // animals
            "AMBIENT",                        // bats
            "WATER_CREATURE",                 // squid
            "AXOLOTLS",                       // axolotl
            "UNDERGROUND_WATER_CREATURE"      // glow squid
    );

    /**
     * Whether this mob may approach the player.
     *
     * @param category        {@code MobCategory.name()}; null means "unknown", which is
     *                        treated as not allowed
     * @param entityId        registry id such as {@code minecraft:cow}; null is ignored
     * @param allowedCategories configured categories
     * @param allowedEntities   configured exact ids
     */
    public static boolean isAllowed(String category,
                                    String entityId,
                                    Collection<String> allowedCategories,
                                    Collection<String> allowedEntities) {
        // An explicit id beats the category rule in both directions only when it is present;
        // there is no "deny list" here on purpose — one list, one meaning.
        if (containsIgnoreCase(allowedEntities, entityId)) return true;
        return containsIgnoreCase(allowedCategories, category);
    }

    private static boolean containsIgnoreCase(Collection<String> values, String wanted) {
        if (values == null || wanted == null || wanted.isEmpty()) return false;
        for (String v : values) {
            if (v != null && v.trim().equalsIgnoreCase(wanted)) return true;
        }
        return false;
    }
}

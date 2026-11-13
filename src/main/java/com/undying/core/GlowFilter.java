package com.undying.core;

import java.util.List;
import java.util.Locale;

/**
 * Which entities the glow applies to.
 *
 * <h3>Why there is no "neutral" option</h3>
 * Minecraft has no such category. {@code MobCategory} has MONSTER, CREATURE, AMBIENT and a few
 * water ones; endermen, zombified piglins and wolves live in MONSTER or CREATURE alongside
 * mobs that behave nothing like them. The only dependable marker is {@code Enemy}, which means
 * "actively hostile" — so the split here is friendly (not MONSTER), hostile (Enemy), everything,
 * or a list the player writes himself.
 *
 * <p>Anything that claims to know "neutral" is either guessing from a hardcoded mob list (which
 * mods invalidate) or reading AI state (which changes while you look at it). A configurable list
 * is honest and always correct, because the player is the one deciding.
 *
 * <h3>Why this is a pure function</h3>
 * The rule is the whole feature; everything else is entity scanning. Keeping Minecraft types out
 * lets it be tested with booleans and strings — and a wrong rule here would either glow the
 * entire world or glow nothing, both of which look like the feature is broken.
 */
public final class GlowFilter {

    /** What to glow. */
    public enum Target {
        FRIENDLY, HOSTILE, ALL, CUSTOM;

        public static Target parse(String s) {
            if (s == null) return ALL;
            return switch (s.trim().toLowerCase(Locale.ROOT)) {
                case "friendly" -> FRIENDLY;
                case "hostile" -> HOSTILE;
                case "custom" -> CUSTOM;
                default -> ALL;
            };
        }
    }

    private GlowFilter() {}

    /**
     * @param target       the configured mode
     * @param monster      entity's category is MONSTER
     * @param enemy        entity implements {@code Enemy} (actively hostile)
     * @param category     category name, lowercase (e.g. {@code monster})
     * @param entityId     registry id, e.g. {@code minecraft:zombie}
     * @param allowCats    custom list of category names
     * @param allowIds     custom list of entity ids
     */
    public static boolean shouldGlow(Target target, boolean monster, boolean enemy,
                                     String category, String entityId,
                                     List<String> allowCats, List<String> allowIds) {
        if (target == null) return false;
        return switch (target) {
            case ALL -> true;
            case FRIENDLY -> !monster;
            case HOSTILE -> enemy;
            case CUSTOM -> hit(category, allowCats) || hit(entityId, allowIds);
        };
    }

    /** Case- and whitespace-insensitive: these values are typed into a config file by hand. */
    private static boolean hit(String value, List<String> list) {
        if (value == null || list == null || list.isEmpty()) return false;
        String v = value.trim().toLowerCase(Locale.ROOT);
        for (String s : list) {
            if (s != null && s.trim().toLowerCase(Locale.ROOT).equals(v)) return true;
        }
        return false;
    }
}

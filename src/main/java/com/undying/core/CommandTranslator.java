package com.undying.core;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Turns a command into one plain sentence.
 *
 * <h3>Why translate at all</h3>
 * A player watching a command block fire sees {@code execute as @e[type=zombie] run tp @s ~ ~1 ~}
 * and learns nothing except that something is happening. The same command as
 * "执行 → 传送 所有实体 → ~ ~1 ~" tells them what is happening, which is the difference
 * between a log and a warning.
 *
 * <h3>Why it is a pure function</h3>
 * Everything here is string work. Keeping Minecraft types out of it means the whole table can be
 * tested with literals, which matters because a wrong translation is worse than no translation:
 * it would tell the player something confidently false about what just ran.
 *
 * <p>Unknown commands are not guessed at. They return {@code null} and the caller shows the raw
 * text — an honest "I do not know what this is" beats a made-up summary.
 */
public final class CommandTranslator {

    private CommandTranslator() {}

    /** Item ids worth naming; everything else keeps its id, which is still readable. */
    private static final Map<String, String> ITEMS = new HashMap<>();

    static {
        ITEMS.put("diamond", "钻石");
        ITEMS.put("diamond_block", "钻石块");
        ITEMS.put("emerald", "绿宝石");
        ITEMS.put("gold_ingot", "金锭");
        ITEMS.put("iron_ingot", "铁锭");
        ITEMS.put("netherite_ingot", "下界合金锭");
        ITEMS.put("netherite_sword", "下界合金剑");
        ITEMS.put("diamond_sword", "钻石剑");
        ITEMS.put("golden_apple", "金苹果");
        ITEMS.put("enchanted_golden_apple", "附魔金苹果");
        ITEMS.put("totem_of_undying", "不死图腾");
        ITEMS.put("ender_pearl", "末影珍珠");
        ITEMS.put("tnt", "TNT");
        ITEMS.put("bedrock", "基岩");
        ITEMS.put("command_block", "命令方块");
        ITEMS.put("barrier", "屏障");
        ITEMS.put("cookie", "曲奇");
        ITEMS.put("bread", "面包");
    }

    private static final Map<String, String> TIMES = Map.of(
            "day", "白天", "noon", "正午", "night", "夜晚", "midnight", "午夜",
            "sunrise", "日出", "sunset", "日落");

    private static final Map<String, String> WEATHER = Map.of(
            "clear", "晴", "rain", "雨", "thunder", "雷暴");

    private static final Map<String, String> MODES = Map.of(
            "survival", "生存", "creative", "创造", "adventure", "冒险", "spectator", "旁观");

    private static final Map<String, String> DIFFICULTY = Map.of(
            "peaceful", "和平", "easy", "简单", "normal", "普通", "hard", "困难");

    /**
     * @param raw the command as executed, with or without a leading slash
     * @return a one-line summary, or {@code null} when the command is not recognised
     */
    public static String describe(String raw) {
        if (raw == null) return null;
        String t = raw.trim();
        if (t.startsWith("/")) t = t.substring(1);
        if (t.isEmpty()) return null;

        // /execute runs another command. Describing the outer command alone would say "execute"
        // and hide the only part a player cares about, so the inner command is described and
        // labelled as being run through something.
        String lower = t.toLowerCase(Locale.ROOT);
        int run = lower.indexOf(" run ");
        if (lower.startsWith("execute ") && run > 0) {
            String inner = describe(t.substring(run + 5));
            return inner == null ? null : "执行 → " + inner;
        }

        String[] a = t.split("\\s+");
        if (a.length == 0) return null;
        String head = stripNamespace(a[0].toLowerCase(Locale.ROOT));

        switch (head) {
            case "tp":
            case "teleport":
                return teleport(a);
            case "give":
                return give(a);
            case "kill":
                return a.length > 1 ? "杀死 " + target(a[1]) : "杀死 自己";
            case "clear":
                return a.length > 1 ? "清空 " + target(a[1]) + " 的物品" : "清空 自己的物品";
            case "time":
                return time(a);
            case "weather":
                return a.length > 1 ? "天气 → " + map(WEATHER, a[1]) : null;
            case "gamemode":
                return gamemode(a);
            case "summon":
                return summon(a);
            case "effect":
                return effect(a);
            case "difficulty":
                return a.length > 1 ? "难度 → " + map(DIFFICULTY, a[1]) : null;
            case "gamerule":
                return a.length > 2 ? "规则 " + a[1] + " = " + a[2] : null;
            case "setblock":
                return a.length > 4 ? "放置方块 " + item(a[4]) + " @ " + a[1] + " " + a[2] + " " + a[3] : null;
            case "fill":
                return a.length > 1 ? "填充区域（" + a[1] + " 起）" : null;
            case "setworldspawn":
                return a.length > 3 ? "世界出生点 → " + a[1] + " " + a[2] + " " + a[3] : null;
            case "spawnpoint":
                return "设置 " + (a.length > 1 ? target(a[1]) : "自己") + " 的出生点";
            case "title":
                return a.length > 2 ? "标题文字 → " + target(a[1]) : null;
            default:
                return null;
        }
    }

    private static String teleport(String[] a) {
        // Token counts, not guesses: "tp @p 100 64 -200" is five tokens, "tp 100 64 -200" is
        // four, "tp @s @p" is three. Getting this wrong reads a target as a coordinate.
        if (a.length >= 5) {
            return "传送 " + target(a[1]) + " → " + a[2] + " " + a[3] + " " + a[4];
        }
        if (a.length == 4) {
            return "传送 自己 → " + a[1] + " " + a[2] + " " + a[3];
        }
        if (a.length == 3) {
            return "传送 " + target(a[1]) + " → " + target(a[2]);
        }
        return null;
    }

    private static String give(String[] a) {
        if (a.length < 3) return null;
        String count = a.length > 3 ? " ×" + a[3] : "";
        return "给予 " + target(a[1]) + " " + item(a[2]) + count;
    }

    private static String time(String[] a) {
        if (a.length < 3) return null;
        String sub = a[1].toLowerCase(Locale.ROOT);
        if (sub.equals("set")) {
            String v = a[2].toLowerCase(Locale.ROOT);
            return "时间 → " + TIMES.getOrDefault(v, a[2]);
        }
        if (sub.equals("add")) return "时间推进 " + a[2];
        if (sub.equals("query")) return "查询时间";
        return null;
    }

    private static String gamemode(String[] a) {
        if (a.length < 2) return null;
        String mode = map(MODES, a[1]);
        if (a.length >= 3) return "模式 " + target(a[2]) + " → " + mode;
        return "模式 自己 → " + mode;
    }

    private static String summon(String[] a) {
        if (a.length < 2) return null;
        String where = a.length >= 5 ? " @ " + a[2] + " " + a[3] + " " + a[4] : "";
        return "生成 " + stripNamespace(a[1]) + where;
    }

    private static String effect(String[] a) {
        if (a.length < 4) return null;
        String sub = a[1].toLowerCase(Locale.ROOT);
        if (sub.equals("clear")) return "清除 " + target(a[2]) + " 的效果";
        if (sub.equals("give")) {
            String seconds = a.length > 4 ? " " + a[4] + "秒" : "";
            return "效果 " + target(a[2]) + " ← " + stripNamespace(a[3]) + seconds;
        }
        return null;
    }

    private static String target(String s) {
        if (s == null || s.isEmpty()) return "?";
        String base = s;
        int bracket = s.indexOf('[');
        if (bracket > 0) base = s.substring(0, bracket);
        return switch (base) {
            case "@p" -> "最近的玩家";
            case "@s" -> "自己";
            case "@a" -> "所有玩家";
            case "@e" -> "所有实体";
            case "@r" -> "随机玩家";
            default -> s;
        };
    }

    private static String item(String id) {
        String key = stripNamespace(id);
        return ITEMS.getOrDefault(key, id);
    }

    private static String stripNamespace(String id) {
        return id.startsWith("minecraft:") ? id.substring("minecraft:".length()) : id;
    }

    private static String map(Map<String, String> table, String key) {
        if (key == null) return "?";
        String v = table.get(key.toLowerCase(Locale.ROOT));
        return v != null ? v : key;
    }
}

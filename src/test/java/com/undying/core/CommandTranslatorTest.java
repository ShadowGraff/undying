package com.undying.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The command translator's whole table.
 *
 * <p>These tests carry more weight than usual: a wrong translation would tell a player
 * something confidently false about what just ran, which is worse than showing the raw text.
 * Every case here is a sentence the mod is allowed to put on screen.
 */
class CommandTranslatorTest {

    @Test
    void teleportWithCoordinatesNamesTheTarget() {
        assertEquals("传送 最近的玩家 → 100 64 -200",
                CommandTranslator.describe("tp @p 100 64 -200"));
    }

    @Test
    void teleportToAnotherEntityIsNotReadAsCoordinates() {
        // Four arguments, not six: the destination is a selector, not a position.
        assertEquals("传送 自己 → 最近的玩家",
                CommandTranslator.describe("/tp @s @p"));
    }

    @Test
    void executeIsDescribedByTheCommandItRuns() {
        assertEquals("执行 → 传送 自己 → ~ ~1 ~",
                CommandTranslator.describe("execute as @e[type=zombie] run tp @s ~ ~1 ~"));
    }

    @Test
    void giveKeepsTheItemAndTheCount() {
        assertEquals("给予 所有玩家 钻石 ×3",
                CommandTranslator.describe("give @a minecraft:diamond 3"));
    }

    @Test
    void unknownItemsKeepTheirIdRatherThanBeingGuessed() {
        assertTrue(CommandTranslator.describe("give @p some_mod:strange_thing 1")
                .contains("some_mod:strange_thing"));
    }

    @Test
    void timeAndWeatherUsePlainWords() {
        assertEquals("时间 → 夜晚", CommandTranslator.describe("time set night"));
        assertEquals("天气 → 雷暴", CommandTranslator.describe("weather thunder"));
    }

    @Test
    void gamemodeNamesWhoIsAffected() {
        assertEquals("模式 所有玩家 → 创造", CommandTranslator.describe("gamemode creative @a"));
        assertEquals("模式 自己 → 生存", CommandTranslator.describe("gamemode survival"));
    }

    @Test
    void killAndClearAreShort() {
        assertEquals("杀死 所有实体", CommandTranslator.describe("kill @e"));
        assertEquals("清空 自己的物品", CommandTranslator.describe("clear"));
    }

    @Test
    void effectAndSummonKeepTheDetails() {
        assertEquals("效果 自己 ← speed 30秒",
                CommandTranslator.describe("effect give @s speed 30 1"));
        assertEquals("生成 zombie @ 10 64 -5",
                CommandTranslator.describe("summon minecraft:zombie 10 64 -5"));
    }

    @Test
    void setblockNamesTheBlockAndThePosition() {
        assertEquals("放置方块 TNT @ 10 64 -5",
                CommandTranslator.describe("setblock 10 64 -5 tnt"));
    }

    @Test
    void unknownCommandsReturnNullInsteadOfAGuess() {
        assertNull(CommandTranslator.describe("undying report"));
        assertNull(CommandTranslator.describe("somecustomcommand with args"));
        assertNull(CommandTranslator.describe(""));
        assertNull(CommandTranslator.describe(null));
    }

    @Test
    void namespacePrefixesDoNotHideTheCommand() {
        assertEquals("传送 最近的玩家 → 0 64 0",
                CommandTranslator.describe("minecraft:tp @p 0 64 0"));
    }
}

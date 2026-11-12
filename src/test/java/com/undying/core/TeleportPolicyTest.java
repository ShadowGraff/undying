package com.undying.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The teleport guard's decisions.
 *
 * <p>Pure, which is the only reason they can be tested at all: the actual interception needs a
 * live server, a player and something trying to move them.
 */
class TeleportPolicyTest {

    // ═══════════ "Is this command a teleport?" ═══════════

    @Test
    void plainTeleportCommandsAreRecognised() {
        assertTrue(TeleportPolicy.isTeleportCommand("tp @s 0 64 0"));
        assertTrue(TeleportPolicy.isTeleportCommand("/tp Steve 100 70 -200"));
        assertTrue(TeleportPolicy.isTeleportCommand("teleport @a ~ ~1 ~"));
        assertTrue(TeleportPolicy.isTeleportCommand("minecraft:tp @s 1 2 3"));
    }

    @Test
    void teleportInsideExecuteIsRecognised() {
        assertTrue(TeleportPolicy.isTeleportCommand("execute as @e run tp @s ~ ~ ~"));
        assertTrue(TeleportPolicy.isTeleportCommand("execute at @p run teleport @s 0 0 0"));
    }

    @Test
    void anItemNamedTpIsNotATeleport() {
        // Segment scan, not "contains tp": this is the false positive the scan exists to avoid.
        assertFalse(TeleportPolicy.isTeleportCommand("give @s minecraft:tp"));
        assertFalse(TeleportPolicy.isTeleportCommand("say tp"));
        assertFalse(TeleportPolicy.isTeleportCommand("tp_sword"));
    }

    @Test
    void emptyAndNullAreNotTeleports() {
        assertFalse(TeleportPolicy.isTeleportCommand(null));
        assertFalse(TeleportPolicy.isTeleportCommand(""));
        assertFalse(TeleportPolicy.isTeleportCommand("   "));
        assertFalse(TeleportPolicy.isTeleportCommand("/"));
    }

    // ═══════════ "Should this teleport be stopped?" ═══════════

    @Test
    void everyTeleportIsStopped() {
        // No case is special-cased. The previous version let player-typed /tp through, and a
        // live session showed it treating a real player command as a mod's doing. A rule with
        // an exception that can be wrong in production is worth less than a boring rule.
        assertTrue(TeleportPolicy.shouldIntercept(true, true, false));
    }

    @Test
    void theApprovedTeleportIsNotInterceptedAgain() {
        // Otherwise "yes" would re-trigger the same question and the player could never move.
        assertFalse(TeleportPolicy.shouldIntercept(true, true, true));
    }

    @Test
    void theSwitchAndTheEnvironmentGateBothDisableIt() {
        assertFalse(TeleportPolicy.shouldIntercept(false, true, false));
        assertFalse(TeleportPolicy.shouldIntercept(true, false, false));
        // The gate wins over everything: on a server, not even the bypass matters.
        assertFalse(TeleportPolicy.shouldIntercept(false, false, false));
    }
}

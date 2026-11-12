package com.undying.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The teleport guard's two judgements.
 *
 * <p>Both are pure, which is the only reason they can be tested at all: the actual interception
 * needs a live server, a player and something trying to move them.
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
    void anUnrequestedTeleportIsIntercepted() {
        assertTrue(TeleportPolicy.shouldIntercept(true, true, false, false));
    }

    @Test
    void aPlayerTypedTeleportGoesThrough() {
        assertFalse(TeleportPolicy.shouldIntercept(true, true, true, false));
    }

    @Test
    void theApprovedTeleportIsNotInterceptedAgain() {
        // Otherwise "yes" would re-trigger the same question and the player could never move.
        assertFalse(TeleportPolicy.shouldIntercept(true, true, false, true));
    }

    @Test
    void theSwitchAndTheEnvironmentGateBothDisableIt() {
        assertFalse(TeleportPolicy.shouldIntercept(false, true, false, false));
        assertFalse(TeleportPolicy.shouldIntercept(true, false, false, false));
    }
}

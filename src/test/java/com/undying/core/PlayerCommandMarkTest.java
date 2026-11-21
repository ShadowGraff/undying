package com.undying.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The "a player typed this" stamp.
 *
 * <p>This is now the only thing standing between {@code /antigrief die} and any mod in the process
 * being able to run it through the dispatcher. Two properties matter: it matches only the tick it
 * was written for, and it is per thread.
 */
class PlayerCommandMarkTest {

    @Test
    void aStampMatchesItsOwnTick() {
        PlayerCommandMark.noteChatCommand(1000L);
        assertTrue(PlayerCommandMark.chatCommandThisTick(1000L));
    }

    @Test
    void itExpiresTheTickAfter() {
        PlayerCommandMark.noteChatCommand(1000L);
        assertFalse(PlayerCommandMark.chatCommandThisTick(1001L),
                "a stamp that survives ticks is a permanent backdoor");
    }

    @Test
    void nothingIsStampedByDefault() {
        assertFalse(PlayerCommandMark.chatCommandThisTick(4242L));
    }

    @Test
    void theNewestStampWins() {
        PlayerCommandMark.noteChatCommand(10L);
        PlayerCommandMark.noteChatCommand(20L);
        assertFalse(PlayerCommandMark.chatCommandThisTick(10L));
        assertTrue(PlayerCommandMark.chatCommandThisTick(20L));
    }
}

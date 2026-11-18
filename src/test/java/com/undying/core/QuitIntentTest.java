package com.undying.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The "the player asked to leave" window.
 *
 * <p>This decides whether a shutdown is questioned, so both directions matter: too narrow and
 * a slow world save makes Save-and-Quit look like a forced shutdown; too wide and a mod only has
 * to wait five seconds to get through.
 */
class QuitIntentTest {

    @Test
    void nothingMarkedMeansNoIntent() {
        assertFalse(QuitIntent.recentAt(1_000_000L, 0L));
    }

    @Test
    void justNowCounts() {
        assertTrue(QuitIntent.recentAt(1_000_000L, 1_000_000L));
        assertTrue(QuitIntent.recentAt(1_000_000L, 999_000L));
    }

    @Test
    void theEdgeOfTheWindowIsInside() {
        assertTrue(QuitIntent.recentAt(10_000L, 5_000L));
    }

    @Test
    void oneMillisecondLaterIsOutside() {
        assertFalse(QuitIntent.recentAt(10_001L, 5_000L));
    }
}

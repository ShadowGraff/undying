package com.undying.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A smoke test that proves the test harness itself works.
 *
 * <p>Deliberately free of any Minecraft or Forge imports: if this fails to run, the
 * problem is the test setup, not the mod. Every other test file in this source set is
 * written the same way, so the whole suite can run without provisioning the game.
 */
class SelfTestHarnessTest {

    @Test
    @DisplayName("test harness runs")
    void harnessRuns() {
        assertTrue(true);
    }

    @Test
    @DisplayName("assertions actually fail when they should")
    void assertionsAreNotVacuous() {
        // Guards against a harness that reports success no matter what.
        assertThrows(AssertionError.class, () -> assertEquals(1, 2));
    }
}

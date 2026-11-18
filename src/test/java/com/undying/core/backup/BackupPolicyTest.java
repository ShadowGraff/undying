package com.undying.core.backup;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * When a backup may run.
 *
 * <p>Every one of these rules exists because the alternative is visible to the player: running on
 * a server they do not own, running every tick, running while the last one is still writing, or
 * running on a world nobody is in.
 */
class BackupPolicyTest {

    private static final long NOW = 1_800_000_000_000L;

    @Test
    void aMinuteAfterTheLastRunIsEnough() {
        assertTrue(BackupPolicy.shouldRun(true, true, 1, false, NOW, NOW - 60_000L));
    }

    @Test
    void halfAMinuteIsNot() {
        assertFalse(BackupPolicy.shouldRun(true, true, 1, false, NOW, NOW - 30_000L));
    }

    @Test
    void theVeryFirstRunHappensImmediately() {
        // lastMs = 0 means "never": the player should not wait a minute for the first copy.
        assertTrue(BackupPolicy.shouldRun(true, true, 1, false, NOW, 0L));
    }

    @Test
    void neverOnSomebodyElsesServer() {
        assertFalse(BackupPolicy.shouldRun(true, false, 3, false, NOW, 0L));
    }

    @Test
    void neverWhileTheSwitchIsOff() {
        assertFalse(BackupPolicy.shouldRun(false, true, 1, false, NOW, 0L));
    }

    @Test
    void neverWithNobodyInAWorld() {
        assertFalse(BackupPolicy.shouldRun(true, true, 0, false, NOW, 0L));
    }

    @Test
    void neverOverlappingTheRunInFlight() {
        assertFalse(BackupPolicy.shouldRun(true, true, 1, true, NOW, 0L));
    }
}

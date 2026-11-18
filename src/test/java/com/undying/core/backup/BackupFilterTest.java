package com.undying.core.backup;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The exclusion list.
 *
 * <p>Both entries here are "found by other people's outages": a live lock file that throws on
 * read, and the backup folder that backs itself up until the disk is gone.
 */
class BackupFilterTest {

    @Test
    void theLiveLockIsNeverCopied() {
        assertTrue(BackupFilter.isExcluded("session.lock"));
        assertTrue(BackupFilter.isExcluded("world/session.lock"));
        assertFalse(BackupFilter.isWorthCopying("session.lock", 3));
    }

    @Test
    void ourOwnFolderIsNeverDescendedInto() {
        assertTrue(BackupFilter.isExcluded("_undying_backup"));
        assertTrue(BackupFilter.isExcluded("_undying_backup/world/index.json"));
    }

    @Test
    void theWorldItselfIsCopied() {
        assertFalse(BackupFilter.isExcluded("level.dat"));
        assertFalse(BackupFilter.isExcluded("region/r.0.0.mca"));
        assertFalse(BackupFilter.isExcluded("DIM-1/region/r.0.0.mca"));
        assertFalse(BackupFilter.isExcluded("playerdata/0123.dat"));
    }

    @Test
    void vanillaPreviousLevelDataIsKeptOnPurpose() {
        // level.dat_old is the cheapest rescue a player has when level.dat is unreadable.
        assertFalse(BackupFilter.isExcluded("level.dat_old"));
    }

    @Test
    void unsafePathsAreExcludedByTheSameRuleThatProtectsRestore() {
        assertTrue(BackupFilter.isExcluded("../options.txt"));
        assertTrue(BackupFilter.isExcluded("region\\r.0.0.mca"));
        assertTrue(BackupFilter.isExcluded(""));
    }

    @Test
    void emptyFilesAreNotWorthASlot() {
        assertFalse(BackupFilter.isWorthCopying("region/r.0.0.mca", 0));
        assertTrue(BackupFilter.isWorthCopying("region/r.0.0.mca", 1));
        assertFalse(BackupFilter.isWorthCopying("session.lock", 1));
    }

    @Test
    void anyLockFileIsSkippedNotJustTheKnownOne() {
        assertTrue(BackupFilter.isExcluded("some_mod/state.LOCK"));
    }
}

package com.undying.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the multiplayer GUI ban.
 *
 * <p>These run without the game on the classpath: {@link MultiplayerBan} is deliberately
 * built from pure string logic so that the decision can be pinned down exactly.
 */
class MultiplayerBanTest {

    @Nested
    @DisplayName("banned keys")
    class BannedKeys {

        @Test
        @DisplayName("the title screen multiplayer button is banned")
        void multiPlayerKeyIsBanned() {
            assertTrue(MultiplayerBan.isBannedKey("menu.multiplayer"));
        }

        @Test
        @DisplayName("the open-to-LAN button is banned")
        void shareToLanKeyIsBanned() {
            assertTrue(MultiplayerBan.isBannedKey("menu.shareToLan"));
        }

        @Test
        @DisplayName("the constants match the vanilla language keys")
        void constantsMatchVanilla() {
            // Pinned literally: these strings are the security boundary. If someone
            // "tidies" a constant, this test fails instead of the ban silently stopping.
            assertEquals("menu.multiplayer", MultiplayerBan.KEY_MULTIPLAYER);
            assertEquals("menu.shareToLan", MultiplayerBan.KEY_SHARE_TO_LAN);
        }
    }

    @Nested
    @DisplayName("keys that must NOT be banned")
    class AllowedKeys {

        @Test
        @DisplayName("null is not banned")
        void nullIsNotBanned() {
            assertFalse(MultiplayerBan.isBannedKey(null));
        }

        @Test
        @DisplayName("empty is not banned")
        void emptyIsNotBanned() {
            assertFalse(MultiplayerBan.isBannedKey(""));
        }

        @Test
        @DisplayName("neighbouring multiplayer keys are not banned")
        void neighbouringKeysAreNotBanned() {
            // Prefix matching would wrongly disable these. Exact matching must not.
            assertFalse(MultiplayerBan.isBannedKey("menu.multiplayerWarning"));
            assertFalse(MultiplayerBan.isBannedKey("menu.multiplayerOptions"));
            assertFalse(MultiplayerBan.isBannedKey("menu.multiplayer."));
        }

        @Test
        @DisplayName("single-player and options are not banned")
        void ordinaryMenuKeysAreNotBanned() {
            assertFalse(MultiplayerBan.isBannedKey("menu.singleplayer"));
            assertFalse(MultiplayerBan.isBannedKey("menu.options"));
            assertFalse(MultiplayerBan.isBannedKey("menu.quit"));
            assertFalse(MultiplayerBan.isBannedKey("menu.returnToGame"));
        }

        @Test
        @DisplayName("localised text is not a key, so it is not banned")
        void localisedTextIsNotBanned() {
            // This is the bug the implementation avoids: comparing rendered text would
            // match nothing on a non-English client, or match accidentally elsewhere.
            assertFalse(MultiplayerBan.isBannedKey("多人游戏"));
            assertFalse(MultiplayerBan.isBannedKey("Multiplayer"));
            assertFalse(MultiplayerBan.isBannedKey("Open to LAN"));
        }

        @Test
        @DisplayName("case differences are not treated as the same key")
        void caseSensitive() {
            assertFalse(MultiplayerBan.isBannedKey("MENU.MULTIPLAYER"));
            assertFalse(MultiplayerBan.isBannedKey("Menu.ShareToLan"));
        }

        @Test
        @DisplayName("surrounding whitespace is not trimmed away")
        void whitespaceIsNotTrimmed() {
            assertFalse(MultiplayerBan.isBannedKey(" menu.multiplayer"));
            assertFalse(MultiplayerBan.isBannedKey("menu.multiplayer "));
        }
    }

    @Nested
    @DisplayName("labels")
    class Labels {

        @Test
        @DisplayName("each banned key has a human-readable label")
        void labelsAreDistinct() {
            assertEquals("Multiplayer", MultiplayerBan.label("menu.multiplayer"));
            assertEquals("Open to LAN", MultiplayerBan.label("menu.shareToLan"));
        }

        @Test
        @DisplayName("an unknown key still yields a non-null label")
        void unknownLabelIsNotNull() {
            assertEquals("unknown", MultiplayerBan.label("menu.options"));
            assertEquals("unknown", MultiplayerBan.label(null));
        }
    }

    @Nested
    @DisplayName("once-per-session logging")
    class Logging {

        @Test
        @DisplayName("resetNotification can be called repeatedly without error")
        void resetIsIdempotent() {
            // The logging path itself touches the mod logger, which is not initialised in a
            // unit test, so only the flag handling is exercised here.
            MultiplayerBan.resetNotification();
            MultiplayerBan.resetNotification();
            assertFalse(MultiplayerBan.isBannedKey(null));
        }
    }

    @Nested
    @DisplayName("reason text")
    class Reason {

        @Test
        @DisplayName("a reason is always available and mentions single-player")
        void reasonMentionsSinglePlayer() {
            String reason = MultiplayerBan.reason();
            assertTrue(reason.toLowerCase().contains("single-player"),
                    "reason should state the single-player restriction, was: " + reason);
        }
    }
}

package com.undying.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the single-player-only gate.
 *
 * <p>This is a security boundary: if it wrongly allows, the mod hands out
 * invulnerability on somebody's server. Every branch is therefore covered explicitly,
 * including the LAN case that {@code MinecraftServer.isSingleplayer()} does not catch.
 */
class EnvironmentTest {

    @Nested
    @DisplayName("the gate decides correctly for all four environments")
    class Gate {

        @Test
        @DisplayName("single player, not published -> allowed")
        void singleplayerLocal() {
            assertTrue(Environment.decide(true, false));
        }

        @Test
        @DisplayName("single player opened to LAN -> BLOCKED (this is the case isSingleplayer misses)")
        void singleplayerButLan() {
            assertFalse(Environment.decide(true, true));
        }

        @Test
        @DisplayName("dedicated server -> blocked")
        void dedicatedServer() {
            assertFalse(Environment.decide(false, false));
        }

        @Test
        @DisplayName("client connected to a remote server -> blocked")
        void remoteClient() {
            // On a client connected to someone else's server there is no local server,
            // so isSingleplayer() is false. Same shape as a dedicated server.
            assertFalse(Environment.decide(false, false));
        }

        @Test
        @DisplayName("blocked whenever isSingleplayer is false, regardless of LAN")
        void dedicatedAlwaysBlocked() {
            assertFalse(Environment.decide(false, true));
        }
    }

    @Nested
    @DisplayName("LAN detection")
    class LanDetection {

        @ParameterizedTest(name = "port={0} ip=\"{1}\" -> lanPublished={2}")
        @CsvSource({
                // port, ip, expected
                "25565, 192.168.1.5, true",     // published to a real address
                "25565, 127.0.0.1,   true",     // published even if only loopback
                "0,     192.168.1.5, false",    // no bound port
                "0,     '',          false",    // nothing bound
                "-1,    '',          false",    // defensive: negative port
        })
        void lanDetection(int port, String ip, boolean expected) {
            assertEquals(expected, Environment.decideLan(port, ip));
        }

        @Test
        @DisplayName("null address means not published")
        void nullAddress() {
            assertFalse(Environment.decideLan(25565, null));
        }

        @Test
        @DisplayName("empty address means not published")
        void emptyAddress() {
            assertFalse(Environment.decideLan(25565, ""));
        }
    }

    @Nested
    @DisplayName("fail-closed behaviour")
    class FailClosed {

        @Test
        @DisplayName("null server is blocked")
        void nullServer() {
            assertFalse(Environment.isAllowed((net.minecraft.server.MinecraftServer) null));
        }

        @Test
        @DisplayName("null player is blocked")
        void nullPlayer() {
            assertFalse(Environment.isAllowed((net.minecraft.server.level.ServerPlayer) null));
        }
    }
}

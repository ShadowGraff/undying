package com.undying.core;

import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Field;

/**
 * Extra, one-way evidence that a player is connected over an in-memory channel.
 *
 * <h3>Why the existing LAN signals are not enough</h3>
 * The multiplayer gate currently reads two booleans: the LAN pinger field on the integrated server,
 * and {@code getPort() > 0}. Both are ordinary fields and methods, so a mod in the same process can
 * make them read whatever it likes — the gate becomes a switch a stranger can flip, and a
 * "single-player only" protection quietly starts working on a world other people are in.
 *
 * <p>A Netty channel is a different kind of fact: a single-player host talks to its own client over
 * {@code LocalChannel} (an in-memory pipe), while a LAN client is a real socket. Faking that means
 * replacing the transport, not flipping a value.
 *
 * <h3>Why this only ever <i>removes</i> permission</h3>
 * {@link #isLocal} returns {@code null} when it cannot tell — reflection may fail, a future version
 * may rename the fields. Callers treat {@code null} as "no opinion" and keep whatever the other
 * signals said. Only a confident "this is not a local channel" closes the gate. That direction is
 * the safe one: a missed signal leaves behaviour unchanged, a wrong one would have been a false
 * accusation of multiplayer that disables everything on a single-player world.
 *
 * <p>Fields are found by <b>type</b>, never by name, because their names differ between the
 * development and runtime mapping sets.
 */
public final class LocalChannelProbe {

    private LocalChannelProbe() {}

    private static volatile Field connectionField;
    private static volatile Field channelField;
    private static volatile boolean resolved;

    /** Reuse window, in ticks. A channel does not change mid-session often enough to matter. */
    private static final int CACHE_TICKS = 20;

    private static volatile java.util.UUID cachedId;
    private static volatile int cachedTick = -1_000_000;
    private static volatile Boolean cachedValue;

    /** @return true/false when known, null when it cannot be determined */
    public static Boolean isLocal(ServerPlayer p) {
        try {
            // Called from every gate, which means several times per damage event. Two reflective
            // field reads each time is real overhead for a value that cannot change between
            // ticks, so it is cached for a second.
            if (p != null && p.getUUID().equals(cachedId) && p.tickCount - cachedTick < CACHE_TICKS) {
                return cachedValue;
            }
            if (p == null || p.connection == null) return null;
            resolve(p.connection.getClass());
            Field conn = connectionField;
            Field chan = channelField;
            if (conn == null || chan == null) return null;

            Boolean result = compute(p, conn, chan);
            cachedId = p.getUUID();
            cachedTick = p.tickCount;
            cachedValue = result;
            return result;
        } catch (Throwable t) {
            ModLog.swallowed("LocalChannelProbe.isLocal", t);
            return null;
        }
    }

    private static Boolean compute(ServerPlayer p, Field conn, Field chan) throws Exception {
        {
            Object netConnection = conn.get(p.connection);
            if (netConnection == null) return null;
            Object channel = chan.get(netConnection);
            if (channel == null) return null;

            return channel.getClass().getName().startsWith("io.netty.channel.local.");
        }
    }

    private static void resolve(Class<?> listenerClass) {
        if (resolved) return;
        synchronized (LocalChannelProbe.class) {
            if (resolved) return;
            try {
                for (Field f : listenerClass.getDeclaredFields()) {
                    if (net.minecraft.network.Connection.class.isAssignableFrom(f.getType())) {
                        f.setAccessible(true);
                        connectionField = f;
                        break;
                    }
                }
                if (connectionField != null) {
                    for (Field f : net.minecraft.network.Connection.class.getDeclaredFields()) {
                        if (io.netty.channel.Channel.class.isAssignableFrom(f.getType())) {
                            f.setAccessible(true);
                            channelField = f;
                            break;
                        }
                    }
                }
                resolved = true;
                if (connectionField == null || channelField == null) {
                    IntegrityGuard.LOGGER.warn(
                            "[gate] local-channel probe unavailable (connection={}, channel={}); "
                                    + "falling back to the other signals",
                            connectionField != null, channelField != null);
                }
            } catch (Throwable t) {
                ModLog.swallowed("LocalChannelProbe.resolve", t);
                resolved = true;   // do not retry every tick
            }
        }
    }
}

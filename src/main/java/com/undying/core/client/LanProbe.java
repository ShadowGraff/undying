package com.undying.core.client;

/**
 * Exists only to give the merged {@code IntegratedServer} method a type that is real at
 * runtime.
 *
 * <p>A mixin class cannot be referenced from ordinary code. Mixin merges it <b>into</b> the
 * target class and never loads it as a class of its own, so casting a server to
 * {@code MixinIntegratedServerLan} compiles and then throws
 * {@code NoClassDefFoundError: ... is invalid} on every call. Declaring a plain interface
 * and having the mixin implement it puts the method on a type the JVM can actually resolve.
 *
 * <p>No Minecraft type is referenced here, so this stays loadable anywhere.
 */
public interface LanProbe {

    /** True when the world is currently published to LAN. */
    boolean undying$isPublishedToLan();
}

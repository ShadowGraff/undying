package com.undying.core.mixin;

import com.undying.core.KickGuard;
import com.undying.core.ModLog;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Holds a forced shutdown of the integrated server.
 *
 * <h3>What a "forced shutdown" looks like from here</h3>
 * Every stop converges on {@code stopServer()}: the player's own quit calls it through
 * {@code halt}, and a mod that wants the world closed calls {@code halt} or this method
 * directly. Cancelling at the head therefore covers all of them, and the player's own quit is
 * separated by {@code QuitIntent} — the client marks the moment it starts tearing the level
 * down, and a stop inside that window passes through untouched.
 *
 * <h3>Why the bypass flag is a thread-local</h3>
 * When the player answers "yes, close it", {@link KickGuard#accept} calls {@code halt(false)}
 * again. The bypass is set for the duration of that call on this thread only, so it cannot leak
 * into an unrelated shutdown happening on another thread.
 *
 * <p>Fail-open, same as every other guard here: an exception means the shutdown proceeds.
 */
@Mixin(MinecraftServer.class)
public abstract class MixinMinecraftServerStop {

    @Inject(method = "m_7041_", at = @At("HEAD"), cancellable = true, remap = false)
    private void undying$guardForcedShutdown(CallbackInfo ci) {
        try {
            if (KickGuard.interceptStopServer((MinecraftServer) (Object) this)) {
                ci.cancel();
            }
        } catch (Throwable t) {
            ModLog.swallowed("MixinMinecraftServerStop", t);
        }
    }

    /**
     * {@code halt(boolean)} — the outer call.
     *
     * <h3>Why cancelling only {@code stopServer} is not enough</h3>
     * {@code halt} does not just call {@code stopServer}: after it returns it also shuts down
     * the executor pools and the reaper thread. Cancelling the inner call and letting the outer
     * one continue would leave a server whose tick loop is technically alive but whose threads
     * are gone — worse than either outcome. So the whole halt is cancelled first, and when the
     * player does accept, {@code KickGuard.accept} calls {@code halt} again with the bypass set.
     */
    @Inject(method = "m_7570_", at = @At("HEAD"), cancellable = true, remap = false)
    private void undying$guardHalt(boolean waitForServer, CallbackInfo ci) {
        try {
            if (KickGuard.interceptStopServer((MinecraftServer) (Object) this)) {
                ci.cancel();
            }
        } catch (Throwable t) {
            ModLog.swallowed("MixinMinecraftServerStop.halt", t);
        }
    }
}

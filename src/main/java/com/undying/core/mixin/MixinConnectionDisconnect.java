package com.undying.core.mixin;

import com.undying.core.KickGuard;
import com.undying.core.ModLog;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Holds a disconnect and turns it into a question.
 *
 * <h3>Why this method</h3>
 * {@code disconnect(Component)} is the single funnel for "this player is leaving, now" —
 * vanilla's own kick, a mod calling {@code connection.disconnect(...)}, and the disconnect
 * that follows a ban all end up here. Cancelling at the head means the player stays connected
 * and keeps playing while the question is on screen; the answer re-enters the same method with
 * {@link KickGuard}'s bypass set, so there is exactly one code path that actually disconnects.
 *
 * <h3>What this cannot tell you</h3>
 * Which mod did it. By the time the call arrives it is just a stack frame, so the dialog says
 * what is checkable — the command that ran this tick, or the thread — rather than inventing a
 * mod name. See {@code KickGuard.describeOrigin}.
 *
 * <p>Fail-open: if anything here throws, the disconnect proceeds. Trapping a player in a world
 * because our guard broke would be worse than the interruption it was meant to prevent.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class MixinConnectionDisconnect {

    @Inject(method = "m_9942_", at = @At("HEAD"), cancellable = true, remap = false)
    private void undying$guardDisconnect(Component reason, CallbackInfo ci) {
        try {
            ServerPlayer player = ((ServerGamePacketListenerImpl) (Object) this).player;
            if (KickGuard.interceptDisconnect(player, reason)) {
                ci.cancel();
            }
        } catch (Throwable t) {
            ModLog.swallowed("MixinConnectionDisconnect", t);
        }
    }
}

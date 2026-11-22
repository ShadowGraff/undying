package com.undying.core.mixin;

import com.undying.core.Environment;
import com.undying.core.ProtectionBaseline;
import com.undying.core.ModConfig;
import com.undying.core.UndyingMod;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayer.class)
public abstract class MixinServerPlayer {

    /**
     * The death decision, taken <b>before</b> the death packet is sent.
     *
     * <h3>Why here and not in LivingEntity.die</h3>
     * {@code ServerPlayer.die} sends {@code ClientboundPlayerCombatKillPacket} before calling
     * {@code super.die()}. Cancelling in the superclass therefore let the client show a death
     * screen for a death that never happened, while the pre-super work had already run. Moving the
     * hook up one class means a cancelled death is cancelled before anything observable happens.
     *
     * <p>{@code DeathIntercept} is shared with the old hook so both paths agree; it consumes the
     * suicide permit, so calling it twice cannot leak a second death.
     */
    @Inject(method = "m_6667_", at = @At("HEAD"), cancellable = true, remap = false)
    private void undying$onDieEarly(net.minecraft.world.damagesource.DamageSource src, CallbackInfo ci) {
        try {
            if (com.undying.core.DeathIntercept.shouldCancel((ServerPlayer) (Object) this)) {
                ci.cancel();
            }
        } catch (Throwable t) {
            com.undying.core.ModLog.swallowed("MixinServerPlayer.onDieEarly", t);
        }
    }

    @Inject(method = "m_143403_", at = @At("HEAD"), cancellable = true, remap = false)
    private void undying$onSetGameMode(GameType gt, CallbackInfoReturnable<Boolean> cir) {
        if (!ProtectionBaseline.master() || !ModConfig.GAMEMODE_LOCK.get()) return;
        ServerPlayer self = (ServerPlayer) (Object) this;
        if (!Environment.isAllowed(self)) return;   // single-player only
        // A change the player asked for in our own panel is not tampering. Checked before the
        // OP test on purpose: the OP state is granted late (and can be revoked), so relying on
        // it alone made the same button work sometimes and fail other times.
        if (com.undying.core.QuickActions.isPlayerRequest()) return;
        // OP players can switch freely
        if (self.server.getPlayerList().isOp(self.getGameProfile())) return;
        cir.setReturnValue(false);
        UndyingMod.LOGGER.debug("[Undying] Blocked gamemode: {} -> {}", self.getName().getString(), gt.getName());
    }
}

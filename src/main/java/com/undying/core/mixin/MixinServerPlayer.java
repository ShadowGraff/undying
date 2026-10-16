package com.undying.core.mixin;

import com.undying.core.Environment;
import com.undying.core.ModConfig;
import com.undying.core.UndyingMod;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayer.class)
public abstract class MixinServerPlayer {

    @Inject(method = "m_143403_", at = @At("HEAD"), cancellable = true, remap = false)
    private void undying$onSetGameMode(GameType gt, CallbackInfoReturnable<Boolean> cir) {
        if (!ModConfig.MASTER_TOGGLE.get() || !ModConfig.GAMEMODE_LOCK.get()) return;
        ServerPlayer self = (ServerPlayer) (Object) this;
        if (!Environment.isAllowed(self)) return;   // single-player only
        // OP players can switch freely
        if (self.server.getPlayerList().isOp(self.getGameProfile())) return;
        cir.setReturnValue(false);
        UndyingMod.LOGGER.debug("[Undying] Blocked gamemode: {} -> {}", self.getName().getString(), gt.getName());
    }
}

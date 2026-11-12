package com.undying.core.mixin;

import com.undying.core.TeleportGuard;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.RelativeMovement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Set;

/**
 * The two teleport entry points that move a player to a specific place.
 *
 * <h3>Why exactly these two</h3>
 * {@code m_264318_} (with a relative-movement set) is what {@code /tp} and most mods reach for;
 * {@code m_8999_} is the simpler variant that takes only a rotation. Hooking both covers the
 * "put the player somewhere" case without touching the movement paths that would be felt as
 * breakage:
 *
 * <ul>
 *   <li>{@code teleportTo(double, double, double)} — ender pearls, chorus fruit, ordinary
 *       repositioning. Not hooked on purpose; intercepting it would stop vanilla mechanics.</li>
 *   <li>{@code setPos} / {@code moveTo} / {@code changeDimension} — not hooked. A mod that
 *       uses these to move a player will not be seen, and that is a stated limit rather than
 *       an oversight.</li>
 * </ul>
 *
 * <p>Both methods are cancelled at HEAD, so nothing moves. The destination is held in
 * {@link TeleportGuard} and the same teleport is re-issued after the player answers.
 */
@Mixin(ServerPlayer.class)
public abstract class MixinServerPlayerTeleport {

    /** teleportTo(ServerLevel, x, y, z, Set&lt;RelativeMovement&gt;, yRot, xRot) → boolean */
    @Inject(method = "m_264318_", at = @At("HEAD"), cancellable = true, remap = false)
    private void undying$guardTeleport(ServerLevel level, double x, double y, double z,
                                       Set<RelativeMovement> relatives, float yaw, float pitch,
                                       CallbackInfoReturnable<Boolean> cir) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        if (TeleportGuard.intercept(self, level, x, y, z, yaw, pitch)) {
            // The method reports success by its return value; a refused teleport did not move
            // anyone, so it must not claim it did.
            cir.setReturnValue(false);
        }
    }

    /** teleportTo(ServerLevel, x, y, z, yRot, xRot) → void */
    @Inject(method = "m_8999_", at = @At("HEAD"), cancellable = true, remap = false)
    private void undying$guardTeleportSimple(ServerLevel level, double x, double y, double z,
                                             float yaw, float pitch, CallbackInfo ci) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        if (TeleportGuard.intercept(self, level, x, y, z, yaw, pitch)) {
            ci.cancel();
        }
    }
}

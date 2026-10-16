package com.undying.core.mixin;

import com.mojang.authlib.GameProfile;
import com.undying.core.ModConfig;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * OP4 hardening: prevents OP permission from being revoked.
 *
 * Intercepts PlayerList.deop(GameProfile): while immortal mode is on, no source
 * (commands, other mods, scripts) can revoke the player's OP, except for the
 * whitelist (currently empty).
 *
 * The design follows the same idea as "gamemode lock": a whitelist filter that only
 * lets whitelisted players through.
 */
@Mixin(PlayerList.class)
public abstract class MixinPlayerList {

    /**
     * Resolves the server this PlayerList belongs to, so the environment gate can run.
     *
     * <p>{@code PlayerList} itself has no public server accessor in 1.20.1, so this
     * goes through the mixin-extended instance: {@code PlayerList} is constructed by
     * {@code MinecraftServer} and holds it in a field. Falling back to "blocked" when
     * it cannot be resolved keeps the single-player-only guarantee intact.
     */
    private boolean undying$serverAllowed() {
        try {
            // PlayerList has a `server` field in 1.20.1 (SRG: f_11196_)
            java.lang.reflect.Field f = net.minecraft.server.players.PlayerList.class
                    .getDeclaredField("f_11196_");
            f.setAccessible(true);
            Object server = f.get(this);
            if (server instanceof net.minecraft.server.MinecraftServer ms) {
                return com.undying.core.Environment.isAllowed(ms);
            }
        } catch (Throwable t) {
            com.undying.core.ModLog.swallowed("MixinPlayerList.serverAllowed", t);
        }
        return false;   // fail closed
    }

    @Inject(method = "m_5750_", at = @At("HEAD"), cancellable = true, remap = false)
    private void undying$guardDeop(GameProfile profile, CallbackInfo ci) {
        if (!ModConfig.MASTER_TOGGLE.get() || !ModConfig.OP_LOCK.get()) return;
        if (!undying$serverAllowed()) return;   // single-player only
        ci.cancel(); // OP cannot be revoked
    }

    @Inject(method = "m_5749_", at = @At("HEAD"), cancellable = true, remap = false)
    private void undying$guardOp(GameProfile profile, CallbackInfo ci) {
        if (!ModConfig.MASTER_TOGGLE.get() || !ModConfig.OP_LOCK.get()) return;
        // Granting OP is allowed (harmless); only revocation is blocked. The hook is kept
        // here so it can be refined later
    }
}

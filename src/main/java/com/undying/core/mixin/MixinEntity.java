package com.undying.core.mixin;

import com.undying.core.DeathLock;
import com.undying.core.Environment;
import com.undying.core.ModConfig;
import com.undying.core.ModLog;
import com.undying.core.Notify;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Death lock hardening: seal off the "direct removal" bypass.
 *
 * In 1.20.1 the decompiled implementation of Entity.kill() is:
 *     kill() = remove(RemovalReason.KILLED) + gameEvent(ENTITY_DIE)
 * It does not pass through hurt / setHealth / checkTotemDeathProtection / die at all, so
 * scripted instant-kills in some mods can bypass the death lock.
 *
 * ═══════════ Important change, 2026-10-05 ═══════════
 * [Problem with the original implementation]
 *   It used to be: `if (p.isDeadOrDying()) return;` - once the player was flagged as
 *   "dead", we voluntarily let every removal through. The intent was "do not interfere
 *   with normal death and respawn", but this check [can be forged from the outside]: an
 *   external implementation only has to drive the player's health to 0 (or set the death
 *   flag directly) and all of our subsequent interception steps aside automatically.
 *
 *   The consequence has been observed in practice: with this mod installed, `/kill` still
 *   kills the player.
 *
 * [New implementation]
 *   It no longer looks at isDeadOrDying(); it looks only at two signals that [cannot be
 *   forged from the outside]:
 *     (1) UndyingMod.PENDING_SUICIDES - our own /antigrief die command
 *     (2) UndyingMod.isDeathGranted(p) - written only by undying$onDie() on a real death
 *         path that [we] recognise, and expiring automatically after 60 seconds
 *
 *   If neither holds -> block everything.
 *
 *   At the same time MixinLivingEntity prevents the player's health from being written to 0,
 *   so the "drain health first, then remove" combination breaks at its first link.
 *
 * Note: real death + respawn still work normally: health reaches zero on death ->
 *    MixinLivingEntity.undying$onDie (or isDeathGranted) lets it through -> the
 *    remove(KILLED) in the respawn flow is permitted inside the authorisation window.
 */
@Mixin(Entity.class)
public abstract class MixinEntity {

    /** Whether this player is currently authorised to die */
    private static boolean undying$deathAllowed(ServerPlayer p) {
        if (DeathLock.consumeSuicide(p.getUUID())) {
            return true;  // our own /antigrief die
        }
        return DeathLock.isGranted(p);
    }

    @Inject(method = "m_6074_", at = @At("HEAD"), cancellable = true, remap = false)
    private void undying$guardKill(CallbackInfo ci) {
        if (!undying$isGuarded()) return;
        ServerPlayer p = (ServerPlayer) (Object) this;
        if (undying$deathAllowed(p)) return;

        ci.cancel();
        p.level().broadcastEntityEvent(p, (byte) 35);
        Notify.removalBlocked(p, "kill()");
    }

    @Inject(method = "m_142687_", at = @At("HEAD"), cancellable = true, remap = false)
    private void undying$guardRemove(Entity.RemovalReason reason, CallbackInfo ci) {
        if (!undying$isGuarded()) return;

        // Only block KILLED, not DISCARDED.
        // DISCARDED is the vanilla cleanup reason used when [switching dimension / discarding
        // the old player entity on respawn]; blocking it would leave two of you in the world,
        // followed by a chunk-manager NPE and a player who cannot move.
        //
        if (reason != Entity.RemovalReason.KILLED)
            return;

        ServerPlayer p = (ServerPlayer) (Object) this;
        if (undying$deathAllowed(p)) return;

        ci.cancel();
        p.level().broadcastEntityEvent(p, (byte) 35);

        // * Key fix (2026-10-05): leave the death animation
        //
        // Vanilla tickDeath() increments the death timer by 1 each tick and calls
        // remove(KILLED) once it reaches 20. It starts counting when health hits 0; after we
        // restore health to 1.0 it [does not reset automatically], so it re-sends remove once
        // per second -> we block it -> the next second it comes again -> the player is stuck forever.
        //
        // Clear it here: since we have decided this player should not die, we must also leave
        // the death flow, otherwise merely blocking remove() just locks the player in a "half alive, half dead" state.
        try {
            ((LivingEntityDeathTimerAccess) (Object) p).undying$setDeathTime(0);
        } catch (Throwable t) {
            ModLog.swallowed("MixinEntity.undying$guardRemove.deathTime", t);
        }

        Notify.removalBlocked(p, "remove(" + reason + ")");
    }

    /**
     * Shared gate for both injections.
     *
     * <p>Includes the environment check: this mod is single-player only, so on a
     * dedicated server, a LAN-published world, or a client connected to a remote
     * server, the death lock must not engage at all. Putting it here rather than in
     * each injector guarantees both paths are covered.
     */
    private boolean undying$isGuarded() {
        if (!ModConfig.MASTER_TOGGLE.get() || !ModConfig.DEATH_LOCK.get()) return false;
        if (!((Object) this instanceof ServerPlayer p)) return false;
        return Environment.isAllowed(p);
    }
}
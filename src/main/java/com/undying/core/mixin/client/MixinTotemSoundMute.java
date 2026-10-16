package com.undying.core.mixin.client;

import com.undying.core.ModLog;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mutes the <i>totem of undying</i> sound effect.
 *
 * ═══════════════════════════════════════════════════════════
 *  Why it is needed (user request)
 * ═══════════════════════════════════════════════════════════
 *
 * This mod's hidden totem is a "real totem": on death MixinLivingEntity.undying$ensureTotem
 * temporarily puts a totem of undying into the offhand so that vanilla runs the
 * complete totem flow (event / sound / particles all behave correctly).
 * The benefit is behaviour identical to vanilla; the cost is that <i>vanilla's loud
 * blast plays every single time</i> ——
 * and our totem triggers almost constantly, so every "kill" blasts the ears.
 *
 * User request: turn the sound off; make the particles less conspicuous (they block the view).
 *
 * <i>Approach: intercept at the client-side sound engine layer</i>
 *
 * Rationale:
 *   · More robust than intercepting "who plays it" —— no need to guess which method
 *     plays the totem sound. That method lives in a Forge patch and its name is hard
 *     to pin down (I guessed wrong the first time and took isHolding for it).
 *   · More accurate than intercepting server-side —— the server does not play sounds
 *     at all; sounds are played client-side after the packet is received.
 *   · It only affects local playback and changes no game logic: the totem still works,
 *     is still consumed, and LivingUseTotemEvent still fires; you just do not hear it.
 *
 * ⚠️ Only the <i>totem</i> sound is muted; every other sound is passed through.
 * ⚠️ Cancelling directly does not leak resources —— the sound object is never created,
 *    so there is nothing to release.
 *    (Changing the volume to 0 and passing it through was considered, but volume is a
 *     final field so it cannot be changed, and that would additionally create a silent
 *     instance, wasting more.)
 */
@Mixin(SoundEngine.class)
public abstract class MixinTotemSoundMute {

    /** Id of the totem of undying sound. Compared as a plain string to avoid depending on how Holder resolves values. */
    private static final String TOTEM_USE_ID = "minecraft:item.totem.use";

    @Inject(method = "m_120274_", at = @At("HEAD"), cancellable = true, remap = false)
    private void undying$muteTotemSound(SoundInstance sound, CallbackInfo ci) {
        try {
            if (sound == null || sound.getLocation() == null) return;
            if (!TOTEM_USE_ID.equals(sound.getLocation().toString())) return;
            ci.cancel();
        } catch (Throwable t) {
            // On any error, pass the sound through; never swallow a legitimate sound, but always log it
            ModLog.swallowed("MixinTotemSoundMute", t);
        }
    }
}

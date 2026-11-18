package com.undying.core.mixin.client;

import com.undying.core.ModLog;
import com.undying.core.QuitIntent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Records that the player themself asked to leave the world.
 *
 * <h3>Why this is the deciding signal for the shutdown guard</h3>
 * "Save and Quit" does not stop the server directly: it clears the level, which stops the
 * integrated server as a consequence. A mod that wants the world closed skips all of that and
 * calls the shutdown directly. So the honest question is not "who called stopServer" (there is
 * no way to know) but "did the client start tearing the level down a moment ago" — and that is
 * exactly what {@code clearLevel} marks.
 *
 * <p>Both overloads are hooked: the no-argument form is what the pause screen reaches, and the
 * {@code Screen} form is what it calls internally. Marking twice costs nothing because the mark
 * is a timestamp, not a flag.
 *
 * <h3>The limitation, written where it can be found</h3>
 * A mod that calls {@code Minecraft.clearLevel} or {@code Minecraft.close} directly would be
 * read as the player and would not be questioned. That is a real hole and it is accepted: the
 * alternative (questioning everything) makes the game hard to close, which is worse.
 */
@Mixin(Minecraft.class)
public abstract class MixinMinecraftClearLevel {

    @Inject(method = "m_91399_", at = @At("HEAD"), remap = false)
    private void undying$markQuit(CallbackInfo ci) {
        try {
            QuitIntent.mark();
        } catch (Throwable t) {
            ModLog.swallowed("MixinMinecraftClearLevel.mark", t);
        }
    }

    @Inject(method = "m_91320_", at = @At("HEAD"), remap = false)
    private void undying$markQuitWithScreen(Screen screen, CallbackInfo ci) {
        try {
            QuitIntent.mark();
        } catch (Throwable t) {
            ModLog.swallowed("MixinMinecraftClearLevel.markScreen", t);
        }
    }
}

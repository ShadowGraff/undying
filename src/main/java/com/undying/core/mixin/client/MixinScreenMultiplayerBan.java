package com.undying.core.mixin.client;

import com.undying.core.ModLog;
import com.undying.core.client.MultiplayerGuard;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Disables the multiplayer entrances in the client GUI.
 *
 * ═══════════════════════════════════════════════════════════
 *  What it does
 * ═══════════════════════════════════════════════════════════
 *
 * Whenever a screen has finished laying itself out, the widgets it registered are walked and
 * any whose translation key is {@code menu.multiplayer} or {@code menu.shareToLan} gets
 * {@code active = false}. The buttons stay visible but greyed out and unclickable, with a
 * tooltip explaining why.
 *
 * <h3>Where to hook — this took four attempts, so the reasoning is recorded</h3>
 *
 * <b>Attempt 1: {@code addRenderableWidget} at {@code HEAD}.</b> The natural choke point —
 * every widget is registered through it. The LAN button obeyed; the multiplayer button did
 * not. {@code TitleScreen} shows why:
 * <pre>
 *   addRenderableWidget(multiplayerButton);       // ← the ban was applied here
 *   multiplayerButton.active = warning == null;   // ← then vanilla overwrote it
 * </pre>
 *
 * <b>Attempt 2: the same hook at {@code RETURN}.</b> Still wrong. That point is the moment
 * {@code addRenderableWidget} returns, which is <i>before</i> the line above runs. Vanilla
 * assigns {@code active} in the caller, after the call, so no injection point inside the
 * callee can win.
 *
 * <b>Attempt 3: {@code Screen.init(Minecraft, int, int)} at {@code RETURN}.</b> That method
 * is {@code public final} and no screen overrides it — {@code TitleScreen} and
 * {@code PauseScreen} override the {@code protected void init()} it calls. Being final, it
 * cannot be overridden, so it is a genuine single point that runs for every screen; and
 * because it is the outermost frame, it returns only after all of the screen's own
 * {@code active} assignments have happened.
 *
 * <b>Attempt 4 (2026-11-10): the above is still not enough, because resize bypasses it.</b>
 * Toggling fullscreen or changing the GUI scale does <i>not</i> call
 * {@code init(Minecraft, int, int)}. Vanilla's path is:
 * <pre>
 *   resize(w, h)            m_6574_   → sets width/height only
 *     repositionElements()  m_267719_
 *       rebuildWidgets()    m_232761_
 *         clearWidgets(); init();       ← buttons recreated, hook never runs
 * </pre>
 * Result: after a fullscreen toggle the "Multiplayer" button was clickable again, and stayed
 * clickable until the screen was re-created from scratch. Reproducible every time.
 * <p>Fixed with a per-tick sweep in {@link MultiplayerGuard}, which does not depend on any
 * particular rebuild path. This hook is kept because it still gives the immediate result on
 * screen creation; the sweep is the net underneath it. Both call
 * {@link MultiplayerGuard#enforce(Screen)} so the rule lives in exactly one place.
 *
 * <h3>Why the translation key, not the rendered text</h3>
 * The obvious implementation compares {@code widget.getMessage().getString()} against
 * "Multiplayer". That is wrong: {@code getString()} returns the <i>localised</i> text, so on a
 * Chinese client it yields "多人游戏" and the check would silently never match — precisely the
 * class of bug this project keeps hitting. The translation key underneath is
 * language-independent.
 *
 * <h3>Why {@code active = false} and not {@code visible = false}</h3>
 * Hiding the button makes players think the mod broke the game; greying it out reads as
 * "disabled on purpose". Disabled is also the honest representation — the feature is not
 * missing, it is refused.
 *
 * ⚠️ Client-side ban only. It stops the player from <i>reaching</i> multiplayer; it cannot
 *    stop someone from starting a modded server. That is {@link com.undying.core.Environment}'s
 *    job. The two are complementary and neither replaces the other.
 * ⚠️ Fail-open here, deliberately: if anything throws, the widget is left alone. A broken GUI
 *    is worse than an un-banned button, and the server-side gate still holds.
 */
@Mixin(Screen.class)
public abstract class MixinScreenMultiplayerBan {

    @Inject(method = "m_6575_", at = @At("RETURN"), remap = false)
    private void undying$banMultiplayerEntrances(Minecraft minecraft, int width, int height,
                                                 CallbackInfo ci) {
        try {
            MultiplayerGuard.enforce((Screen) (Object) this);
        } catch (Throwable t) {
            // Fail open: leave the widgets untouched rather than risk a broken screen.
            ModLog.swallowed("MixinScreenMultiplayerBan", t);
        }
    }
}

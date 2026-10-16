package com.undying.core.mixin.client;

import com.undying.core.ModLog;
import com.undying.core.MultiplayerBan;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Disables the multiplayer entrances in the client GUI.
 *
 * ═══════════════════════════════════════════════════════════
 *  What it does
 * ═══════════════════════════════════════════════════════════
 *
 * Once a screen has finished laying itself out, this walks the widgets it registered and sets
 * {@code active = false} on any whose translation key is {@code menu.multiplayer} or
 * {@code menu.shareToLan}. The buttons stay visible but greyed out and unclickable, with a
 * tooltip explaining why.
 *
 * <h3>Where to hook — this took three attempts, so the reasoning is recorded</h3>
 *
 * <b>Attempt 1: {@code addRenderableWidget} at {@code HEAD}.</b> The natural choke point — every
 * widget is registered through it. The LAN button obeyed; the multiplayer button did not.
 * {@code TitleScreen} shows why:
 * <pre>
 *   addRenderableWidget(multiplayerButton);       // ← the ban was applied here
 *   multiplayerButton.active = warning == null;   // ← then vanilla overwrote it
 * </pre>
 *
 * <b>Attempt 2: the same hook at {@code RETURN}.</b> Still wrong. That point is the moment
 * {@code addRenderableWidget} returns, which is <i>before</i> the line above runs. Vanilla
 * assigns {@code active} in the caller, after the call, so no injection point inside the callee
 * can win.
 *
 * <b>Attempt 3 (this one): {@code Screen.init(Minecraft, int, int)} at {@code RETURN}.</b>
 * That method is {@code public final} and <b>no screen overrides it</b> — {@code TitleScreen} and
 * {@code PauseScreen} override the {@code protected void init()} it calls. Being final, it cannot
 * be overridden, so it is a genuine single point that runs for every screen; and because it is
 * the outermost frame, it returns only after all of the screen's own {@code active} assignments
 * have happened. Nothing runs afterwards to undo the ban.
 *
 * <p>This also covers screens that bypass {@code addRenderableWidget} entirely, since the whole
 * widget list is inspected rather than the call that added each entry.
 *
 * <h3>Why the translation key, not the rendered text</h3>
 * The obvious implementation compares {@code widget.getMessage().getString()} against
 * "Multiplayer". That is wrong: {@code getString()} returns the <i>localised</i> text, so on a
 * Chinese client it yields "多人游戏" and the check would silently never match — precisely the
 * class of bug this project keeps hitting. The translation key underneath is
 * language-independent.
 *
 * <p>The key is read by unwrapping the component: {@code getContents()} is asked whether it is a
 * {@link TranslatableContents}, and only then is {@code getKey()} read. No string parsing, and a
 * literal-text component simply does not match.
 *
 * <h3>Why {@code active = false} and not {@code visible = false}</h3>
 * Hiding the button makes players think the mod broke the game; greying it out reads as "disabled
 * on purpose". Disabled is also the honest representation — the feature is not missing, it is
 * refused. A tooltip carries the reason, so it is discoverable without reading logs.
 *
 * ⚠️ Client-side ban only. It stops the player from <i>reaching</i> multiplayer; it cannot stop
 *    someone from starting a modded server. That is {@link com.undying.core.Environment}'s job.
 *    The two are complementary and neither replaces the other.
 * ⚠️ Fail-open here, deliberately: if anything throws, the widget is left alone. A broken GUI is
 *    worse than an un-banned button, and the server-side gate still holds.
 */
@Mixin(Screen.class)
public abstract class MixinScreenMultiplayerBan {

    /**
     * Every widget the screen has registered.
     *
     * <p>Shadowed rather than read through a public getter: {@code children} is private, and the
     * public {@code children()} returns a wildcard type that cannot be iterated usefully here.
     */
    @Shadow(remap = false)
    private List<GuiEventListener> f_96540_;

    @Inject(method = "m_6575_", at = @At("RETURN"), remap = false)
    private void undying$banMultiplayerEntrances(Minecraft minecraft, int width, int height,
                                                 CallbackInfo ci) {
        try {
            if (f_96540_ == null) return;

            for (GuiEventListener listener : f_96540_) {
                banWidget(listener);
            }
        } catch (Throwable t) {
            // Fail open: leave the widgets untouched rather than risk a broken screen.
            ModLog.swallowed("MixinScreenMultiplayerBan", t);
        }
    }

    /**
     * Disable one widget if it is a banned multiplayer entrance.
     *
     * <p>Isolated so that a failure on an unexpected widget type cannot abort the scan and leave
     * a later button uninspected.
     */
    private static void banWidget(GuiEventListener listener) {
        try {
            if (!(listener instanceof AbstractWidget w)) return;

            Component message = w.getMessage();
            if (message == null) return;

            ComponentContents contents = message.getContents();
            if (!(contents instanceof TranslatableContents translatable)) return;

            String key = translatable.getKey();
            if (!MultiplayerBan.isBannedKey(key)) return;

            w.active = false;

            // Explain why, on hover. Cheap, and it turns "the mod broke my menu" into
            // "this is disabled on purpose".
            try {
                w.setTooltip(Tooltip.create(
                        Component.literal(MultiplayerBan.label(key) + " — disabled: ")
                                .append(Component.translatable(key))
                                .append(Component.literal(" (" + MultiplayerBan.reason() + ")"))));
            } catch (Throwable t) {
                ModLog.swallowed("MixinScreenMultiplayerBan.tooltip", t);
            }

            MultiplayerBan.logOnce();
        } catch (Throwable t) {
            ModLog.swallowed("MixinScreenMultiplayerBan.banWidget", t);
        }
    }
}

package com.undying.core.client;

import com.undying.core.ModLog;
import com.undying.core.Networking;
import com.undying.core.UndyingMod;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Detects a double-tap of jump and asks the server to toggle flight.
 *
 * <h3>Why the client detects it</h3>
 * The jump key only exists on the client. Watching the server for two {@code ServerboundMovePlayerPacket}
 * jumps would mean guessing from packet timing, and would fire on a player bouncing down a hill.
 *
 * <h3>Why a tap is decided on release, but the toggle fires on the next press</h3>
 * Flight is steered by <i>holding</i> jump to ascend. So a press only counts as a tap if it is
 * released within {@link #HOLD_MS}; a longer press is an ascent and leaves no pending tap.
 *
 * <p>The first version instead cleared the pending tap <i>during</i> a long press. That broke
 * landing in a way that is easy to reproduce and hard to guess: ascend by holding jump, let go,
 * then double-tap — and nothing happened, because the hold had wiped the state and the two taps
 * after it only produced one candidate. Creative flight does not behave that way, and the player
 * reasonably reported it as "cannot cancel flight with a double tap".
 *
 * <p>Recording the candidate at <b>release</b> and firing at the <b>next press</b> fixes it and
 * keeps both things that matter: holding to ascend never toggles (no candidate is recorded), and
 * a double-tap that ends in a held key still takes off and climbs (the toggle fires on press,
 * before the hold begins) — the same feel as creative.
 *
 * <p>No state is sent: this only says "the player double-tapped". Whether that means take off,
 * land, or nothing at all is decided on the server, which also owns the switches.
 */
@Mod.EventBusSubscriber(modid = UndyingMod.MODID, value = Dist.CLIENT)
public final class FlightTrigger {

    /** Two taps must be at most this far apart, release to press. */
    private static final long DOUBLE_TAP_MS = 300L;
    /** A press released within this time is a tap; anything longer is a hold. */
    private static final long HOLD_MS = 180L;

    private static boolean wasDown;
    private static long pressedAt;
    /** Release time of the last press that qualified as a tap; 0 = none pending. */
    private static long lastTapAt;

    private FlightTrigger() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.level == null) {
                clear();
                return;
            }
            // A key pressed while a menu is open belongs to the menu, not to flight. Dropping
            // the state here also stops "opened inventory mid-tap, closed it" from taking off.
            if (mc.screen != null) {
                wasDown = false;
                lastTapAt = 0;
                return;
            }

            boolean down = mc.options.keyJump.isDown();
            long now = System.currentTimeMillis();

            if (down && !wasDown) {
                pressedAt = now;
                if (lastTapAt != 0 && now - lastTapAt <= DOUBLE_TAP_MS) {
                    lastTapAt = 0;
                    requestToggle(mc);
                }
            } else if (!down && wasDown) {
                // Released: only a short press becomes a candidate for the next tap.
                lastTapAt = (now - pressedAt <= HOLD_MS) ? now : 0;
            }

            wasDown = down;
        } catch (Throwable t) {
            ModLog.swallowed("FlightTrigger.tick", t);
        }
    }

    private static void requestToggle(Minecraft mc) {
        // Creative already flies and spectators cannot be touched; asking the server in those
        // cases would only produce a message explaining why nothing happened.
        if (mc.player == null || mc.player.isCreative() || mc.player.isSpectator()) return;
        Networking.sendToggleFly();
    }

    private static void clear() {
        wasDown = false;
        lastTapAt = 0;
        pressedAt = 0;
    }
}

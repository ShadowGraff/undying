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
 * <h3>Why a held key does not count</h3>
 * Flight is steered by holding jump to ascend. If only the interval between presses were checked,
 * every ascent would end in a landing toggle. Releasing and pressing again within
 * {@link #DOUBLE_TAP_MS} is what counts as a tap; a press held longer than {@link #HOLD_MS} clears
 * the pending tap so it cannot be completed later.
 *
 * <p>No state is sent: this only says "the player double-tapped". Whether that means take off,
 * land, or nothing at all is decided on the server, which also owns the switches.
 */
@Mod.EventBusSubscriber(modid = UndyingMod.MODID, value = Dist.CLIENT)
public final class FlightTrigger {

    /** Two presses must be at most this far apart. */
    private static final long DOUBLE_TAP_MS = 300L;
    /** A press held longer than this is a hold, not a tap. */
    private static final long HOLD_MS = 180L;

    private static boolean wasDown;
    private static long firstTapAt;
    private static long heldSince;

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
                firstTapAt = 0;
                return;
            }

            boolean down = mc.options.keyJump.isDown();
            long now = System.currentTimeMillis();

            if (down) {
                if (!wasDown) {
                    heldSince = now;
                    if (firstTapAt != 0 && now - firstTapAt <= DOUBLE_TAP_MS) {
                        firstTapAt = 0;
                        requestToggle(mc);
                    } else {
                        firstTapAt = now;
                    }
                } else if (now - heldSince > HOLD_MS) {
                    // Held: this is an ascent, so the previous tap can no longer pair with it.
                    firstTapAt = 0;
                }
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
        firstTapAt = 0;
        heldSince = 0;
    }
}

package com.undying.core;

import com.undying.core.mixin.MixinIntegratedServerLan;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Client-only entry points.
 *
 * <p><b>Why this class exists at all.</b> Anything that names a
 * {@code net.minecraft.client.*} type has to live somewhere that is never loaded on a
 * dedicated server, otherwise the class fails to link with {@code NoClassDefFoundError} the
 * moment the server touches it. Both methods below are called from dual-sided code
 * ({@link Environment}, {@link Networking}), so they must stay behind this fence.
 */
@OnlyIn(Dist.CLIENT)
public class ClientHooks {

    /** Ask the client to open the creative item menu */
    public static void openGiveScreen() {
        net.minecraft.client.Minecraft.getInstance().setScreen(new CreativeGiveScreen());
    }

    /**
     * Is this world currently published to LAN?
     *
     * <p>Structural test, not a guess. Vanilla's {@code IntegratedServer.publishServer}
     * assigns a {@code LanServerPinger} to a field and starts it, then interrupts it and
     * clears the field when the world is closed. The field is therefore non-null exactly
     * while the world is open to the network — set by the same code path that implements the
     * feature, so it cannot disagree with it.
     *
     * <p>The earlier implementation inferred LAN from {@code getPort()} and
     * {@code getLocalIp()}. That compiled and looked plausible, but <b>did not fire during a
     * real LAN test</b>, leaving the mod active on a world other people could join. Rather
     * than keep tuning an unverified assumption on a security boundary, the signal is now the
     * mechanism itself.
     *
     * @param server any server; a dedicated server simply returns false here because it is
     *               not an integrated server, and {@code isSingleplayer()} already covers it
     * @return true only when this is an integrated server that has been opened to LAN
     */
    public static boolean isLanPublished(MinecraftServer server) {
        try {
            if (!(server instanceof IntegratedServer integrated)) return false;
            return ((MixinIntegratedServerLan) (Object) integrated).undying$isPublishedToLan();
        } catch (Throwable t) {
            // "This signal says nothing", not "not published" — Environment OR-s the signals,
            // so returning false here cannot open the gate on its own.
            ModLog.swallowed("ClientHooks.isLanPublished", t);
            return false;
        }
    }
}

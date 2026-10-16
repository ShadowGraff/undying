package com.undying.core.mixin;

import net.minecraft.client.server.IntegratedServer;
import net.minecraft.client.server.LanServerPinger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * LAN state probe for {@link com.undying.core.ClientHooks#isLanPublished}.
 *
 * ═══════════════════════════════════════════════════════════
 *  Why this exists
 * ═══════════════════════════════════════════════════════════
 *
 * {@link com.undying.core.Environment} has to answer one question: "has this single-player
 * world been opened to the network?" Getting that wrong is a security hole, because the mod
 * grants invulnerability.
 *
 * The first implementation asked the server for its port and local IP
 * ({@code getPort() > 0 && getLocalIp() != null}). That compiled, looked reasonable, and
 * <b>did not work</b>: during a real "Open to LAN" test the gate never fired, so the mod
 * stayed active on a world other people could join. Why those two values did not behave as
 * assumed was never established — and an unverified assumption on a security boundary is not
 * something to keep.
 *
 * This replaces the guess with the actual mechanism. Vanilla's
 * {@code IntegratedServer.publishServer(...)} does exactly this:
 *
 * <pre>
 *   this.f_120018_ = new LanServerPinger(...);   // ← field set
 *   this.f_120018_.start();
 *   ...
 *   this.f_120018_.interrupt();                  // ← field cleared on close
 *   this.f_120018_ = null;
 * </pre>
 *
 * So {@code f_120018_ != null} is true <i>if and only if</i> the world is published. No port
 * number to interpret, no address to compare, no platform behaviour to assume: the field is
 * set by the same code path as the feature it describes, so it cannot disagree with it.
 *
 * <h3>Why a class mixin with {@code @Shadow}, not an interface with {@code @Accessor}</h3>
 * The first attempt declared this as an {@code interface} with {@code @Accessor}, copying the
 * shape of {@code SynchedEntityDataOwnerAccess}. That copy was wrong, and the runtime said so
 * outright:
 * <pre>
 *   InvalidMixinException: @Mixin target type mismatch:
 *   net.minecraft.client.server.IntegratedServer is not an interface
 * </pre>
 * Mixin pairs the mixin kind with the target kind: an interface mixin may only target an
 * interface. {@code IntegratedServer} is a concrete class, so this must be a class mixin, and
 * the field is reached with {@code @Shadow} instead of {@code @Accessor}.
 *
 * <p>The field name was verified against the 1.20.1 bytecode (javap:
 * {@code private net.minecraft.client.server.LanServerPinger f_120018_;}) and by
 * disassembling {@code publishServer} to confirm both the assignment and the null reset. The
 * SRG name is written literally with {@code remap = false}, matching every other mixin in
 * this project: this mod ships <b>no refmap</b>, so a development name would have nothing to
 * translate it at runtime.
 *
 * <p>Client-only: listed under {@code "client"} in the mixin config, so it is never applied
 * on a dedicated server, where {@code IntegratedServer} does not exist.
 */
@Mixin(IntegratedServer.class)
public abstract class MixinIntegratedServerLan {

    /**
     * The LAN pinger. Present only while the world is published to LAN.
     *
     * <p>Declared with the field's exact type ({@code LanServerPinger}, descriptor
     * {@code Lnet/minecraft/client/server/LanServerPinger;}). {@code @Shadow} validates the
     * type against the target, so {@code Object} would not be accepted even though the only
     * question ever asked is whether the value is null.
     */
    @Shadow(remap = false)
    private LanServerPinger f_120018_;

    /**
     * @return true when this world is currently open to LAN
     */
    public boolean undying$isPublishedToLan() {
        return f_120018_ != null;
    }
}

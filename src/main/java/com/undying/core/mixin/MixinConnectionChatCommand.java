package com.undying.core.mixin;

import com.undying.core.ModLog;
import com.undying.core.PlayerCommandMark;
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Stamps the tick when a <b>player</b> typed a command, so the suicide command can require it.
 *
 * <h3>What this single hook buys</h3>
 * Before it, any code in the process could run {@code /antigrief die} through the command
 * dispatcher and the death lock would treat it as "the player asked for this". After it, only a
 * command that arrived as a chat packet counts — command blocks, functions, and another mod's
 * {@code performCommand} all leave no stamp.
 *
 * <p>This is a packet-level hook on purpose: it is the one place in the server where "a human
 * typed this" is structurally true rather than inferred.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class MixinConnectionChatCommand {

    @Inject(method = "m_214047_", at = @At("HEAD"), remap = false)
    private void undying$markChatCommand(ServerboundChatCommandPacket packet, CallbackInfo ci) {
        try {
            // `player` is public on the listener; `server` is private, so the server is reached
            // through the player instead of shadowing a field (no refmap in this project, and a
            // shadowed private field would need one to survive reobfuscation).
            ServerGamePacketListenerImpl self = (ServerGamePacketListenerImpl) (Object) this;
            ServerPlayer p = self.player;
            if (p != null && p.server != null) {
                PlayerCommandMark.noteChatCommand(p.server.getTickCount());
            }
        } catch (Throwable t) {
            ModLog.swallowed("MixinConnectionChatCommand", t);
        }
    }
}

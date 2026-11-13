package com.undying.core;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.function.Supplier;

/**
 * Network channel: the creative item menu (survival mode can take items straight from
 * the creative inventory).
 *  - OpenGiveScreenMessage (S2C): asks the client to open the creative item menu
 *  - GiveItemMessage        (C2S): client clicks an item → the server grants it to the inventory
 */
public class Networking {
    public static final String VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(UndyingMod.MODID, "main"),
            () -> VERSION, VERSION::equals, VERSION::equals);

    public static void register() {
        CHANNEL.registerMessage(0, OpenGiveScreenMessage.class,
                OpenGiveScreenMessage::encode, OpenGiveScreenMessage::decode, OpenGiveScreenMessage::handle);
        CHANNEL.registerMessage(1, GiveItemMessage.class,
                GiveItemMessage::encode, GiveItemMessage::decode, GiveItemMessage::handle);
        CHANNEL.registerMessage(2, AskTeleportMessage.class,
                AskTeleportMessage::encode, AskTeleportMessage::decode, AskTeleportMessage::handle);
        CHANNEL.registerMessage(3, TeleportAnswerMessage.class,
                TeleportAnswerMessage::encode, TeleportAnswerMessage::decode, TeleportAnswerMessage::handle);
        CHANNEL.registerMessage(4, ToggleFlyMessage.class,
                ToggleFlyMessage::encode, ToggleFlyMessage::decode, ToggleFlyMessage::handle);
    }

    // ── S2C: open the creative item menu ──
    public static class OpenGiveScreenMessage {
        public static void encode(OpenGiveScreenMessage m, FriendlyByteBuf b) {}
        public static OpenGiveScreenMessage decode(FriendlyByteBuf b) { return new OpenGiveScreenMessage(); }
        public static void handle(OpenGiveScreenMessage m, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                if (ctx.get().getDirection().getReceptionSide().isClient()) {
                    DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHooks.openGiveScreen());
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }

    // ── C2S: request an item grant ──
    public static class GiveItemMessage {
        private final String itemId;
        private final int count;

        public GiveItemMessage(String itemId, int count) {
            this.itemId = itemId;
            this.count = count;
        }

        public static void encode(GiveItemMessage m, FriendlyByteBuf b) {
            b.writeUtf(m.itemId);
            b.writeInt(m.count);
        }

        public static GiveItemMessage decode(FriendlyByteBuf b) {
            // ⚠️ Fixed 2026-09-19: this used to be readUtf(128), but ResourceLocation
            // allows longer values (modded item ids easily exceed 128). Overlong ids were
            // truncated into invalid ones → "I click and nothing happens".
            return new GiveItemMessage(b.readUtf(256), b.readInt());
        }

        public static void handle(GiveItemMessage m, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer p = ctx.get().getSender();
                if (p == null || m.count <= 0 || m.count > 64) return;
                Item it = ForgeRegistries.ITEMS.getValue(new ResourceLocation(m.itemId));
                if (it == null || it == Items.AIR) return;
                int give = Math.min(m.count, it.getMaxStackSize());
                ItemStack stack = new ItemStack(it, give);
                // ⚠️ Fixed 2026-09-19: the original implementation ignored the return value
                // of add(), so a full inventory made items vanish into thin air. Whatever does
                // not fit is now dropped at the player's feet.
                p.getInventory().add(stack);
                if (!stack.isEmpty()) p.drop(stack, false);
                p.getInventory().setChanged();
            });
            ctx.get().setPacketHandled(true);
        }
    }

    /** Convenience helper to send the S2C message to a specific player */
    public static void sendOpenScreen(ServerPlayer p) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new OpenGiveScreenMessage());
    }

    // ── Teleport guard ──
    //
    // The destination has to reach the client, because the question the player is answering is
    // "go here?". Asking "a teleport was blocked, allow it?" without the coordinates would be a
    // question nobody can answer. Registration order matters: ids are fixed, version stays "1"
    // only because both sides ship together in this mod.

    /** S2C: a teleport was intercepted; here is where it wanted to go. */
    public static class AskTeleportMessage {
        private final String dimension;
        private final double x, y, z;
        private final float yaw, pitch;
        private final String origin;

        public AskTeleportMessage(String dimension, double x, double y, double z,
                                  float yaw, float pitch, String origin) {
            this.dimension = dimension;
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.pitch = pitch;
            this.origin = origin;
        }

        public static void encode(AskTeleportMessage m, FriendlyByteBuf b) {
            b.writeUtf(m.dimension, 128);
            b.writeDouble(m.x);
            b.writeDouble(m.y);
            b.writeDouble(m.z);
            b.writeFloat(m.yaw);
            b.writeFloat(m.pitch);
            b.writeUtf(m.origin, 128);
        }

        public static AskTeleportMessage decode(FriendlyByteBuf b) {
            return new AskTeleportMessage(b.readUtf(128), b.readDouble(), b.readDouble(), b.readDouble(),
                    b.readFloat(), b.readFloat(), b.readUtf(128));
        }

        public static void handle(AskTeleportMessage m, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                if (ctx.get().getDirection().getReceptionSide().isClient()) {
                    DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                            () -> () -> ClientHooks.openTeleportAsk(m.dimension, m.x, m.y, m.z,
                                    m.yaw, m.pitch, m.origin));
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }

    /** C2S: the player's answer. */
    public static class TeleportAnswerMessage {
        private final boolean accept;

        public TeleportAnswerMessage(boolean accept) {
            this.accept = accept;
        }

        public static void encode(TeleportAnswerMessage m, FriendlyByteBuf b) {
            b.writeBoolean(m.accept);
        }

        public static TeleportAnswerMessage decode(FriendlyByteBuf b) {
            return new TeleportAnswerMessage(b.readBoolean());
        }

        public static void handle(TeleportAnswerMessage m, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer p = ctx.get().getSender();
                if (p == null) return;
                if (m.accept) TeleportGuard.confirm(p);
                else TeleportGuard.deny(p);
            });
            ctx.get().setPacketHandled(true);
        }
    }

    public static void sendTeleportAsk(ServerPlayer p, TeleportGuard.Pending pending) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> p),
                new AskTeleportMessage(pending.dimension.location().toString(),
                        pending.x, pending.y, pending.z, pending.yaw, pending.pitch, pending.origin));
    }

    /** Send the answer back; only called from the client thread. */
    public static void sendTeleportAnswer(boolean accept) {
        CHANNEL.sendToServer(new TeleportAnswerMessage(accept));
    }

    // ── Flight ──

    /**
     * C2S: "I double-tapped jump."
     *
     * <p>Carries no state at all. The server decides whether the player may fly and whether
     * this is a take-off or a landing, from the abilities it already owns. A packet that said
     * "make me fly" would be a packet another client could send regardless of the switch, and
     * the server would have to trust it.
     */
    public static class ToggleFlyMessage {
        public static void encode(ToggleFlyMessage m, FriendlyByteBuf b) {}
        public static ToggleFlyMessage decode(FriendlyByteBuf b) { return new ToggleFlyMessage(); }

        public static void handle(ToggleFlyMessage m, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer p = ctx.get().getSender();
                if (p == null) return;
                Flight.toggle(p);
            });
            ctx.get().setPacketHandled(true);
        }
    }

    /** Only called from the client thread. */
    public static void sendToggleFly() {
        CHANNEL.sendToServer(new ToggleFlyMessage());
    }
}

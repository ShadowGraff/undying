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
}

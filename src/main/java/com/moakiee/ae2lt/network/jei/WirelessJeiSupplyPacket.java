package com.moakiee.ae2lt.network.jei;

import java.util.List;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

public record WirelessJeiSupplyPacket(int containerId, int requestId, boolean take, List<ItemStack> items) {
    public void write(FriendlyByteBuf buffer) {
        buffer.writeVarInt(containerId);
        buffer.writeVarInt(requestId);
        buffer.writeBoolean(take);
        WirelessJeiStackCodec.write(buffer, items);
    }

    public static WirelessJeiSupplyPacket read(FriendlyByteBuf buffer) {
        int container = buffer.readVarInt();
        int request = buffer.readVarInt();
        boolean take = buffer.readBoolean();
        return new WirelessJeiSupplyPacket(container, request, take, WirelessJeiStackCodec.read(buffer));
    }

    public static void handle(WirelessJeiSupplyPacket packet, Supplier<NetworkEvent.Context> context) {
        var ctx = context.get();
        ctx.enqueueWork(() -> {
            var player = ctx.getSender();
            if (player != null) com.moakiee.ae2lt.network.PacketSender.sendToPlayer(player,
                    com.moakiee.ae2lt.logic.tianshu.terminal.WirelessJeiSupply.handle(player, packet));
        });
        ctx.setPacketHandled(true);
    }
}

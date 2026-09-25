package com.moakiee.ae2lt.network.jei;

import com.moakiee.ae2lt.logic.tianshu.terminal.WirelessJeiInventoryPlan;
import java.util.ArrayList;
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
        if (items.size() > WirelessJeiInventoryPlan.MAX_ENTRIES) throw new IllegalArgumentException("Too many JEI inputs");
        buffer.writeVarInt(items.size());
        for (var stack : items) buffer.writeItem(stack);
    }

    public static WirelessJeiSupplyPacket read(FriendlyByteBuf buffer) {
        int container = buffer.readVarInt();
        int request = buffer.readVarInt();
        boolean take = buffer.readBoolean();
        int size = buffer.readVarInt();
        if (size < 0 || size > WirelessJeiInventoryPlan.MAX_ENTRIES) throw new IllegalArgumentException("Too many JEI inputs");
        var stacks = new ArrayList<ItemStack>(size);
        for (int i = 0; i < size; i++) stacks.add(buffer.readItem());
        return new WirelessJeiSupplyPacket(container, request, take, List.copyOf(stacks));
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

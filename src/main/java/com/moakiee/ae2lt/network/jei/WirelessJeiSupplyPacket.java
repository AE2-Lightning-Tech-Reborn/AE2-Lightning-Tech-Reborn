package com.moakiee.ae2lt.network.jei;

import com.moakiee.ae2lt.logic.tianshu.terminal.WirelessJeiInventoryPlan;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

public record WirelessJeiSupplyPacket(int containerId, int requestId, boolean take, boolean craft, List<ItemStack> items) {
    public WirelessJeiSupplyPacket(int containerId, int requestId, boolean take, List<ItemStack> items) {
        this(containerId, requestId, take, false, items);
    }
    public void write(FriendlyByteBuf buffer) {
        buffer.writeVarInt(containerId);
        buffer.writeVarInt(requestId);
        buffer.writeBoolean(take);
        buffer.writeBoolean(craft);
        writeItems(buffer, items);
    }

    public static WirelessJeiSupplyPacket read(FriendlyByteBuf buffer) {
        int container = buffer.readVarInt();
        int request = buffer.readVarInt();
        boolean take = buffer.readBoolean();
        boolean craft = buffer.readBoolean();
        return new WirelessJeiSupplyPacket(container, request, take, craft, readItems(buffer));
    }

    static void writeItems(FriendlyByteBuf buffer, List<ItemStack> items) {
        if (items.size() > WirelessJeiInventoryPlan.MAX_ENTRIES) throw new IllegalArgumentException("Too many JEI inputs");
        buffer.writeVarInt(items.size());
        for (var stack : items) {
            if (stack.isEmpty() || stack.getCount() > WirelessJeiInventoryPlan.MAX_COUNT)
                throw new IllegalArgumentException("Invalid JEI ingredient count");
            // Vanilla's item codec stores a signed byte count; recipe totals can exceed 127.
            buffer.writeItemStack(stack.copyWithCount(1), false);
            buffer.writeVarInt(stack.getCount());
        }
    }

    static List<ItemStack> readItems(FriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        if (size < 0 || size > WirelessJeiInventoryPlan.MAX_ENTRIES) throw new IllegalArgumentException("Too many JEI inputs");
        var stacks = new ArrayList<ItemStack>(size);
        for (int i = 0; i < size; i++) {
            var stack = buffer.readItem();
            int count = buffer.readVarInt();
            if (stack.isEmpty() || count <= 0 || count > WirelessJeiInventoryPlan.MAX_COUNT)
                throw new IllegalArgumentException("Invalid JEI ingredient count");
            stacks.add(stack.copyWithCount(count));
        }
        return List.copyOf(stacks);
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

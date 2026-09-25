package com.moakiee.ae2lt.network.jei;

import com.moakiee.ae2lt.logic.tianshu.terminal.WirelessJeiInventoryPlan;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

public record WirelessJeiSupplyResultPacket(int containerId, int requestId, int status, List<ItemStack> items) {
    public void write(FriendlyByteBuf buffer) {
        buffer.writeVarInt(containerId);
        buffer.writeVarInt(requestId);
        buffer.writeVarInt(status);
        if (items.size() > WirelessJeiInventoryPlan.MAX_ENTRIES) throw new IllegalArgumentException("Too many JEI inputs");
        buffer.writeVarInt(items.size());
        for (var stack : items) buffer.writeItem(stack);
    }

    public static WirelessJeiSupplyResultPacket read(FriendlyByteBuf buffer) {
        int container = buffer.readVarInt();
        int request = buffer.readVarInt();
        int status = buffer.readVarInt();
        int size = buffer.readVarInt();
        if (size < 0 || size > WirelessJeiInventoryPlan.MAX_ENTRIES) throw new IllegalArgumentException("Too many JEI inputs");
        var stacks = new ArrayList<ItemStack>(size);
        for (int i = 0; i < size; i++) stacks.add(buffer.readItem());
        return new WirelessJeiSupplyResultPacket(container, request, status, List.copyOf(stacks));
    }

    public static void handle(WirelessJeiSupplyResultPacket packet, Supplier<NetworkEvent.Context> context) {
        var ctx = context.get();
        ctx.enqueueWork(() -> net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(
                net.minecraftforge.api.distmarker.Dist.CLIENT, () -> () -> {
                    if (net.minecraftforge.fml.ModList.get().isLoaded("jei"))
                        com.moakiee.ae2lt.client.JeiWirelessSupplyClient.receive(packet);
                }));
        ctx.setPacketHandled(true);
    }
}

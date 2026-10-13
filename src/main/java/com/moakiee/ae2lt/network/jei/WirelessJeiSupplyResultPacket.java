package com.moakiee.ae2lt.network.jei;

import java.util.List;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

/** 0 = failed, 1 = offer, 2 = delivered, 3 = crafting confirmation opened. */
public record WirelessJeiSupplyResultPacket(int containerId, int requestId, int status, List<ItemStack> items,
                                           List<ItemStack> craftable) {
    public WirelessJeiSupplyResultPacket(int containerId, int requestId, int status, List<ItemStack> items) {
        this(containerId, requestId, status, items, List.of());
    }
    public void write(FriendlyByteBuf buffer) {
        buffer.writeVarInt(containerId);
        buffer.writeVarInt(requestId);
        buffer.writeVarInt(status);
        WirelessJeiSupplyPacket.writeItems(buffer, items);
        WirelessJeiSupplyPacket.writeItems(buffer, craftable);
    }

    public static WirelessJeiSupplyResultPacket read(FriendlyByteBuf buffer) {
        int container = buffer.readVarInt();
        int request = buffer.readVarInt();
        int status = buffer.readVarInt();
        return new WirelessJeiSupplyResultPacket(container, request, status,
                WirelessJeiSupplyPacket.readItems(buffer), WirelessJeiSupplyPacket.readItems(buffer));
    }

    public static void handle(WirelessJeiSupplyResultPacket packet, Supplier<NetworkEvent.Context> context) {
        var ctx = context.get();
        ctx.enqueueWork(() -> net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(
                net.minecraftforge.api.distmarker.Dist.CLIENT, () -> () -> {
                    if (net.minecraftforge.fml.ModList.get().isLoaded("jei"))
                        com.moakiee.ae2lt.client.compat.JeiWirelessSupplyClient.receive(packet);
                }));
        ctx.setPacketHandled(true);
    }
}

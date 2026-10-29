package com.moakiee.ae2lt.network;

import com.moakiee.ae2lt.logic.management.ManagementSnapshot;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public record NetworkManagementSyncPacket(int menuId, int serial, String error, ManagementSnapshot snapshot) {
    public void write(FriendlyByteBuf b) { b.writeVarInt(menuId); b.writeVarInt(serial); b.writeUtf(error, 64); snapshot.write(b); }
    public static NetworkManagementSyncPacket decode(FriendlyByteBuf b) { return new NetworkManagementSyncPacket(b.readVarInt(), b.readVarInt(), b.readUtf(64), ManagementSnapshot.read(b)); }
    public static void handle(NetworkManagementSyncPacket packet, java.util.function.Supplier<net.minecraftforge.network.NetworkEvent.Context> supplier) {
        var context = supplier.get();
        context.setPacketHandled(true);
        context.enqueueWork(() -> {
            if (net.minecraft.client.Minecraft.getInstance().screen instanceof com.moakiee.ae2lt.client.gui.NetworkManagementScreen screen)
                screen.receive(packet);
        });
    }
}

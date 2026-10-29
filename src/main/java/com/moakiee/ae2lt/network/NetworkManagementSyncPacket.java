package com.moakiee.ae2lt.network;

import com.moakiee.ae2lt.logic.management.ManagementSnapshot;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record NetworkManagementSyncPacket(int menuId, int serial, String error, ManagementSnapshot snapshot) implements CustomPacketPayload {
    public static final Type<NetworkManagementSyncPacket> TYPE = new Type<>(ResourceLocation.parse("ae2lt:network_management_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, NetworkManagementSyncPacket> STREAM_CODEC = StreamCodec.ofMember(
            (p, b) -> {
                b.writeVarInt(p.menuId); b.writeVarInt(p.serial); b.writeUtf(p.error, 64);
                p.snapshot.write(b);
            }, b -> {
                int menu = b.readVarInt(), serial = b.readVarInt(); String error = b.readUtf(64);
                return new NetworkManagementSyncPacket(menu, serial, error, ManagementSnapshot.read(b));
            });
    @Override public Type<NetworkManagementSyncPacket> type() { return TYPE; }
    public static void handle(NetworkManagementSyncPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (net.minecraft.client.Minecraft.getInstance().screen instanceof com.moakiee.ae2lt.client.gui.NetworkManagementScreen screen)
                screen.receive(packet);
        });
    }
}

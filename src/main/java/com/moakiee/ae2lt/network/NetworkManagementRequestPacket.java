package com.moakiee.ae2lt.network;

import com.moakiee.ae2lt.logic.management.ManagementQuery;
import com.moakiee.ae2lt.menu.NetworkManagementMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record NetworkManagementRequestPacket(int menuId, ManagementQuery query) implements CustomPacketPayload {
    public static final Type<NetworkManagementRequestPacket> TYPE = new Type<>(ResourceLocation.parse("ae2lt:network_management_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, NetworkManagementRequestPacket> STREAM_CODEC = StreamCodec.ofMember(
            (p, b) -> { b.writeVarInt(p.menuId); p.query.write(b); }, b -> new NetworkManagementRequestPacket(b.readVarInt(), ManagementQuery.read(b)));
    @Override public Type<NetworkManagementRequestPacket> type() { return TYPE; }
    public static void handle(NetworkManagementRequestPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player && player.containerMenu instanceof NetworkManagementMenu menu
                    && menu.containerId == packet.menuId) menu.query(player, packet.query);
        });
    }
}

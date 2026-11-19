package com.moakiee.ae2lt.network;

import com.moakiee.ae2lt.logic.management.RemoteDeviceSession;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ReturnToDeviceManagementPacket(int menuId) implements CustomPacketPayload {
    public static final Type<ReturnToDeviceManagementPacket> TYPE = new Type<>(ResourceLocation.parse("ae2lt:return_device_management"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ReturnToDeviceManagementPacket> STREAM_CODEC = StreamCodec.ofMember(
            (p, b) -> b.writeVarInt(p.menuId), b -> new ReturnToDeviceManagementPacket(b.readVarInt()));
    @Override public Type<ReturnToDeviceManagementPacket> type() { return TYPE; }
    public static void handle(ReturnToDeviceManagementPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player && player.containerMenu.containerId == packet.menuId) {
                var remote = RemoteDeviceSession.locator(player);
                if (remote != null && remote.session() != null) remote.session().returnToDevices(player);
            }
        });
    }
}

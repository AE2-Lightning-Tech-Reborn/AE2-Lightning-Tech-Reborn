package com.moakiee.ae2lt.network.hub;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import com.moakiee.ae2lt.menu.hub.DeviceHubHost;
import com.moakiee.ae2lt.network.NetworkInit;

/** Client-selected entity id is only a hint; ownership and reach are checked on the server. */
public record OpenEntityDeviceHubPacket(int entityId) implements CustomPacketPayload {
    public static final Type<OpenEntityDeviceHubPacket> TYPE =
            new Type<>(NetworkInit.id("open_entity_device_hub"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenEntityDeviceHubPacket> STREAM_CODEC =
            StreamCodec.ofMember((packet, buf) -> buf.writeVarInt(packet.entityId()),
                    buf -> new OpenEntityDeviceHubPacket(buf.readVarInt()));

    @Override
    public Type<OpenEntityDeviceHubPacket> type() {
        return TYPE;
    }

    public static void handle(OpenEntityDeviceHubPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player
                    && player.level().getEntity(packet.entityId()) instanceof LivingEntity wearer) {
                DeviceHubHost.openForEntity(player, wearer);
            }
        });
    }
}

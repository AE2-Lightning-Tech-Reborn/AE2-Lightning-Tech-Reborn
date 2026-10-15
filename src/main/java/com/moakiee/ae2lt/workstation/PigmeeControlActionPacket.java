package com.moakiee.ae2lt.workstation;

import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record PigmeeControlActionPacket(UUID token, int sequence, int action, int slot, float yaw, float pitch) implements CustomPacketPayload {
    public static final Type<PigmeeControlActionPacket> TYPE = new Type<>(ResourceLocation.parse("ae2lt:pigmee_control_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PigmeeControlActionPacket> STREAM_CODEC = StreamCodec.ofMember(
            (v, b) -> { b.writeUUID(v.token); b.writeVarInt(v.sequence); b.writeVarInt(v.action); b.writeVarInt(v.slot); b.writeFloat(v.yaw); b.writeFloat(v.pitch); },
            b -> new PigmeeControlActionPacket(b.readUUID(), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readFloat(), b.readFloat()));
    @Override public Type<PigmeeControlActionPacket> type() { return TYPE; }
    public static void handle(PigmeeControlActionPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!PigmeeWorkstationRuntime.enabled() || !(context.player() instanceof ServerPlayer owner) || owner.isFakePlayer()) return;
            PigmeeWorkstationRuntime.manager(owner.server).controls().action(owner, packet.token, packet.sequence,
                    packet.action, packet.slot, packet.yaw, packet.pitch);
        });
    }
}

package com.moakiee.ae2lt.workstation;

import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record PigmeeControlInputPacket(UUID token, int sequence, int flags, float yaw, float pitch, boolean release) implements CustomPacketPayload {
    public static final Type<PigmeeControlInputPacket> TYPE = new Type<>(ResourceLocation.parse("ae2lt:pigmee_control_input"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PigmeeControlInputPacket> STREAM_CODEC = StreamCodec.ofMember(
            (v,b) -> { b.writeUUID(v.token); b.writeVarInt(v.sequence); b.writeVarInt(v.flags); b.writeFloat(v.yaw); b.writeFloat(v.pitch); b.writeBoolean(v.release); },
            b -> new PigmeeControlInputPacket(b.readUUID(), b.readVarInt(), b.readVarInt(), b.readFloat(), b.readFloat(), b.readBoolean()));
    @Override public Type<PigmeeControlInputPacket> type() { return TYPE; }
    public static void handle(PigmeeControlInputPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!PigmeeWorkstationRuntime.enabled() || !(context.player() instanceof ServerPlayer owner) || owner.isFakePlayer()) return;
            var leases = PigmeeWorkstationRuntime.manager(owner.server).controls();
            if (packet.release) leases.release(owner, packet.token);
            else leases.input(owner, packet.token, packet.sequence, packet.flags, packet.yaw, packet.pitch);
        });
    }
}

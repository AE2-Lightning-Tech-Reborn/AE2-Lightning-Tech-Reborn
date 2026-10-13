package com.moakiee.ae2lt.workstation;

import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record PigmeeControlStatePacket(UUID actor, UUID token, boolean active, boolean opening, float health, int food, float yaw, float pitch) implements CustomPacketPayload {
    public static final Type<PigmeeControlStatePacket> TYPE = new Type<>(ResourceLocation.parse("ae2lt:pigmee_control_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PigmeeControlStatePacket> STREAM_CODEC = StreamCodec.ofMember(
            (v,b) -> { b.writeUUID(v.actor); b.writeUUID(v.token); b.writeBoolean(v.active); b.writeBoolean(v.opening); b.writeFloat(v.health); b.writeVarInt(v.food); b.writeFloat(v.yaw); b.writeFloat(v.pitch); },
            b -> new PigmeeControlStatePacket(b.readUUID(), b.readUUID(), b.readBoolean(), b.readBoolean(), b.readFloat(), b.readVarInt(), b.readFloat(), b.readFloat()));
    @Override public Type<PigmeeControlStatePacket> type() { return TYPE; }
    static PigmeeControlStatePacket of(PigmeeControlLeases.Lease lease, boolean active) {
        return new PigmeeControlStatePacket(lease.actor.getUUID(), lease.token, active, false, lease.actor.getHealth(), lease.actor.getFoodData().getFoodLevel(), lease.actor.getYRot(), lease.actor.getXRot());
    }
    static PigmeeControlStatePacket opening(PigmeeControlLeases.Lease lease) {
        return new PigmeeControlStatePacket(lease.actor.getUUID(), lease.token, true, true, lease.actor.getHealth(), lease.actor.getFoodData().getFoodLevel(), lease.actor.getYRot(), lease.actor.getXRot());
    }
    public static void handle(PigmeeControlStatePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> com.moakiee.ae2lt.client.workstation.PigmeeControlScreen.receive(packet));
    }
}

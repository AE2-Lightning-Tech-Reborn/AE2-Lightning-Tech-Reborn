package com.moakiee.ae2lt.workstation;

import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Server-authored visual identity only. Never grants ownership or control. */
public record PigmeeIdentityPacket(UUID id, int variant) implements CustomPacketPayload {
    public static final Type<PigmeeIdentityPacket> TYPE = new Type<>(ResourceLocation.parse("ae2lt:pigmee_identity"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PigmeeIdentityPacket> STREAM_CODEC =
            StreamCodec.ofMember((value, buf) -> { buf.writeUUID(value.id); buf.writeVarInt(value.variant); },
                    buf -> new PigmeeIdentityPacket(buf.readUUID(), buf.readVarInt()));
    @Override public Type<PigmeeIdentityPacket> type() { return TYPE; }
    public static void handle(PigmeeIdentityPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> com.moakiee.ae2lt.client.workstation.PigmeeClientIdentity.accept(packet));
    }
}

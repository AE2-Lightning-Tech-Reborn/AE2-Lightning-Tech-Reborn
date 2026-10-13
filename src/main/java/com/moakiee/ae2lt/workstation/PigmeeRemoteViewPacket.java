package com.moakiee.ae2lt.workstation;

import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Server-chosen cache window; no coordinates or chunk requests are accepted from clients. */
public record PigmeeRemoteViewPacket(UUID token, boolean active, int x, int z) implements CustomPacketPayload {
    public static final Type<PigmeeRemoteViewPacket> TYPE = new Type<>(ResourceLocation.parse("ae2lt:pigmee_remote_view"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PigmeeRemoteViewPacket> STREAM_CODEC = StreamCodec.ofMember(
            (v,b) -> { b.writeUUID(v.token); b.writeBoolean(v.active); b.writeInt(v.x); b.writeInt(v.z); },
            b -> new PigmeeRemoteViewPacket(b.readUUID(), b.readBoolean(), b.readInt(), b.readInt()));
    @Override public Type<PigmeeRemoteViewPacket> type() { return TYPE; }
    public static void handle(PigmeeRemoteViewPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> com.moakiee.ae2lt.client.workstation.PigmeeRemoteChunkCache.receive(packet));
    }
}

package com.moakiee.ae2lt.network;

import com.moakiee.ae2lt.menu.PigmeeDimensionMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record PigmeeDimensionActionPacket(int containerId, int action, int dimensionId,
        String name, int frame, int center, boolean shared, String target, byte role, byte[] pattern) implements CustomPacketPayload {
    public PigmeeDimensionActionPacket(int containerId, int action, int dimensionId,
            String name, int frame, int center, boolean shared, String target, byte role) {
        this(containerId, action, dimensionId, name, frame, center, shared, target, role, new byte[0]);
    }
    public PigmeeDimensionActionPacket { pattern = pattern.clone(); }
    @Override public byte[] pattern() { return pattern.clone(); }
    public static final int CREATE = 0, BIND = 1, ENTER = 2, CONFIGURE = 3, MEMBER = 4;
    public static final Type<PigmeeDimensionActionPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("ae2lt", "pigmee_dimension_action"));
    public static final StreamCodec<FriendlyByteBuf, PigmeeDimensionActionPacket> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> {
                buf.writeVarInt(packet.containerId); buf.writeVarInt(packet.action); buf.writeVarInt(packet.dimensionId);
                buf.writeUtf(packet.name, 24); buf.writeVarInt(packet.frame); buf.writeVarInt(packet.center);
                buf.writeBoolean(packet.shared); buf.writeUtf(packet.target, 36); buf.writeByte(packet.role);
                buf.writeByteArray(packet.pattern);
            },
            buf -> new PigmeeDimensionActionPacket(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                    buf.readUtf(24), buf.readVarInt(), buf.readVarInt(), buf.readBoolean(), buf.readUtf(36), buf.readByte(), buf.readByteArray(256)));

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static void handle(PigmeeDimensionActionPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player && player.containerMenu instanceof PigmeeDimensionMenu menu) {
                menu.handle(player, packet);
            }
        });
    }
}

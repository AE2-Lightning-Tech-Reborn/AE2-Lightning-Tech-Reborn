package com.moakiee.ae2lt.network;

import com.moakiee.ae2lt.logic.craft.migration.PatternMigrationSnapshot;
import com.moakiee.ae2lt.menu.MatrixMigrationMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record MatrixPatternMigrationStatusPacket(int token, PatternMigrationSnapshot snapshot) implements CustomPacketPayload {
    public static final Type<MatrixPatternMigrationStatusPacket> TYPE = new Type<>(NetworkInit.id("matrix_pattern_migration_status"));
    public static final StreamCodec<FriendlyByteBuf, MatrixPatternMigrationStatusPacket> STREAM_CODEC = StreamCodec.of(
            MatrixPatternMigrationStatusPacket::encode, MatrixPatternMigrationStatusPacket::decode);

    private static void encode(FriendlyByteBuf buf, MatrixPatternMigrationStatusPacket packet) {
        var s = packet.snapshot;
        buf.writeVarInt(packet.token); buf.writeEnum(s.stage()); buf.writeEnum(s.reason()); buf.writeVarInt(s.tier());
        buf.writeVarLong(s.scanned()); buf.writeVarLong(s.total()); buf.writeVarLong(s.moved()); buf.writeVarLong(s.recovered());
        buf.writeVarLong(s.incompatible()); buf.writeVarLong(s.noSpace()); buf.writeVarLong(s.refundBlocked());
        buf.writeVarLong(s.unavailable()); buf.writeVarLong(s.unsupported()); buf.writeVarLong(s.disks());
        buf.writeVarLong(s.lastSliceNanos()); buf.writeVarLong(s.budgetHits());
    }

    private static MatrixPatternMigrationStatusPacket decode(FriendlyByteBuf buf) {
        int token = buf.readVarInt();
        return new MatrixPatternMigrationStatusPacket(token, new PatternMigrationSnapshot(
                buf.readEnum(PatternMigrationSnapshot.Stage.class), buf.readEnum(PatternMigrationSnapshot.Reason.class),
                buf.readVarInt(), buf.readVarLong(), buf.readVarLong(), buf.readVarLong(), buf.readVarLong(),
                buf.readVarLong(), buf.readVarLong(), buf.readVarLong(), buf.readVarLong(), buf.readVarLong(),
                buf.readVarLong(), buf.readVarLong(), buf.readVarLong()));
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(MatrixPatternMigrationStatusPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            var player = context.player();
            if (player != null && player.containerMenu.containerId == packet.token
                    && player.containerMenu instanceof MatrixMigrationMenu menu) menu.acceptMigrationSnapshot(packet.snapshot);
        });
    }
}

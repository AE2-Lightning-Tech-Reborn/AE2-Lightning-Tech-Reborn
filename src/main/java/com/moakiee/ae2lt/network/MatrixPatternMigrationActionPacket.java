package com.moakiee.ae2lt.network;

import com.moakiee.ae2lt.logic.craft.migration.PatternMigrationSnapshot;
import com.moakiee.ae2lt.menu.MatrixMigrationMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record MatrixPatternMigrationActionPacket(int token, BlockPos menuPos, boolean stop) implements CustomPacketPayload {
    public static final Type<MatrixPatternMigrationActionPacket> TYPE = new Type<>(NetworkInit.id("matrix_pattern_migration_action"));
    public static final StreamCodec<FriendlyByteBuf, MatrixPatternMigrationActionPacket> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> { buf.writeVarInt(packet.token); buf.writeBlockPos(packet.menuPos); buf.writeBoolean(packet.stop); },
            buf -> new MatrixPatternMigrationActionPacket(buf.readVarInt(), buf.readBlockPos(), buf.readBoolean()));

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(MatrixPatternMigrationActionPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.containerMenu instanceof MatrixMigrationMenu menu)
                    || player.containerMenu.containerId != packet.token
                    || !menu.getMigrationMenuPos().equals(packet.menuPos)
                    || !player.containerMenu.stillValid(player)) return;
            var controller = menu.getMigrationController();
            if (controller == null || controller.isRemoved()) return;
            if (packet.stop) controller.getPatternMigration().stop(PatternMigrationSnapshot.Reason.CANCELLED);
            else controller.getPatternMigration().start(player);
            player.containerMenu.broadcastChanges();
        });
    }
}

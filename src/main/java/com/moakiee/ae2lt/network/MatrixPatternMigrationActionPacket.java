package com.moakiee.ae2lt.network;

import com.moakiee.ae2lt.logic.craft.migration.PatternMigrationSnapshot;
import com.moakiee.ae2lt.menu.MatrixMigrationMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public record MatrixPatternMigrationActionPacket(int token, BlockPos menuPos, boolean stop) {
    public static void encode(MatrixPatternMigrationActionPacket packet, FriendlyByteBuf buf) {
        buf.writeVarInt(packet.token); buf.writeBlockPos(packet.menuPos); buf.writeBoolean(packet.stop);
    }

    public static MatrixPatternMigrationActionPacket decode(FriendlyByteBuf buf) {
        return new MatrixPatternMigrationActionPacket(buf.readVarInt(), buf.readBlockPos(), buf.readBoolean());
    }

    public static void handle(MatrixPatternMigrationActionPacket packet, Supplier<NetworkEvent.Context> context) {
        var ctx = context.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null
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
        ctx.setPacketHandled(true);
    }
}

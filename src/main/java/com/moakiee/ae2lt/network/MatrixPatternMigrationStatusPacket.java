package com.moakiee.ae2lt.network;

import com.moakiee.ae2lt.logic.craft.migration.PatternMigrationSnapshot;
import com.moakiee.ae2lt.menu.MatrixMigrationMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.api.distmarker.Dist;
import java.util.function.Supplier;
import com.moakiee.ae2lt.client.ClientNetworkPacketHandlers;

public record MatrixPatternMigrationStatusPacket(int token, PatternMigrationSnapshot snapshot) {
    public static void encode(MatrixPatternMigrationStatusPacket packet, FriendlyByteBuf buf) {
        var s = packet.snapshot;
        buf.writeVarInt(packet.token); buf.writeEnum(s.stage()); buf.writeEnum(s.reason()); buf.writeVarInt(s.tier());
        buf.writeVarLong(s.scanned()); buf.writeVarLong(s.total()); buf.writeVarLong(s.moved()); buf.writeVarLong(s.recovered());
        buf.writeVarLong(s.incompatible()); buf.writeVarLong(s.noSpace()); buf.writeVarLong(s.refundBlocked());
        buf.writeVarLong(s.unavailable()); buf.writeVarLong(s.unsupported()); buf.writeVarLong(s.disks());
        buf.writeVarLong(s.lastSliceNanos()); buf.writeVarLong(s.budgetHits());
    }

    public static MatrixPatternMigrationStatusPacket decode(FriendlyByteBuf buf) {
        int token = buf.readVarInt();
        return new MatrixPatternMigrationStatusPacket(token, new PatternMigrationSnapshot(
                buf.readEnum(PatternMigrationSnapshot.Stage.class), buf.readEnum(PatternMigrationSnapshot.Reason.class),
                buf.readVarInt(), buf.readVarLong(), buf.readVarLong(), buf.readVarLong(), buf.readVarLong(),
                buf.readVarLong(), buf.readVarLong(), buf.readVarLong(), buf.readVarLong(), buf.readVarLong(),
                buf.readVarLong(), buf.readVarLong(), buf.readVarLong()));
    }

    public static void handle(MatrixPatternMigrationStatusPacket packet, Supplier<NetworkEvent.Context> context) {
        var ctx = context.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientNetworkPacketHandlers.handleMatrixPatternMigrationStatus(packet)));
        ctx.setPacketHandled(true);
    }
}

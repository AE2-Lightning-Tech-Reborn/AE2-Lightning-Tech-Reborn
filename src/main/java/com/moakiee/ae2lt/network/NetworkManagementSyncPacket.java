package com.moakiee.ae2lt.network;

import com.moakiee.ae2lt.logic.management.ManagementQuery;
import com.moakiee.ae2lt.logic.management.ManagementRow;
import com.moakiee.ae2lt.logic.management.ManagementSnapshot;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record NetworkManagementSyncPacket(int menuId, int serial, String error, ManagementSnapshot snapshot) implements CustomPacketPayload {
    public static final Type<NetworkManagementSyncPacket> TYPE = new Type<>(ResourceLocation.parse("ae2lt:network_management_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, NetworkManagementSyncPacket> STREAM_CODEC = StreamCodec.ofMember(
            (p, b) -> {
                b.writeVarInt(p.menuId); b.writeVarInt(p.serial); b.writeUtf(p.error, 64);
                var s = p.snapshot; b.writeVarInt(s.total()); b.writeVarInt(s.devices()); b.writeVarInt(s.offset());
                for (double rate : s.totals()) b.writeDouble(rate);
                b.writeBoolean(s.limited()); b.writeVarInt(s.rows().size());
                for (var row : s.rows()) row.write(b);
            }, b -> {
                int menu = b.readVarInt(), serial = b.readVarInt(); String error = b.readUtf(64);
                int total = b.readVarInt(), devices = b.readVarInt(), offset = b.readVarInt();
                double[] totals = new double[5]; for (int i = 0; i < 5; i++) totals[i] = b.readDouble();
                boolean limited = b.readBoolean(); int size = ManagementQuery.bounded(b.readVarInt(), ManagementQuery.PAGE_SIZE);
                var rows = new ArrayList<ManagementRow>(size); for (int i = 0; i < size; i++) rows.add(ManagementRow.read(b));
                return new NetworkManagementSyncPacket(menu, serial, error, new ManagementSnapshot(total, devices, offset, totals, limited, List.copyOf(rows)));
            });
    @Override public Type<NetworkManagementSyncPacket> type() { return TYPE; }
    public static void handle(NetworkManagementSyncPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (net.minecraft.client.Minecraft.getInstance().screen instanceof com.moakiee.ae2lt.client.gui.NetworkManagementScreen screen)
                screen.receive(packet);
        });
    }
}

package com.moakiee.ae2lt.network;

import com.moakiee.ae2lt.logic.energy.WirelessEnergyEndpoints;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record WirelessEnergyMenuSyncPacket(int menuId, int frequency, int requestedPage, int page,
        int total, String result, List<Entry> entries) implements CustomPacketPayload {
    public static final int PAGE_SIZE = 3;
    public record Entry(UUID id, String dimension, BlockPos pos, Direction face, WirelessEnergyEndpoints.State state,
                        long age, long lastDelivered, long totalDelivered, boolean canDisconnect) {}
    public static final Type<WirelessEnergyMenuSyncPacket> TYPE = new Type<>(ResourceLocation.parse("ae2lt:wireless_energy_menu_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, WirelessEnergyMenuSyncPacket> STREAM_CODEC = StreamCodec.ofMember(
            (v, b) -> {
                b.writeVarInt(v.menuId); b.writeVarInt(v.frequency); b.writeVarInt(v.requestedPage); b.writeVarInt(v.page);
                b.writeVarInt(v.total); b.writeUtf(v.result, 64); b.writeVarInt(v.entries.size());
                for (var e : v.entries) {
                    b.writeUUID(e.id); b.writeUtf(e.dimension, 256); b.writeBlockPos(e.pos); b.writeEnum(e.face); b.writeEnum(e.state);
                    b.writeLong(e.age); b.writeLong(e.lastDelivered); b.writeLong(e.totalDelivered); b.writeBoolean(e.canDisconnect);
                }
            }, b -> {
                int menu = b.readVarInt(), frequency = b.readVarInt(), requested = b.readVarInt(), page = b.readVarInt(), total = b.readVarInt();
                String result = b.readUtf(64); int size = b.readVarInt();
                if (size < 0 || size > PAGE_SIZE) throw new IllegalArgumentException("Invalid energy page size");
                var entries = new java.util.ArrayList<Entry>(size);
                for (int i = 0; i < size; i++) entries.add(new Entry(b.readUUID(), b.readUtf(256), b.readBlockPos(),
                        b.readEnum(Direction.class), b.readEnum(WirelessEnergyEndpoints.State.class), b.readLong(), b.readLong(), b.readLong(), b.readBoolean()));
                return new WirelessEnergyMenuSyncPacket(menu, frequency, requested, page, total, result, List.copyOf(entries));
            });
    @Override public Type<WirelessEnergyMenuSyncPacket> type() { return TYPE; }
    public static void handle(WirelessEnergyMenuSyncPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (net.minecraft.client.Minecraft.getInstance().screen instanceof com.moakiee.ae2lt.client.gui.FrequencyScreen screen)
                screen.receiveEnergyPage(packet);
        });
    }
}

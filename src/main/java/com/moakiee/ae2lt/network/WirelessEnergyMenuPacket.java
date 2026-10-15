package com.moakiee.ae2lt.network;

import com.moakiee.ae2lt.grid.WirelessFrequencyManager;
import com.moakiee.ae2lt.logic.energy.WirelessEnergyEndpoints;
import com.moakiee.ae2lt.menu.FrequencyMenu;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Bounded page query or generation-specific disconnect from an open advanced controller. */
public record WirelessEnergyMenuPacket(int menuId, int frequency, int page, UUID disconnect) implements CustomPacketPayload {
    public static final Type<WirelessEnergyMenuPacket> TYPE = new Type<>(ResourceLocation.parse("ae2lt:wireless_energy_menu"));
    public static final StreamCodec<RegistryFriendlyByteBuf, WirelessEnergyMenuPacket> STREAM_CODEC = StreamCodec.ofMember(
            (v, b) -> { b.writeVarInt(v.menuId); b.writeVarInt(v.frequency); b.writeVarInt(v.page); b.writeNullable(v.disconnect, (buf, id) -> buf.writeUUID(id)); },
            b -> new WirelessEnergyMenuPacket(b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readNullable(buf -> buf.readUUID())));
    @Override public Type<WirelessEnergyMenuPacket> type() { return TYPE; }
    public static void handle(WirelessEnergyMenuPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.containerMenu instanceof FrequencyMenu menu) || menu.containerId != packet.menuId
                    || packet.page < 0 || packet.page > 8192 / WirelessEnergyMenuSyncPacket.PAGE_SIZE
                    || !menu.acceptEnergyRequest(player, packet.frequency, packet.disconnect != null)) return;
            var f = WirelessFrequencyManager.get(player.server).getFrequency(packet.frequency);
            var registry = WirelessEnergyEndpoints.get(player.server);
            String result = f == null || !f.getPlayerAccess(player).canUse() ? "no_permission" : "";
            if (packet.disconnect != null) result = registry.disconnect(player, packet.frequency, packet.disconnect);
            var entries = registry.inspect(packet.frequency, player.getUUID());
            int page = Math.min(packet.page, Math.max(0, (entries.size() - 1) / WirelessEnergyMenuSyncPacket.PAGE_SIZE));
            int start = page * WirelessEnergyMenuSyncPacket.PAGE_SIZE;
            var rows = entries.subList(start, Math.min(entries.size(), start + WirelessEnergyMenuSyncPacket.PAGE_SIZE)).stream()
                    .map(e -> new WirelessEnergyMenuSyncPacket.Entry(e.id(), e.location().dimension().location().toString(),
                            e.location().pos(), e.face(), e.state(), e.lastVisitedTick() == Long.MIN_VALUE ? -1
                            : Math.max(0, player.server.getTickCount() - e.lastVisitedTick()), e.lastDelivered(), e.totalDelivered(),
                            !player.isSpectator() && player.mayBuild() && f != null
                                    && (e.actor().equals(player.getUUID()) || f.getPlayerAccess(player).isManager())))
                    .toList();
            PacketDistributor.sendToPlayer(player, new WirelessEnergyMenuSyncPacket(menu.containerId, packet.frequency,
                    packet.page, page, entries.size(), result, rows));
        });
    }
}

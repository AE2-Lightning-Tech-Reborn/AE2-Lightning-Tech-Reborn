package com.moakiee.ae2lt.network;

import com.moakiee.ae2lt.grid.FrequencySecurityLevel;
import com.moakiee.ae2lt.grid.WirelessFrequency;
import com.moakiee.ae2lt.grid.WirelessFrequencyManager;
import com.moakiee.ae2lt.menu.FrequencyMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public record CreateFrequencyPacket(
        int token,
        String name, int color,
        FrequencySecurityLevel security, String password
) {

    public static void encode(CreateFrequencyPacket pkt, FriendlyByteBuf buf) {
        buf.writeVarInt(pkt.token);
        buf.writeUtf(pkt.name, WirelessFrequency.MAX_NAME_LENGTH);
        buf.writeInt(pkt.color);
        buf.writeByte(pkt.security.getId());
        buf.writeUtf(pkt.password, WirelessFrequency.MAX_PASSWORD_LENGTH);
    }

    public static CreateFrequencyPacket decode(FriendlyByteBuf buf) {
        return new CreateFrequencyPacket(
                buf.readVarInt(),
                buf.readUtf(WirelessFrequency.MAX_NAME_LENGTH),
                buf.readInt(),
                FrequencySecurityLevel.fromId(buf.readByte()),
                buf.readUtf(WirelessFrequency.MAX_PASSWORD_LENGTH));
    }

    public static void handle(CreateFrequencyPacket pkt, Supplier<NetworkEvent.Context> ctxSupplier) {
        var ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;
            if (FrequencyMenu.validateToken(player, pkt.token) == null) {
                NetworkInit.sendToPlayer(player, new FrequencyResponsePacket(FrequencyResponsePacket.REJECTED));
                return;
            }
            var manager = WirelessFrequencyManager.get();
            if (manager == null) return;

            if (pkt.name.isBlank()) {
                NetworkInit.sendToPlayer(player, new FrequencyResponsePacket(FrequencyResponsePacket.REJECTED));
                return;
            }
            // Encrypted frequencies without a password become private.
            FrequencySecurityLevel effectiveSecurity = pkt.security;
            if (effectiveSecurity == FrequencySecurityLevel.ENCRYPTED && pkt.password.isBlank()) {
                effectiveSecurity = FrequencySecurityLevel.PRIVATE;
            }

            var freq = manager.createFrequency(player, pkt.name, pkt.color, effectiveSecurity, pkt.password);
            if (freq != null) {
                UpdateFrequencyBasicPacket.broadcastToPlayers(
                        player.getServer(), UpdateFrequencyBasicPacket.forFrequency(freq));
                // Send ownership before binding; the normal broadcast only reaches already-bound menus.
                SyncFrequencyDetailPacket.sendInitialMembersIfNeeded(player, freq.getId());
            }
        });
        ctx.setPacketHandled(true);
    }
}


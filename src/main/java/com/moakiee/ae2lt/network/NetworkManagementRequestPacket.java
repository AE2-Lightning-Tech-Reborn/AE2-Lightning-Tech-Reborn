package com.moakiee.ae2lt.network;

import com.moakiee.ae2lt.logic.management.ManagementQuery;
import com.moakiee.ae2lt.menu.NetworkManagementMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public record NetworkManagementRequestPacket(int menuId, ManagementQuery query) {
    public void write(FriendlyByteBuf b) { b.writeVarInt(menuId); query.write(b); }
    public static NetworkManagementRequestPacket decode(FriendlyByteBuf b) { return new NetworkManagementRequestPacket(b.readVarInt(), ManagementQuery.read(b)); }
    public static void handle(NetworkManagementRequestPacket packet, java.util.function.Supplier<net.minecraftforge.network.NetworkEvent.Context> supplier) {
        var context = supplier.get();
        context.setPacketHandled(true);
        context.enqueueWork(() -> {
            if (!((net.minecraft.world.entity.player.Player) context.getSender() instanceof ServerPlayer player) || player.containerMenu.containerId != packet.menuId) return;
            if (player.containerMenu instanceof NetworkManagementMenu menu) menu.query(player, packet.query);
            else if (player.containerMenu instanceof com.moakiee.ae2lt.menu.FrequencyMenu menu)
                menu.openManagement(player, packet.query);
        });
    }
}

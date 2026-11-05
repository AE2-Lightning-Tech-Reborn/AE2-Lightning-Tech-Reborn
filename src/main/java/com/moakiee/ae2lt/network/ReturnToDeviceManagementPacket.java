package com.moakiee.ae2lt.network;

import com.moakiee.ae2lt.logic.management.RemoteDeviceSession;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public record ReturnToDeviceManagementPacket(int menuId) {
    public void write(FriendlyByteBuf b) { b.writeVarInt(menuId); }
    public static ReturnToDeviceManagementPacket decode(FriendlyByteBuf b) { return new ReturnToDeviceManagementPacket(b.readVarInt()); }
    public static void handle(ReturnToDeviceManagementPacket packet, java.util.function.Supplier<net.minecraftforge.network.NetworkEvent.Context> supplier) {
        var context = supplier.get();
        context.setPacketHandled(true);
        context.enqueueWork(() -> {
            if ((net.minecraft.world.entity.player.Player) context.getSender() instanceof ServerPlayer player && player.containerMenu.containerId == packet.menuId) {
                var remote = RemoteDeviceSession.locator(player);
                if (remote != null && remote.session() != null) remote.session().returnToDevices(player);
            }
        });
    }
}

package com.moakiee.ae2lt.network.tianshu;

import com.moakiee.ae2lt.menu.TianshuPatternEncodingTermMenu;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

/** The server resolves the native recipe again before accepting the client selection. */
public record SelectOmniversalPatternPacket(int containerId, ItemStack pattern) {
    public void write(FriendlyByteBuf data) { data.writeVarInt(containerId);data.writeItem(pattern); }
    public static SelectOmniversalPatternPacket read(FriendlyByteBuf data) { return new SelectOmniversalPatternPacket(data.readVarInt(), data.readItem()); }
    public static void handle(SelectOmniversalPatternPacket packet, Supplier<NetworkEvent.Context> context) {
        var ctx = context.get();
        ctx.enqueueWork(() -> {
            var player = ctx.getSender();
            if (player != null && player.isAlive() && player.containerMenu instanceof TianshuPatternEncodingTermMenu menu
                    && menu.containerId == packet.containerId() && menu.isValidMenu() && menu.stillValid(player))
                menu.selectOmniversalPattern(packet.pattern());
        });
        ctx.setPacketHandled(true);
    }
}

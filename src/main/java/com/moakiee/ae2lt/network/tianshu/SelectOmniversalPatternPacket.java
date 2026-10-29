package com.moakiee.ae2lt.network.tianshu;

import java.util.function.Supplier;
import com.moakiee.ae2lt.menu.TianshuPatternEncodingTermMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

/** The server re-encodes the selected native recipe before accepting its draft. */
public record SelectOmniversalPatternPacket(int containerId, ItemStack pattern) {
    public void write(FriendlyByteBuf data) { data.writeVarInt(containerId); data.writeItem(pattern); }
    public static SelectOmniversalPatternPacket decode(FriendlyByteBuf data) {
        return new SelectOmniversalPatternPacket(data.readVarInt(), data.readItem());
    }
    public static void handle(SelectOmniversalPatternPacket packet, Supplier<NetworkEvent.Context> context) {
        var ctx = context.get();
        ctx.enqueueWork(() -> {
            var player = ctx.getSender();
            if (player != null && player.containerMenu instanceof TianshuPatternEncodingTermMenu menu
                    && menu.containerId == packet.containerId() && menu.stillValid(player)) {
                menu.selectOmniversalPattern(packet.pattern());
            }
        });
        ctx.setPacketHandled(true);
    }
}

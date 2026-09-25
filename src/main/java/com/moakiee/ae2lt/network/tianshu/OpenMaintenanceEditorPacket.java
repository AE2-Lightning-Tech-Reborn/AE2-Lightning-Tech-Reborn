package com.moakiee.ae2lt.network.tianshu;
import java.util.function.Supplier;
import com.moakiee.ae2lt.menu.TianshuMaintenanceMenu;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraft.network.FriendlyByteBuf;
import appeng.api.stacks.AEKey;

public record OpenMaintenanceEditorPacket(
        int containerId, int selectionRevision, AEKey key) {
public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(containerId);
        buf.writeVarInt(selectionRevision);
        AEKey.writeKey(buf, key);
    }

    public static OpenMaintenanceEditorPacket decode(FriendlyByteBuf buf) {
        return new OpenMaintenanceEditorPacket(
                buf.readVarInt(), buf.readVarInt(), AEKey.readKey(buf));
    }
public static void handle(OpenMaintenanceEditorPacket packet, Supplier<NetworkEvent.Context> context) {
        var ctx = context.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
        if (player != null && player.containerMenu instanceof TianshuMaintenanceMenu menu
                    && menu.maintenanceMenu().containerId == packet.containerId()) {
                menu.openMaintenanceEditor(packet.selectionRevision(), packet.key());
            }
        });
        ctx.setPacketHandled(true);
    }
}

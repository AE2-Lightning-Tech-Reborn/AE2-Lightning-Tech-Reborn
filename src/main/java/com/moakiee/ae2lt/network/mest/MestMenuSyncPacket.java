package com.moakiee.ae2lt.network.mest;

import com.moakiee.ae2lt.menu.extension.TianshuMenuContainer;
import com.moakiee.ae2lt.network.NetworkInit;
import io.netty.buffer.Unpooled;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record MestMenuSyncPacket(int containerId, String module, byte[] data) implements CustomPacketPayload {
    private static final int MAX_BYTES = 1 << 20;
    public static final Type<MestMenuSyncPacket> TYPE = new Type<>(NetworkInit.id("mest_menu_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MestMenuSyncPacket> STREAM_CODEC =
            StreamCodec.ofMember(MestMenuSyncPacket::write, MestMenuSyncPacket::read);
    public MestMenuSyncPacket {
        if ((!module.equals("crafting") && !module.equals("pattern")) || data.length > MAX_BYTES)
            throw new IllegalArgumentException("Invalid Tianshu module sync");
        data = data.clone();
    }
    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(containerId); buf.writeUtf(module, 16); buf.writeByteArray(data);
    }
    private static MestMenuSyncPacket read(RegistryFriendlyByteBuf buf) {
        return new MestMenuSyncPacket(buf.readVarInt(), buf.readUtf(16), buf.readByteArray(MAX_BYTES));
    }
    @Override public Type<MestMenuSyncPacket> type() { return TYPE; }
    public static void handle(MestMenuSyncPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            var outer = context.player().containerMenu;
            if (outer.containerId != packet.containerId || !(outer instanceof TianshuMenuContainer container)) return;
            var menu = container.tianshuMenu(packet.module);
            if (menu == null) return;
            var buf = new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(packet.data), context.player().registryAccess());
            try { menu.receiveServerSyncData(buf); } finally { buf.release(); }
        });
    }
}

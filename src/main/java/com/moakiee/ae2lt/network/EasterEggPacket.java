package com.moakiee.ae2lt.network;

import com.moakiee.ae2lt.client.ClientNetworkPacketHandlers;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public record EasterEggPacket(GlobalPos source) {
    public static EasterEggPacket decode(FriendlyByteBuf buf) {
        return new EasterEggPacket(buf.readGlobalPos());
    }

    public static void encode(EasterEggPacket payload, FriendlyByteBuf buf) {
        buf.writeGlobalPos(payload.source());
    }

    public static void handle(EasterEggPacket payload, Supplier<NetworkEvent.Context> ctxSupplier) {
        var ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT,
                () -> () -> ClientNetworkPacketHandlers.handleEasterEgg(payload.source())));
        ctx.setPacketHandled(true);
    }
}

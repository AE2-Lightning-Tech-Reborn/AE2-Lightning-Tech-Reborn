package com.moakiee.ae2lt.network;

import com.moakiee.ae2lt.client.ClientNetworkPacketHandlers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public record EasterEggPacket(GlobalPos source) {
    public static EasterEggPacket decode(FriendlyByteBuf buf) {
        ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, buf.readResourceLocation());
        return new EasterEggPacket(GlobalPos.of(dimension, buf.readBlockPos()));
    }

    public static void encode(EasterEggPacket payload, FriendlyByteBuf buf) {
        buf.writeResourceLocation(payload.source().dimension().location());
        buf.writeBlockPos(payload.source().pos());
    }

    public static void handle(EasterEggPacket payload, Supplier<NetworkEvent.Context> ctxSupplier) {
        var ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT,
                () -> () -> ClientNetworkPacketHandlers.handleEasterEgg(payload.source())));
        ctx.setPacketHandled(true);
    }
}


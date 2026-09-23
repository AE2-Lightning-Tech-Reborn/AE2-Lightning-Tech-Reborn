package com.moakiee.ae2lt.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import io.netty.channel.ChannelFutureListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundPlayerCombatKillPacket;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

import com.moakiee.ae2lt.celestweave.CelestweaveArmorUndyingHandler;

@Mixin(ServerCommonPacketListenerImpl.class)
public abstract class ServerCommonPacketListenerUndyingMixin {
    /**
     * ServerPlayer#die sends this packet before loot, statistics and the death
     * animation. Some mods may reproduce that path without invoking ServerPlayer#die,
     * so recover the player at the shared packet boundary and keep the client out of
     * the death screen. The one-argument send overload delegates here as well.
     */
    @Inject(
            method = "send(Lnet/minecraft/network/protocol/Packet;"
                    + "Lio/netty/channel/ChannelFutureListener;)V",
            at = @At("HEAD"),
            cancellable = true)
    private void ae2lt$suppressProtectedPlayerDeathPacket(
            Packet<?> packet,
            ChannelFutureListener sendListener,
            CallbackInfo ci) {
        if (packet instanceof ClientboundPlayerCombatKillPacket
                && (Object) this instanceof ServerGamePacketListenerImpl gameListener
                && CelestweaveArmorUndyingHandler.protectBeforeDeathSideEffect(gameListener.player)) {
            ci.cancel();
        }
    }
}

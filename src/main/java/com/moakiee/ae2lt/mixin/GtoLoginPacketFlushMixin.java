package com.moakiee.ae2lt.mixin;

import com.moakiee.ae2lt.compat.gto.GtoLoginPacketFlusher;
import com.moakiee.ae2lt.compat.gto.GtoConnectionReads;
import io.netty.channel.Channel;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Retains login flushing and orders read resumption across protocol transitions. */
@Mixin(Connection.class)
public abstract class GtoLoginPacketFlushMixin {
    @Shadow private Channel channel;

    @Inject(method = "tick", at = @At("TAIL"))
    private void ae2lt$flushNegotiatingConnection(CallbackInfo callback) {
        GtoLoginPacketFlusher.flushLogin(channel);
    }

    @Inject(method = "setProtocol", at = @At("TAIL"))
    private void ae2lt$resumeProtocolReads(CallbackInfo callback) {
        GtoConnectionReads.afterProtocolChange(channel);
    }
}

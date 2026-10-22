package com.moakiee.ae2lt.mixin;

import com.moakiee.ae2lt.compat.gto.GtoLoginPacketFlusher;
import io.netty.channel.Channel;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Krypton removes Connection.tick's fallback flush; Forge login still needs that fallback. */
@Mixin(Connection.class)
public abstract class GtoLoginPacketFlushMixin {
    @Shadow private Channel channel;

    @Inject(method = "tick", at = @At("TAIL"))
    private void ae2lt$flushNegotiatingConnection(CallbackInfo callback) {
        GtoLoginPacketFlusher.flushLogin(channel);
    }
}

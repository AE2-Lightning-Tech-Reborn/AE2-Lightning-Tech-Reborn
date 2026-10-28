package com.moakiee.ae2lt.mixin;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Forge adds login indexes on the server thread and acknowledges them on Netty. */
@Mixin(targets = "net.minecraftforge.network.HandshakeHandler", remap = false)
public abstract class GtoHandshakePendingMessagesMixin {
    @Shadow private List<Integer> sentMessages;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void ae2lt$protectPendingLoginMessages(CallbackInfo callback) {
        // Preserve any indexes installed by an earlier constructor hook. The tiny,
        // per-connection list must support atomic removeIf alongside concurrent add.
        sentMessages = new CopyOnWriteArrayList<>(sentMessages);
    }
}

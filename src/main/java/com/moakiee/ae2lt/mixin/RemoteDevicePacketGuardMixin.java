package com.moakiee.ae2lt.mixin;

import appeng.core.sync.BasePacket;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.moakiee.ae2lt.logic.management.RemoteDeviceSession;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Re-check on the queued server thread, including AE2 config packets that do not call stillValid themselves. */
@Mixin(value = appeng.core.sync.network.NetworkHandler.class, remap = false)
public abstract class RemoteDevicePacketGuardMixin {
    @WrapOperation(method = "lambda$serverPacket$8", at = @At(value = "INVOKE",
            target = "Lappeng/core/sync/BasePacket;serverPacketData(Lnet/minecraft/server/level/ServerPlayer;)V"))
    private static void ae2lt$authorizeRemoteAction(BasePacket packet, ServerPlayer player, Operation<Void> original) {
        if (RemoteDeviceSession.acceptPacket(player)) original.call(packet, player);
    }
}

package com.moakiee.ae2lt.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.portal.TeleportTransition;

import com.moakiee.ae2lt.celestweave.PhaseFlightMovementGuard;

/** Stops dimension transitions before the player is removed from the source level. */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerPhaseMovementMixin {
    @Inject(method = "teleport(Lnet/minecraft/world/level/portal/TeleportTransition;)Lnet/minecraft/server/level/ServerPlayer;", at = @At("HEAD"), cancellable = true)
    private void ae2lt$blockExternalPhaseDimensionChange(
            TeleportTransition transition,
            CallbackInfoReturnable<ServerPlayer> cir) {
        ServerPlayer player = (ServerPlayer) (Object) this;
        if (PhaseFlightMovementGuard.blocksExternalTeleports(player)
                && !PhaseFlightMovementGuard.isSelfTeleportAuthorized(player)) {
            PhaseFlightMovementGuard.notifyBlockedDimensionTeleport(
                    player,
                    transition.newLevel(),
                    transition.position());
            cir.setReturnValue(null);
        }
    }
}

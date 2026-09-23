package com.moakiee.ae2lt.mixin;

import java.util.Set;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerAbilitiesPacket;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.phys.Vec3;

import com.moakiee.ae2lt.celestweave.PhaseFlightMovementGuard;
import com.moakiee.ae2lt.celestweave.PhaseFlightPlayerState;

/** Treats coordinates supplied by the player's own movement packet as authorized movement. */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerPhaseMovementMixin {
    @Inject(method = "handlePlayerAbilities", at = @At("HEAD"), cancellable = true)
    private void ae2lt$rejectExternalLockedFlightUpdate(
            ServerboundPlayerAbilitiesPacket packet,
            CallbackInfo ci) {
        var player = ((ServerGamePacketListenerImpl) (Object) this).player;
        if (!PhaseFlightPlayerState.isFlightLocked(player)) {
            return;
        }
        // Locked hover input has its own packet. Vanilla ability packets are public-field
        // reconciliation and must not replace the private flight intent.
        player.getAbilities().flying = PhaseFlightPlayerState.isFlying(player);
        player.onUpdateAbilities();
        ci.cancel();
    }

    @Inject(
            method = "teleport(Lnet/minecraft/world/entity/PositionMoveRotation;Ljava/util/Set;)V",
            at = @At("HEAD"),
            cancellable = true)
    private void ae2lt$blockExternalPhaseTeleportPacket(
            PositionMoveRotation destination,
            Set<Relative> relatives,
            CallbackInfo ci) {
        var player = ((ServerGamePacketListenerImpl) (Object) this).player;
        Vec3 target = PositionMoveRotation.calculateAbsolute(
                PositionMoveRotation.of(player), destination, relatives).position();
        if (PhaseFlightMovementGuard.blocksExternalTeleports(player)
                && !PhaseFlightMovementGuard.isSelfTeleportAuthorized(player)
                && !player.position().equals(target)) {
            PhaseFlightMovementGuard.notifyBlockedTeleport(player, target);
            ci.cancel();
        }
    }

    @Inject(method = "handleMovePlayer", at = @At("HEAD"))
    private void ae2lt$beginPlayerAuthorizedMove(ServerboundMovePlayerPacket packet, CallbackInfo ci) {
        var player = ((ServerGamePacketListenerImpl) (Object) this).player;
        PhaseFlightMovementGuard.beginMovementPacket(player);
    }

    @Inject(method = "handleMovePlayer", at = @At("RETURN"))
    private void ae2lt$endPlayerAuthorizedMove(ServerboundMovePlayerPacket packet, CallbackInfo ci) {
        PhaseFlightMovementGuard.endMovementPacket(
                ((ServerGamePacketListenerImpl) (Object) this).player);
    }

    @Inject(method = "handleCustomPayload", at = @At("HEAD"))
    private void ae2lt$beginPlayerPayload(ServerboundCustomPayloadPacket packet, CallbackInfo ci) {
        PhaseFlightMovementGuard.beginCustomPayload(
                ((ServerGamePacketListenerImpl) (Object) this).player);
    }

    @Inject(method = "handleCustomPayload", at = @At("RETURN"))
    private void ae2lt$endPlayerPayload(ServerboundCustomPayloadPacket packet, CallbackInfo ci) {
        PhaseFlightMovementGuard.endCustomPayload(
                ((ServerGamePacketListenerImpl) (Object) this).player);
    }

    /** Vanilla simulates the player once, then restores the packet-owned position every tick. */
    @WrapOperation(
            method = "tickPlayer",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerPlayer;absSnapTo(DDDFF)V"))
    private void ae2lt$authorizeVanillaTickPositionRestore(
            ServerPlayer player,
            double x,
            double y,
            double z,
            float yRot,
            float xRot,
            Operation<Void> original) {
        PhaseFlightMovementGuard.runAsSelfMovement(
                player,
                () -> original.call(player, x, y, z, yRot, xRot));
    }
}

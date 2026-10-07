package com.moakiee.ae2lt.mixin.mobgrindingutils;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.moakiee.ae2lt.celestweave.PhaseFlightMovementGuard;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/**
 * A conveyor's entityInside callback also runs inside authorized player travel and movement
 * packets. Its own velocity writes are still external forces. Guard only those writes so native
 * collision resolution, block friction, landing callbacks and player input retain their scope.
 */
@Pseudo
@Mixin(targets = "mob_grinding_utils.blocks.BlockEntityConveyor", remap = false)
public abstract class EntityConveyorPhaseLockMixin {
    @WrapOperation(
            method = "entityInside",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;setDeltaMovement(DDD)V"),
            require = 1)
    private void ae2lt$blockConveyorForce(
            Entity entity, double x, double y, double z, Operation<Void> original) {
        if (entity instanceof Player player && PhaseFlightMovementGuard.blocksExternalForces(player)) {
            return;
        }
        original.call(entity, x, y, z);
    }
}

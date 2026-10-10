package com.moakiee.ae2lt.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.moakiee.ae2lt.celestweave.PhaseFlightMovementGuard;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.BedBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Preserves native BedBlock collision writes without authorizing external callbacks. */
@Mixin(BedBlock.class)
public abstract class BedBlockPhaseMovementMixin {
    @WrapOperation(
            method = "bounceUp",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Entity;setDeltaMovement(DDD)V"),
            require = 1)
    private void ae2lt$authorizeBounceUp(Entity entity, double x, double y, double z, Operation<Void> original) {
        if (entity instanceof Player player && PhaseFlightMovementGuard.isVanillaTravelScopeActive(player)) {
            PhaseFlightMovementGuard.runAsVanillaTravelMovement(player, () -> original.call(entity, x, y, z));
        } else {
            original.call(entity, x, y, z);
        }
    }
}

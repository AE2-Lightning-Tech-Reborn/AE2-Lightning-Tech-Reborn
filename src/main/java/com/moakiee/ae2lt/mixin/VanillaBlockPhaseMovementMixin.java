package com.moakiee.ae2lt.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.moakiee.ae2lt.celestweave.PhaseFlightMovementGuard;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Preserves native Block collision writes without authorizing external callbacks. */
@Mixin(Block.class)
public abstract class VanillaBlockPhaseMovementMixin {
    @WrapOperation(
            method = "updateEntityAfterFallOn",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Entity;setDeltaMovement(Lnet/minecraft/world/phys/Vec3;)V"),
            require = 1)
    private void ae2lt$authorizeUpdateEntityAfterFallOn(Entity entity, Vec3 movement, Operation<Void> original) {
        if (entity instanceof Player player && PhaseFlightMovementGuard.isVanillaTravelScopeActive(player)) {
            PhaseFlightMovementGuard.runAsVanillaTravelMovement(player, () -> original.call(entity, movement));
        } else {
            original.call(entity, movement);
        }
    }
}

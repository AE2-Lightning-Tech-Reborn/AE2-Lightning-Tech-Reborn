package com.moakiee.ae2lt.mixin.neoecoae;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.moakiee.ae2lt.event.NaturalLightningTransformationHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/** Leaves complete LT rituals to the normal pre-tick dispatcher. */
@Pseudo
@Mixin(targets = "cn.dancingsnow.neoecoae.event.NELightningTransformEvents", remap = false)
public abstract class NeoEcoLightningTransformMixin {
    @WrapOperation(
            method = "transformBuddingQuartz(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/phys/Vec3;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerLevel;setBlockAndUpdate(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Z",
                    remap = true),
            require = 1)
    private static boolean ae2lt$reserveCompleteRitual(
            ServerLevel level, BlockPos targetPos, BlockState replacement, Operation<Boolean> original,
            ServerLevel strikeLevel, Vec3 strikePosition) {
        if (NaturalLightningTransformationHandler.isReservedForStructure(
                strikeLevel, BlockPos.containing(strikePosition), targetPos)) {
            return false;
        }
        // Keep the original random roll and conversions of unrelated nearby blocks.
        return original.call(level, targetPos, replacement);
    }
}

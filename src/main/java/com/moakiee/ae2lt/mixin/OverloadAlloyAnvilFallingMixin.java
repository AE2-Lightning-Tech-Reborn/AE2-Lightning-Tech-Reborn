package com.moakiee.ae2lt.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.moakiee.ae2lt.block.OverloadAlloyAnvilBlock;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(FallingBlockEntity.class)
public abstract class OverloadAlloyAnvilFallingMixin {
    @Shadow private BlockState blockState;

    @ModifyExpressionValue(method = "causeFallDamage", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/level/block/AnvilBlock;damage(Lnet/minecraft/world/level/block/state/BlockState;)Lnet/minecraft/world/level/block/state/BlockState;"))
    private BlockState ae2lt$keepAlloyAnvilAfterImpact(BlockState damagedState) {
        // Native damage() returns null for custom anvils, which would cancel their drop.
        // Keep impact damage and ordinary anvil wear; only this block is indestructible by wear.
        return blockState.getBlock() instanceof OverloadAlloyAnvilBlock ? blockState : damagedState;
    }
}

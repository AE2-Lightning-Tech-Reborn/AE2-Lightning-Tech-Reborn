package com.moakiee.ae2lt.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.moakiee.ae2lt.block.OverloadAlloyAnvilBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(AnvilMenu.class)
public abstract class OverloadAlloyAnvilMenuMixin {
    @ModifyExpressionValue(method = "lambda$onTake$0", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/level/block/AnvilBlock;damage(Lnet/minecraft/world/level/block/state/BlockState;)Lnet/minecraft/world/level/block/state/BlockState;"))
    private static BlockState ae2lt$keepAlloyAnvil(BlockState damagedState, Player player, Level level, BlockPos pos) {
        // 26.1 keeps wear in this lambda and fires native pre/post craft events around it.
        var state = level.getBlockState(pos);
        return state.getBlock() instanceof OverloadAlloyAnvilBlock ? state : damagedState;
    }
}

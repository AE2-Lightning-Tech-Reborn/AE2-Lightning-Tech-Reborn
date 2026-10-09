package com.moakiee.ae2lt.mixin;

import com.moakiee.ae2lt.machine.largeoverload.LargeFactoryWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelChunk.class)
public abstract class LargeFactoryChunkChangeMixin {
    @Shadow public abstract Level getLevel();
    @Inject(method = "setBlockState", at = @At("HEAD"))
    private void ae2lt$closeFactoryGate(BlockPos position, BlockState next, boolean moved,
            CallbackInfoReturnable<BlockState> callback) {
        // Close the gate before replacement callbacks can re-enter a provider, including interior air edits.
        var self = (LevelChunk) (Object) this;
        if (self.getBlockState(position) != next) LargeFactoryWorld.changed(getLevel(), position);
    }
}

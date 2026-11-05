package com.moakiee.ae2lt.mixin;

import com.moakiee.ae2lt.logic.energy.WirelessEnergyAdmissions;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Observe only a successfully invoked machine ticker, not just a server tick or loaded chunk. */
@Mixin(targets = "net.minecraft.world.level.chunk.LevelChunk$BoundTickingBlockEntity")
public abstract class WirelessEnergyMachineTickMixin {
    @Shadow @Final private BlockEntity blockEntity;
    @Inject(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/entity/BlockEntityTicker;tick(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/entity/BlockEntity;)V", shift = At.Shift.AFTER))
    private void ae2lt$machineTickCompleted(CallbackInfo ci) { WirelessEnergyAdmissions.onMachineTick(blockEntity); }
}

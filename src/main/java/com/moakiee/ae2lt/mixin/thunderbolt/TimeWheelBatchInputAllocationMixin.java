package com.moakiee.ae2lt.mixin.thunderbolt;

import java.util.Map;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.crafting.inv.ListCraftingInventory;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.moakiee.ae2lt.crafting.timewheel.allocation.TimeWheelBatchInputAllocation;
import com.moakiee.thunderbolt.core.crafting.batch.BatchExecutor;
import com.moakiee.thunderbolt.core.crafting.batch.ParallelBatchCpuHelper;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Reuses native batch dispatch; allocation is active only inside an LT CPU-owned scope. */
@Mixin(value = BatchExecutor.class, remap = false)
public abstract class TimeWheelBatchInputAllocationMixin {
    @WrapOperation(method = "runBatchOnly", at = @At(value = "INVOKE",
            target = "Lcom/moakiee/thunderbolt/core/crafting/batch/ParallelBatchCpuHelper;bulkExtract"
                    + "(Lappeng/api/crafting/IPatternDetails;Lappeng/crafting/inv/ListCraftingInventory;"
                    + "JZLjava/util/Map;Lnet/minecraft/world/level/Level;)"
                    + "Lcom/moakiee/thunderbolt/core/crafting/batch/ParallelBatchCpuHelper$BulkResult;"))
    private static ParallelBatchCpuHelper.BulkResult ae2lt$allocateTimeWheelInputs(
            IPatternDetails pattern, ListCraftingInventory inventory, long copies, boolean shared,
            Map<AEKey, Long> reserved, Level level, Operation<ParallelBatchCpuHelper.BulkResult> original) {
        return TimeWheelBatchInputAllocation.extract(pattern, inventory, copies, shared, reserved, level,
                () -> original.call(pattern, inventory, copies, shared, reserved, level));
    }
}

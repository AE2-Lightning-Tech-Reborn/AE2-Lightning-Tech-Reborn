package com.moakiee.ae2lt.mixin.thunderbolt;

import java.util.Map;
import java.util.ArrayList;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.inv.ListCraftingInventory;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.moakiee.ae2lt.crafting.timewheel.allocation.TimeWheelBatchInputAllocation;
import com.moakiee.thunderbolt.core.crafting.batch.BatchExecutor;
import com.moakiee.thunderbolt.core.crafting.batch.ParallelBatchCpuHelper;
import com.moakiee.thunderbolt.api.crafting.batch.BatchJobView;
import com.moakiee.thunderbolt.api.crafting.batch.IBatchCraftingProvider;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;

/** Reuses native batch dispatch; allocation is active only inside an LT CPU-owned scope. */
@Mixin(value = BatchExecutor.class, remap = false)
public abstract class TimeWheelBatchInputAllocationMixin {
    private static final String RUN_BATCH = "runBatchOnly"
            + "(ILcom/moakiee/thunderbolt/core/crafting/batch/BatchCpuAccounting$Mode;"
            + "Lappeng/me/service/CraftingService;Lappeng/api/networking/energy/IEnergyService;"
            + "Lcom/moakiee/thunderbolt/api/crafting/batch/BatchJobView;"
            + "Lappeng/crafting/inv/ListCraftingInventory;Ljava/util/Map;Ljava/lang/Runnable;"
            + "Ljava/util/Map;IJZLcom/moakiee/thunderbolt/core/crafting/batch/TickProviderDispatchSchedule;"
            + "Lcom/moakiee/thunderbolt/api/crafting/batch/BatchProviderAdapter;)"
            + "Lcom/moakiee/thunderbolt/core/crafting/batch/BatchExecutor$BatchRunResult;";

    @WrapOperation(method = RUN_BATCH,
            remap = false, at = @At(value = "INVOKE", remap = false,
            target = "Lcom/moakiee/thunderbolt/core/crafting/batch/ParallelBatchCpuHelper;bulkExtract"
                    + "(Lappeng/api/crafting/IPatternDetails;Lappeng/crafting/inv/ListCraftingInventory;"
                    + "JZLjava/util/Map;Lnet/minecraft/world/level/Level;)"
                    + "Lcom/moakiee/thunderbolt/core/crafting/batch/ParallelBatchCpuHelper$BulkResult;"))
    private static ParallelBatchCpuHelper.BulkResult ae2lt$allocateTimeWheelInputs(
            IPatternDetails pattern, ListCraftingInventory inventory, long copies, boolean shared,
            Map<AEKey, Long> reserved, Level level, Operation<ParallelBatchCpuHelper.BulkResult> original,
            @Local(ordinal = 0) ArrayList<?> candidates, @Local(argsOnly = true) BatchJobView job) {
        return TimeWheelBatchInputAllocation.extractWithAdmission(pattern, inventory, copies, shared, reserved, level,
                candidates, job,
                () -> original.call(pattern, inventory, copies, shared, reserved, level));
    }

    @WrapOperation(method = RUN_BATCH, remap = false,
            at = @At(value = "INVOKE", remap = false,
            target = "Lcom/moakiee/thunderbolt/core/crafting/batch/BatchExecutor$EligibleProvider;capacity()J"))
    private static long ae2lt$admittedCapacity(@Coerce Object candidate, Operation<Long> original,
            @Local(ordinal = 0) IPatternDetails pattern,
            @Local(argsOnly = true) ListCraftingInventory inventory) {
        return TimeWheelBatchInputAllocation.admittedCapacity(pattern, inventory, candidate, original.call(candidate));
    }

    @WrapOperation(method = RUN_BATCH, remap = false,
            at = @At(value = "INVOKE", remap = false,
            target = "Lcom/moakiee/thunderbolt/api/crafting/batch/IBatchCraftingProvider;pushBatch"
                    + "(Lappeng/api/crafting/IPatternDetails;[Lappeng/api/stacks/KeyCounter;J"
                    + "Lcom/moakiee/thunderbolt/api/crafting/batch/BatchJobView;)J"))
    private static long ae2lt$pushAdmitted(IBatchCraftingProvider provider, IPatternDetails execution,
            KeyCounter[] prototype, long copies, BatchJobView job, Operation<Long> original) {
        return TimeWheelBatchInputAllocation.pushAdmitted(provider, execution, prototype, copies, job,
                () -> original.call(provider, execution, prototype, copies, job));
    }
}

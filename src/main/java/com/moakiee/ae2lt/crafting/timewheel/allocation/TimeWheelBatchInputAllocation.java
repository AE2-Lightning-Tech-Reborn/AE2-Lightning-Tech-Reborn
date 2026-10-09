package com.moakiee.ae2lt.crafting.timewheel.allocation;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.core.AELog;
import appeng.crafting.inv.ListCraftingInventory;
import com.moakiee.thunderbolt.api.crafting.batch.BatchJobView;
import com.moakiee.thunderbolt.api.crafting.batch.IBatchCraftingProvider;
import com.moakiee.thunderbolt.core.crafting.batch.ParallelBatchCpuHelper;
import com.moakiee.thunderbolt.core.crafting.support.CraftingPatternDelegates;
import net.minecraft.world.level.Level;

/** A synchronous extraction scope used only by LT's time-wheel CPU. Other CPU calls, including
 * reentrant calls on the same thread, retain native behavior unless both task and inventory match. */
public final class TimeWheelBatchInputAllocation {
    private static final ThreadLocal<Scope> CURRENT = new ThreadLocal<>();

    @FunctionalInterface
    public interface BatchCapacityLimiter {
        long limit(KeyCounter[] prototype, long availableCopies);
    }

    private TimeWheelBatchInputAllocation() { }

    public static <T> T withAllocator(IPatternDetails pattern, ListCraftingInventory inventory,
            ExecutionInputAllocator allocator, Supplier<T> dispatch) {
        return withAllocator(pattern, inventory, allocator, null, dispatch);
    }

    public static <T> T withAllocator(IPatternDetails pattern, ListCraftingInventory inventory,
            ExecutionInputAllocator allocator, BatchCapacityLimiter limiter, Supplier<T> dispatch) {
        var previous = CURRENT.get();
        CURRENT.set(new Scope(pattern, inventory, allocator, limiter));
        try {
            return dispatch.get();
        } finally {
            if (previous == null) CURRENT.remove();
            else CURRENT.set(previous);
        }
    }

    public static ParallelBatchCpuHelper.BulkResult extract(IPatternDetails pattern,
            ListCraftingInventory inventory, long copies, boolean shared,
            Map<AEKey, Long> reserved, Level level,
            Supplier<ParallelBatchCpuHelper.BulkResult> original) {
        var scope = CURRENT.get();
        if (scope == null || scope.pattern != pattern || scope.inventory != inventory) {
            return original.get();
        }
        // If the native result representation changes, the CPU retains its ordinary local
        // extraction path. Never fall back to an unguarded native batch for an LT allocation.
        if (!TimeWheelInputExtractor.canExportNativeBatch()) return null;
        var result = TimeWheelInputExtractor.bulkExtract(pattern, inventory, copies, shared, reserved, level,
                (visible, requested) -> scope.allocator.allocate(pattern, visible, level, requested), scope.limiter);
        if (result == null) return null;
        try {
            return TimeWheelInputExtractor.exportNativeBatch(result);
        } catch (ReflectiveOperationException failure) {
            TimeWheelInputExtractor.reinject(result, result.actualCopies, inventory);
            return null;
        }
    }

    /** Called with the native candidate list after its shared-seed selection and balancing sort.
     * No second provider lookup or cache is introduced by the Forge bridge. */
    public static ParallelBatchCpuHelper.BulkResult extractWithAdmission(IPatternDetails pattern,
            ListCraftingInventory inventory, long copies, boolean shared,
            Map<AEKey, Long> reserved, Level level, List<?> candidates, BatchJobView job,
            Supplier<ParallelBatchCpuHelper.BulkResult> original) {
        var scope = CURRENT.get();
        if (scope == null || scope.pattern != pattern || scope.inventory != inventory) return original.get();
        var execution = CraftingPatternDelegates.forBatchExecution(pattern);
        scope.admitted.clear();
        scope.job = job;
        scope.execution = execution;
        var previous = scope.limiter;
        scope.limiter = (prototype, available) -> {
            long total = 0L;
            for (var raw : candidates) {
                var candidate = (TimeWheelBatchCandidate) raw;
                var provider = candidate.ae2lt$provider();
                long capacity = Math.min(candidate.ae2lt$capacity(), available);
                TimeWheelBatchAdmission.PreparedBatch prepared = null;
                try {
                    // Existing Forge endpoints retain their legacy advisory-capacity contract.
                    if (provider instanceof TimeWheelBatchAdmission admission) {
                        prepared = admission.prepareTimeWheelBatch(execution, prototype, capacity, job);
                        if (prepared != null) capacity = Math.min(capacity, Math.max(0L, prepared.capacity()));
                    }
                } catch (Throwable failure) {
                    AELog.warn("[ae2lt] Time-wheel batch admission failed before bulk extraction: %s", failure);
                    capacity = 0L;
                }
                scope.admitted.put(provider, new Admission(capacity, prepared));
                total = capacity > Long.MAX_VALUE - total ? Long.MAX_VALUE : total + capacity;
            }
            return previous == null ? total : Math.min(total, previous.limit(prototype, available));
        };
        try {
            var result = extract(pattern, inventory, copies, shared, reserved, level, original);
            candidates.removeIf(raw -> {
                var admission = scope.admitted.get(((TimeWheelBatchCandidate) raw).ae2lt$provider());
                return admission != null && admission.capacity <= 0;
            });
            return result;
        } finally {
            scope.limiter = previous;
        }
    }

    public static long admittedCapacity(IPatternDetails pattern, ListCraftingInventory inventory,
            Object candidate, long originalCapacity) {
        var scope = CURRENT.get();
        if (scope == null || scope.pattern != pattern || scope.inventory != inventory) return originalCapacity;
        var admission = scope.admitted.get(((TimeWheelBatchCandidate) candidate).ae2lt$provider());
        return admission == null ? originalCapacity : Math.min(originalCapacity, admission.capacity);
    }

    public static long pushAdmitted(IBatchCraftingProvider provider, IPatternDetails execution,
            KeyCounter[] prototype, long copies, BatchJobView job, Supplier<Long> original) {
        var scope = CURRENT.get();
        if (scope == null || scope.execution != execution || scope.job != job) return original.get();
        var admission = scope.admitted.get(provider);
        return admission == null || admission.prepared == null ? original.get() : admission.prepared.push(copies);
    }

    private record Admission(long capacity, TimeWheelBatchAdmission.PreparedBatch prepared) { }

    private static final class Scope {
        final IPatternDetails pattern;
        final ListCraftingInventory inventory;
        final ExecutionInputAllocator allocator;
        final IdentityHashMap<IBatchCraftingProvider, Admission> admitted = new IdentityHashMap<>();
        BatchCapacityLimiter limiter;
        BatchJobView job;
        IPatternDetails execution;

        Scope(IPatternDetails pattern, ListCraftingInventory inventory,
                ExecutionInputAllocator allocator, BatchCapacityLimiter limiter) {
            this.pattern = pattern;
            this.inventory = inventory;
            this.allocator = allocator;
            this.limiter = limiter;
        }
    }
}

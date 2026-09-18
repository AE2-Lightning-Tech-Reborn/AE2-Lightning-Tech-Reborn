package com.moakiee.ae2lt.crafting.timewheel.allocation;

import java.util.Map;
import java.util.function.Supplier;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.crafting.inv.ListCraftingInventory;
import com.moakiee.thunderbolt.core.crafting.batch.ParallelBatchCpuHelper;
import net.minecraft.world.level.Level;

/** A synchronous extraction scope used only by LT's time-wheel CPU. Other CPU calls, including
 * reentrant calls on the same thread, retain native behavior unless both task and inventory match. */
public final class TimeWheelBatchInputAllocation {
    private static final ThreadLocal<Scope> CURRENT = new ThreadLocal<>();

    private TimeWheelBatchInputAllocation() { }

    public static <T> T withAllocator(IPatternDetails pattern, ListCraftingInventory inventory,
            ExecutionInputAllocator allocator, Supplier<T> dispatch) {
        var previous = CURRENT.get();
        CURRENT.set(new Scope(pattern, inventory, allocator));
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
                (visible, requested) -> scope.allocator.allocate(pattern, visible, level, requested));
        if (result == null) return null;
        try {
            return TimeWheelInputExtractor.exportNativeBatch(result);
        } catch (ReflectiveOperationException failure) {
            TimeWheelInputExtractor.reinject(result, result.actualCopies, inventory);
            return null;
        }
    }

    private record Scope(IPatternDetails pattern, ListCraftingInventory inventory,
            ExecutionInputAllocator allocator) { }
}

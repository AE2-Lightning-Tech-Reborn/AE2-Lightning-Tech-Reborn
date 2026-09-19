package com.moakiee.ae2lt.crafting.timewheel;

import java.util.List;
import java.util.function.Function;

import org.jetbrains.annotations.Nullable;

import appeng.api.networking.crafting.ICraftingCPU;
import appeng.menu.me.crafting.CraftingCPURecord;

/** Preserves an explicit LT pool selection while AE2 rebuilds its candidate list. */
public final class TimeWheelManualCpuSelection {
    @Nullable
    private CraftingCPURecord retainedRow;

    @Nullable
    public CraftingCPURecord capture(
            List<CraftingCPURecord> candidates,
            int selectedIndex,
            Function<CraftingCPURecord, ICraftingCPU> cpuOf) {
        // An unavailable row forces AE2 to rebuild and restore() will mark it again. If AE2
        // no longer rebuilds, the pool has become eligible again and this is now a normal row.
        retainedRow = null;
        if (selectedIndex < 0 || selectedIndex >= candidates.size()) return null;
        var selected = candidates.get(selectedIndex);
        return cpuOf.apply(selected) instanceof TimeWheelCraftingCpuPool ? selected : null;
    }

    public int restore(
            List<CraftingCPURecord> candidates,
            int previousIndex,
            @Nullable CraftingCPURecord captured,
            Function<CraftingCPURecord, ICraftingCPU> cpuOf) {
        retainedRow = null;
        if (captured == null) return previousIndex;
        var target = cpuOf.apply(captured);
        var refreshed = new CraftingCPURecord(target.getAvailableStorage(), target.getCoProcessors(), target);
        if (refreshed.getName() == null) refreshed.setName(captured.getName());
        for (int i = 0; i < candidates.size(); i++) {
            if (cpuOf.apply(candidates.get(i)) == target) {
                candidates.set(i, refreshed);
                return i;
            }
        }

        // Keep an unavailable explicit target visible until the player changes the selection.
        // Dropping it would turn the same index into another CPU (or -1 into automatic mode).
        // The pool checks registration and capacity again before extracting any materials.
        int retainedIndex = Math.clamp(previousIndex, 0, candidates.size());
        candidates.add(retainedIndex, refreshed);
        retainedRow = refreshed;
        return retainedIndex;
    }

    public int prepareCycle(List<CraftingCPURecord> candidates, int selectedIndex, boolean forward) {
        int removedIndex = retainedRow == null ? -1 : candidates.indexOf(retainedRow);
        retainedRow = null;
        if (removedIndex < 0) return selectedIndex;
        candidates.remove(removedIndex);
        // Remove the placeholder before applying the player's next/previous action. Otherwise
        // its removal on the following refresh would shift the newly selected CPU's index again.
        if (selectedIndex > removedIndex || selectedIndex == removedIndex && forward) {
            return selectedIndex - 1;
        }
        return selectedIndex;
    }
}

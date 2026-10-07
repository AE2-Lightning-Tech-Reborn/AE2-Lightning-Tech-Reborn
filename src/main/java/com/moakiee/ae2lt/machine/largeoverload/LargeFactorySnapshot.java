package com.moakiee.ae2lt.machine.largeoverload;

import java.util.List;
import appeng.api.stacks.GenericStack;
import net.minecraft.core.BlockPos;

public record LargeFactorySnapshot(String status, boolean formed, boolean passive, boolean networkEnergy,
        String core, long storedEnergy, long energyCapacity, long remainingOperations, long operationsPerTick,
        int pendingTypes, int entryCount, int entryPage, List<Row> rows, List<Issue> issues) {
    public static final LargeFactorySnapshot EMPTY = new LargeFactorySnapshot("unformed", false, false, true, "", 0, 0, 0, 0, 0, 0, 0, List.of(), List.of());
    public record Row(int index, int slot, GenericStack output, boolean enabled, String status,
            String recipe, long operations, long energy, long high, long extreme) { }
    public record Issue(String problem, BlockPos position, String expected) { }
}

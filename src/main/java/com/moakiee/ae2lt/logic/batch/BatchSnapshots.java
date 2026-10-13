package com.moakiee.ae2lt.logic.batch;

import appeng.api.stacks.GenericStack;
import java.util.List;

public final class BatchSnapshots {
    private BatchSnapshots() {}

    /** Check before dispatch so a receipt cannot overflow after inputs have moved. */
    public static long safeCapacity(List<GenericStack> outputs, long capacity) {
        long safe = Math.max(0, capacity);
        for (var output : outputs) {
            if (output.amount() < 0) return 0;
            if (output.amount() > 0) safe = Math.min(safe, Long.MAX_VALUE / output.amount());
        }
        return safe;
    }

    public static List<GenericStack> multiply(List<GenericStack> outputs, long accepted) {
        if (accepted == 0) return List.of();
        return outputs.stream().map(s -> new GenericStack(s.what(), Math.multiplyExact(s.amount(), accepted))).toList();
    }
}

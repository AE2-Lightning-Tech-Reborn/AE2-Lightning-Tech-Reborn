package com.moakiee.ae2lt.crafting.big;

import appeng.api.stacks.AEKey;
import com.moakiee.thunderbolt.core.storage.big.BigAmounts;
import com.moakiee.thunderbolt.core.crafting.big.BigLongSlice;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Map;

/** The same ownership transaction for native long slices and exact provider batches. */
record BigCraftingTransfer(BigInteger copies, Map<AEKey, BigInteger> inputs,
                           Map<AEKey, BigInteger> outputs) {
    BigCraftingTransfer {
        BigAmounts.nonNegative(copies);
        inputs = Map.copyOf(inputs);
        outputs = Map.copyOf(outputs);
        inputs.values().forEach(BigAmounts::nonNegative);
        outputs.values().forEach(BigAmounts::nonNegative);
    }

    /** Bound physical transfers with the same shared-input contract as ordinary batch CPUs. */
    static BigCraftingTransfer plan(BigInteger remaining, Map<AEKey, BigInteger> escrow,
            Map<AEKey, Long> inputs, Map<AEKey, Long> fixedInputs,
            Map<AEKey, Long> outputs, Map<AEKey, Long> fixedOutputs, long capacity) {
        var slice = BigLongSlice.plan(remaining, escrow, inputs, fixedInputs, outputs, fixedOutputs, capacity);
        return new BigCraftingTransfer(BigInteger.valueOf(slice.copies()), slice.inputs(), slice.outputs());
    }

    static BigCraftingTransfer of(long copies, Map<AEKey, Long> inputs, Map<AEKey, Long> fixedInputs,
            Map<AEKey, Long> outputs, Map<AEKey, Long> fixedOutputs) {
        var slice = BigLongSlice.of(copies, inputs, fixedInputs, outputs, fixedOutputs);
        return new BigCraftingTransfer(BigInteger.valueOf(slice.copies()), slice.inputs(), slice.outputs());
    }

    static BigCraftingTransfer of(BigProviderRecipe recipe, BigInteger copies) {
        return new BigCraftingTransfer(copies, scale(recipe.inputs(), copies), scale(recipe.outputs(), copies));
    }

    private static Map<AEKey, BigInteger> scale(Map<AEKey, Long> values, BigInteger copies) {
        var result = new LinkedHashMap<AEKey, BigInteger>();
        if (copies.signum() > 0)
            values.forEach((key, n) -> result.put(key, BigAmounts.nonNegative(copies.multiply(BigInteger.valueOf(n)))));
        return result;
    }
}

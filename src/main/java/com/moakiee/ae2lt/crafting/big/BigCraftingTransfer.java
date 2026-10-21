package com.moakiee.ae2lt.crafting.big;

import appeng.api.stacks.AEKey;
import com.moakiee.thunderbolt.core.storage.big.BigAmounts;
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

    /**
     * Largest long slice that fits remaining copies, escrow, and provider capacity.
     * Thunderbolt 2.0.3-beta2 has no {@code BigLongSlice}; keep the same bounds here.
     */
    static BigCraftingTransfer plan(BigInteger remaining, Map<AEKey, BigInteger> escrow,
            Map<AEKey, Long> inputs, Map<AEKey, Long> fixedInputs,
            Map<AEKey, Long> outputs, Map<AEKey, Long> fixedOutputs, long capacity) {
        if (remaining.signum() <= 0 || capacity <= 0) return ofEmpty();
        long copies = remaining.bitLength() > 63 ? capacity : Math.min(capacity, remaining.longValueExact());
        copies = Math.min(copies, supported(escrow, inputs, fixedInputs));
        copies = Math.min(copies, bounded(outputs, fixedOutputs));
        return copies <= 0 ? ofEmpty() : of(copies, inputs, fixedInputs, outputs, fixedOutputs);
    }

    static BigCraftingTransfer of(long copies, Map<AEKey, Long> inputs, Map<AEKey, Long> fixedInputs,
            Map<AEKey, Long> outputs, Map<AEKey, Long> fixedOutputs) {
        if (copies <= 0) return ofEmpty();
        return new BigCraftingTransfer(BigInteger.valueOf(copies),
                scale(inputs, fixedInputs, copies), scale(outputs, fixedOutputs, copies));
    }

    static BigCraftingTransfer of(BigProviderRecipe recipe, BigInteger copies) {
        return new BigCraftingTransfer(copies, scale(recipe.inputs(), copies), scale(recipe.outputs(), copies));
    }

    private static BigCraftingTransfer ofEmpty() {
        return new BigCraftingTransfer(BigInteger.ZERO, Map.of(), Map.of());
    }

    /** Copies whose per-copy and once-per-batch inputs are both still in escrow. */
    private static long supported(Map<AEKey, BigInteger> escrow, Map<AEKey, Long> inputs, Map<AEKey, Long> fixed) {
        long copies = Long.MAX_VALUE;
        var keys = new LinkedHashMap<AEKey, Long>();
        inputs.forEach(keys::put);
        fixed.forEach((key, amount) -> keys.merge(key, 0L, Long::sum));
        for (var entry : keys.entrySet()) {
            var available = escrow.getOrDefault(entry.getKey(), BigInteger.ZERO);
            long each = entry.getValue();
            long once = fixed.getOrDefault(entry.getKey(), 0L);
            if (once < 0 || each < 0) return 0;
            if (available.compareTo(BigInteger.valueOf(once)) < 0) return 0;
            if (each == 0) continue;
            long affordable = available.subtract(BigInteger.valueOf(once)).divide(BigInteger.valueOf(each)).longValue();
            copies = Math.min(copies, Math.max(0, affordable));
        }
        return copies == Long.MAX_VALUE ? 0 : copies;
    }

    /** Copies whose scaled outputs still fit a non-negative BigInteger amount. */
    private static long bounded(Map<AEKey, Long> outputs, Map<AEKey, Long> fixed) {
        long copies = Long.MAX_VALUE;
        for (var entry : outputs.entrySet()) {
            long each = entry.getValue();
            long once = fixed.getOrDefault(entry.getKey(), 0L);
            if (each < 0 || once < 0) return 0;
            if (each == 0) continue;
            var room = BigInteger.ONE.shiftLeft(BigAmounts.MAX_BITS).subtract(BigInteger.ONE)
                    .subtract(BigInteger.valueOf(once));
            if (room.signum() < 0) return 0;
            copies = Math.min(copies, room.divide(BigInteger.valueOf(each)).longValue());
        }
        return copies == Long.MAX_VALUE ? 0 : copies;
    }

    private static Map<AEKey, BigInteger> scale(Map<AEKey, Long> each, Map<AEKey, Long> fixed, long copies) {
        var result = new LinkedHashMap<AEKey, BigInteger>();
        var amount = BigInteger.valueOf(copies);
        each.forEach((key, n) -> result.put(key, BigAmounts.nonNegative(amount.multiply(BigInteger.valueOf(n)))));
        fixed.forEach((key, n) -> result.merge(key, BigAmounts.nonNegative(BigInteger.valueOf(n)), BigInteger::add));
        result.values().removeIf(value -> value.signum() == 0);
        return result;
    }

    private static Map<AEKey, BigInteger> scale(Map<AEKey, Long> values, BigInteger copies) {
        var result = new LinkedHashMap<AEKey, BigInteger>();
        if (copies.signum() > 0)
            values.forEach((key, n) -> result.put(key, BigAmounts.nonNegative(copies.multiply(BigInteger.valueOf(n)))));
        return result;
    }
}

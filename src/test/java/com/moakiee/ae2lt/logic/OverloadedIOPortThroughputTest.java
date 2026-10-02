package com.moakiee.ae2lt.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class OverloadedIOPortThroughputTest {
    @Test
    void everyMatrixMultipliesTheAmountByThirtyTwoUntilTheLongBoundary() {
        long[] expected = {
                8_388_608L, 268_435_456L, 8_589_934_592L, 274_877_906_944L,
                8_796_093_022_208L, 281_474_976_710_656L, 9_007_199_254_740_992L,
                288_230_376_151_711_744L, Long.MAX_VALUE
        };
        int[] expectedAttempts = {4, 6, 8, 10, 12, 14, 16, 16, 16};
        for (int matrices = 0; matrices < expected.length; matrices++) {
            assertEquals(expected[matrices], OverloadedIOPortThroughput.operationCap(matrices));
            assertEquals(expectedAttempts[matrices], OverloadedIOPortThroughput.attemptLimit(matrices));
        }
        assertEquals(8, OverloadedIOPortThroughput.MAX_MATRICES);
        assertEquals(16, OverloadedIOPortThroughput.MAX_ATTEMPTS);
    }

    @Test
    void baseRateWithoutAccelerationExceedsFullyAcceleratedExtendedAeForItemsAndFluids() {
        long extendedAePerTick = 65_536;
        long baseCap = OverloadedIOPortThroughput.operationCap(0);
        assertTrue(baseCap > extendedAePerTick * 5);
        assertTrue(OverloadedIOPortThroughput.nativeAmountCap(baseCap, 125) > extendedAePerTick * 125 * 5);
        assertEquals(25.6, baseCap / (5.0 * extendedAePerTick));
    }

    @Test
    void nativeUnitConversionPreservesExactAmountsAndSaturatesBeforeOverflow() {
        long baseCap = OverloadedIOPortThroughput.operationCap(0);
        assertEquals(8_388_608L, OverloadedIOPortThroughput.nativeAmountCap(baseCap, 1));
        assertEquals(1_048_576_000L, OverloadedIOPortThroughput.nativeAmountCap(baseCap, 125));
        assertEquals(34_359_738_368L, OverloadedIOPortThroughput.nativeAmountCap(baseCap, 4096));
        assertEquals(1_125_899_906_842_624_000L,
                OverloadedIOPortThroughput.nativeAmountCap(OverloadedIOPortThroughput.operationCap(6), 125));
        assertEquals(Long.MAX_VALUE,
                OverloadedIOPortThroughput.nativeAmountCap(OverloadedIOPortThroughput.operationCap(7), 125));
        assertEquals(Long.MAX_VALUE, OverloadedIOPortThroughput.nativeAmountCap(Long.MAX_VALUE, 125));
        assertEquals(Long.MAX_VALUE, OverloadedIOPortThroughput.nativeAmountCap(Long.MAX_VALUE, 1));
    }
}

package com.moakiee.ae2lt.logic.energy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

class OverloadedIoCostOverflowTest {
    @Test
    void unlimitedAmountsCannotWrapIntoFreeOrNegativeTransferCosts() {
        var random = new SplittableRandom(20261004L);
        for (int index = 0; index < 20_000; index++) {
            long amount = index == 0 ? Long.MAX_VALUE : Long.MAX_VALUE - random.nextLong(1_000_000);
            long perOperation = index % 3 == 0 ? 4 : index % 3 == 1 ? 500
                    : random.nextLong(1, Long.MAX_VALUE);
            double expected = BigInteger.valueOf(amount)
                    .add(BigInteger.valueOf(perOperation - 1))
                    .divide(BigInteger.valueOf(perOperation)).doubleValue();
            assertEquals(expected, OverloadedIoCost.cost(amount, perOperation));
            assertTrue(OverloadedIoCost.cost(amount, perOperation) > 0);
        }
    }
}

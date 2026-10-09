package com.moakiee.ae2lt.machine.largeoverload;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigInteger;
import java.util.Random;
import org.junit.jupiter.api.Test;
import com.moakiee.ae2lt.me.key.LightningKey;

class LargeFactoryLightningCostTest {
    @Test
    void ordinaryFloorDoesNotAddASecondFeeOrReduceExpensiveRecipes() {
        assertEquals(new LargeFactoryLightningCost(1, 0),
                LargeFactoryLightningCost.ordinary(LightningKey.Tier.HIGH_VOLTAGE, 0));
        assertEquals(new LargeFactoryLightningCost(1, 0),
                LargeFactoryLightningCost.ordinary(LightningKey.Tier.EXTREME_HIGH_VOLTAGE, 0));
        assertEquals(new LargeFactoryLightningCost(1, 0),
                LargeFactoryLightningCost.ordinary(LightningKey.Tier.HIGH_VOLTAGE, 1));
        assertEquals(new LargeFactoryLightningCost(3, 0),
                LargeFactoryLightningCost.ordinary(LightningKey.Tier.HIGH_VOLTAGE, 3));
        assertEquals(new LargeFactoryLightningCost(0, 2),
                LargeFactoryLightningCost.ordinary(LightningKey.Tier.EXTREME_HIGH_VOLTAGE, 2));
    }

    @Test
    void compensatesOnlyTheMissingExtremeUnitsAndNeverPaysHighWithExtreme() {
        var cost = new LargeFactoryLightningCost(0, 2);
        assertEquals(new LargeFactoryLightningCost.Payment(0, 2), cost.plan(1, 8, 2).orElseThrow());
        assertEquals(new LargeFactoryLightningCost.Payment(4, 1), cost.plan(1, 8, 1).orElseThrow());
        assertEquals(new LargeFactoryLightningCost.Payment(8, 0), cost.plan(1, 8, 0).orElseThrow());
        assertTrue(cost.plan(1, 7, 0).isEmpty());
        assertTrue(new LargeFactoryLightningCost(1, 0).plan(1, 0, Long.MAX_VALUE).isEmpty());
        assertEquals(new LargeFactoryLightningCost.Payment(7, 1),
                new LargeFactoryLightningCost(3, 2).plan(1, 7, 1).orElseThrow());
    }

    @Test
    void fullFirmamentTickPays1024ExtremeOr4096HighAndFourSourceCopiesPayFourTimes() {
        var cost = LargeFactoryLightningCost.FIRMAMENT;
        assertEquals(new LargeFactoryLightningCost.Payment(0, 1024), cost.plan(1024, 0, 1024).orElseThrow());
        assertEquals(new LargeFactoryLightningCost.Payment(4096, 0), cost.plan(1024, 4096, 0).orElseThrow());
        assertEquals(new LargeFactoryLightningCost.Payment(0, 4), cost.plan(4, 0, 1024).orElseThrow());
        assertEquals(1023, cost.maxPayableOperations(1024, 4095, 0));
        assertEquals(24, cost.maxPayableOperations(100, 96, 0));
    }

    @Test
    void splitBatchesHaveTheSameTotalFeeAsOneBatch() {
        var cost = new LargeFactoryLightningCost(3, 2);
        var whole = cost.plan(100, 2000, 70).orElseThrow();
        var first = cost.plan(24, 2000, 70).orElseThrow();
        var rest = cost.plan(76, 2000 - first.highVoltage(), 70 - first.extremeHighVoltage()).orElseThrow();
        assertEquals(whole.highVoltage(), first.highVoltage() + rest.highVoltage());
        assertEquals(whole.extremeHighVoltage(), first.extremeHighVoltage() + rest.extremeHighVoltage());
    }

    @Test
    void overflowCannotSaturateIntoAnAffordableSmallerCharge() {
        assertTrue(new LargeFactoryLightningCost(Long.MAX_VALUE, 0).plan(2, Long.MAX_VALUE, Long.MAX_VALUE).isEmpty());
        assertTrue(new LargeFactoryLightningCost(0, Long.MAX_VALUE).plan(1, Long.MAX_VALUE, 0).isEmpty());
        assertTrue(new LargeFactoryLightningCost(Long.MAX_VALUE, 1).plan(1, Long.MAX_VALUE, 0).isEmpty());
        assertEquals(Long.MAX_VALUE / 4, LargeFactoryLightningCost.FIRMAMENT
                .maxPayableOperations(Long.MAX_VALUE, Long.MAX_VALUE, 0));
        assertEquals(1, new LargeFactoryLightningCost(Long.MAX_VALUE, 0)
                .maxPayableOperations(Long.MAX_VALUE, Long.MAX_VALUE, 0));
    }

    @Test
    void quotesAgreeWithBigIntegerOracleAcrossLargeAndMixedCosts() {
        var random = new Random(72641);
        for (int i = 0; i < 1000; i++) {
            long hv = random.nextLong() & Long.MAX_VALUE;
            long ehv = random.nextLong() & Long.MAX_VALUE;
            long count = i % 3 == 0 ? 1 : random.nextLong() & Long.MAX_VALUE;
            long availableHv = random.nextLong() & Long.MAX_VALUE;
            long availableEhv = random.nextLong() & Long.MAX_VALUE;
            var cost = new LargeFactoryLightningCost(hv, ehv);
            BigInteger requiredEhv = BigInteger.valueOf(ehv).multiply(BigInteger.valueOf(count));
            BigInteger useEhv = requiredEhv.min(BigInteger.valueOf(availableEhv));
            BigInteger requiredHv = BigInteger.valueOf(hv).multiply(BigInteger.valueOf(count))
                    .add(requiredEhv.subtract(useEhv).multiply(BigInteger.valueOf(4)));
            boolean feasible = requiredEhv.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) <= 0
                    && requiredHv.compareTo(BigInteger.valueOf(availableHv)) <= 0;
            assertEquals(feasible, cost.plan(count, availableHv, availableEhv).isPresent());
            if (feasible) {
                var plan = cost.plan(count, availableHv, availableEhv).orElseThrow();
                assertEquals(requiredHv.longValueExact(), plan.highVoltage());
                assertEquals(useEhv.longValueExact(), plan.extremeHighVoltage());
            }
        }
    }

    @Test
    void closedFormMaximumIsFeasibleAndItsNextOperationIsNot() {
        var random = new Random(912723);
        for (int i = 0; i < 3000; i++) {
            long hv = i % 3 == 0 ? 0 : i % 3 == 1 ? 1 + random.nextInt(1000) : random.nextLong() & Long.MAX_VALUE;
            long ehv = i % 3 == 1 ? 0 : i % 3 == 0 ? 1 + random.nextInt(1000) : random.nextLong() & Long.MAX_VALUE;
            long requested = random.nextLong() & Long.MAX_VALUE;
            long availableHv = random.nextLong() & Long.MAX_VALUE, availableEhv = random.nextLong() & Long.MAX_VALUE;
            var cost = new LargeFactoryLightningCost(hv, ehv);
            long maximum = cost.maxPayableOperations(requested, availableHv, availableEhv);
            assertTrue(feasible(hv, ehv, maximum, availableHv, availableEhv));
            if (maximum < requested) assertFalse(feasible(hv, ehv, maximum + 1, availableHv, availableEhv));
        }
    }

    private static boolean feasible(long hv, long ehv, long n, long availableHv, long availableEhv) {
        var extreme = BigInteger.valueOf(ehv).multiply(BigInteger.valueOf(n));
        var high = BigInteger.valueOf(hv).multiply(BigInteger.valueOf(n)).add(
                extreme.subtract(BigInteger.valueOf(availableEhv)).max(BigInteger.ZERO).shiftLeft(2));
        return extreme.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) <= 0 && high.compareTo(BigInteger.valueOf(availableHv)) <= 0;
    }

    @Test
    void zeroBatchIsFreeButInvalidInputIsRejected() {
        assertEquals(new LargeFactoryLightningCost.Payment(0, 0),
                LargeFactoryLightningCost.FIRMAMENT.plan(0, 0, 0).orElseThrow());
        assertThrows(IllegalArgumentException.class, () -> new LargeFactoryLightningCost(0, 0));
        assertThrows(IllegalArgumentException.class, () -> LargeFactoryLightningCost.FIRMAMENT.plan(-1, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> LargeFactoryLightningCost.FIRMAMENT.maxPayableOperations(1, -1, 0));
    }
}

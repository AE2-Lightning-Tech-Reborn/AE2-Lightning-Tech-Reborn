package com.moakiee.ae2lt.celestweave;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static com.moakiee.ae2lt.celestweave.ShieldChargeWindow.Profile.*;

import org.junit.jupiter.api.Test;

class ShieldChargeWindowTest {
    @Test
    void phaseBillsOnlyTheNewHighWaterMarkInsideTwentyTicks() {
        var first = ShieldChargeWindow.quote(ShieldChargeWindow.State.EMPTY, PHASE, 100L, 100D);
        assertEquals(2_000_000L, first.feCost());
        assertEquals(200L, first.ehvCost());
        var smaller = ShieldChargeWindow.quote(first.nextState(), PHASE, 110L, 80D);
        assertEquals(0L, smaller.feCost());
        assertEquals(0L, smaller.ehvCost());
        var larger = ShieldChargeWindow.quote(smaller.nextState(), PHASE, 115L, 150D);
        assertEquals(1_000_000L, larger.feCost());
        assertEquals(100L, larger.ehvCost());
        assertEquals(120L, larger.nextState().windowUntil());
    }

    @Test
    void phasePaysForOnly1024AbsorbedDamageAtTwoEhvEach() {
        var first = ShieldChargeWindow.quote(ShieldChargeWindow.State.EMPTY, PHASE, 100L, 1024D);
        assertEquals(20_480_000L, first.feCost());
        assertEquals(2048L, first.ehvCost());
        var extreme = ShieldChargeWindow.quote(ShieldChargeWindow.State.EMPTY, PHASE, 100L, Float.MAX_VALUE);
        assertEquals(first, extreme);
        var repeated = ShieldChargeWindow.quote(first.nextState(), PHASE, 119L, Float.MAX_VALUE);
        assertEquals(0L, repeated.feCost());
        assertEquals(0L, repeated.ehvCost());
    }

    @Test
    void overloadCapsEachResourceIndependentlyAndStopsChargingAtTheCap() {
        var first = ShieldChargeWindow.quote(ShieldChargeWindow.State.EMPTY, OVERLOAD, 100L, 10_000D);
        assertEquals(200_000_000L, first.feCost());
        assertEquals(16_384L, first.ehvCost());
        var extreme = ShieldChargeWindow.quote(first.nextState(), OVERLOAD, 105L, Float.MAX_VALUE);
        assertEquals(19_800_000_000L, extreme.feCost());
        assertEquals(0L, extreme.ehvCost());
        var repeat = ShieldChargeWindow.quote(extreme.nextState(), OVERLOAD, 119L, Double.POSITIVE_INFINITY);
        assertEquals(0L, repeat.feCost());
        assertEquals(0L, repeat.ehvCost());
    }

    @Test
    void shieldAndLastStandShareTheSameResourceHighWaterMarks() {
        var shield = ShieldChargeWindow.quote(ShieldChargeWindow.State.EMPTY, OVERLOAD, 100L, 400D);
        var lastStand = ShieldChargeWindow.quoteCosts(shield.nextState(), OVERLOAD, 105L, 2_000_000_000L, 512L);
        assertEquals(1_992_000_000L, lastStand.feCost());
        assertEquals(0L, lastStand.ehvCost()); // 800 EHV already paid for the shield.
        var capped = ShieldChargeWindow.quoteCosts(lastStand.nextState(), OVERLOAD, 110L, Long.MAX_VALUE, Long.MAX_VALUE);
        assertEquals(18_000_000_000L, capped.feCost());
        assertEquals(15_584L, capped.ehvCost());
        var shieldAfterDeath = ShieldChargeWindow.quote(capped.nextState(), OVERLOAD, 115L, Float.MAX_VALUE);
        assertEquals(0L, shieldAfterDeath.feCost());
        assertEquals(0L, shieldAfterDeath.ehvCost());
    }

    @Test
    void twentyTickBoundaryChargesAFullNewWindow() {
        var first = ShieldChargeWindow.quote(ShieldChargeWindow.State.EMPTY, OVERLOAD, 100L, Float.MAX_VALUE);
        var next = ShieldChargeWindow.quote(first.nextState(), OVERLOAD, 120L, Float.MAX_VALUE);
        assertEquals(20_000_000_000L, next.feCost());
        assertEquals(16_384L, next.ehvCost());
        assertEquals(140L, next.nextState().windowUntil());
    }

    @Test
    void fractionalDamageRoundsUpAndInvalidDamageDoesNotCharge() {
        var fraction = ShieldChargeWindow.quote(ShieldChargeWindow.State.EMPTY, PHASE, 100L, 0.25D);
        assertEquals(5000L, fraction.feCost());
        assertEquals(1L, fraction.ehvCost());
        for (double damage : new double[] {Double.NaN, -1D, 0D}) {
            var invalid = ShieldChargeWindow.quote(ShieldChargeWindow.State.EMPTY, PHASE, 100L, damage);
            assertEquals(0L, invalid.feCost());
            assertEquals(0L, invalid.ehvCost());
        }
    }

    @Test
    void windowEndSaturatesInsteadOfOverflowing() {
        var quote = ShieldChargeWindow.quote(ShieldChargeWindow.State.EMPTY, OVERLOAD, Long.MAX_VALUE - 10L,
                Double.POSITIVE_INFINITY);
        assertEquals(Long.MAX_VALUE, quote.nextState().windowUntil());
        assertEquals(20_000_000_000L, quote.feCost());
        assertEquals(16_384L, quote.ehvCost());
    }
}

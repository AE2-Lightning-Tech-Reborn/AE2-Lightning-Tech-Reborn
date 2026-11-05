package com.moakiee.ae2lt.logic.energy;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class WirelessEnergyWorkBudgetTest {
    @Test void slowSimulationStillAllowsProgressThenDefersFurtherCallbacks() {
        var clock = new AtomicLong(100);
        var work = new WirelessEnergyWorkBudget(2, clock::get);
        assertTrue(work.hasTime());
        clock.addAndGet(3_000_000);
        assertFalse(work.hasTime());
        assertTrue(work.canDispatch(), "an expired planning budget must not starve delivery");
        work.dispatched();
        assertFalse(work.canDispatch(), "slow callbacks must defer subsequent deliveries");
    }

    @Test void fastReceiversMayUseAllRemainingTimeWithoutAnEnergyOrTargetQuota() {
        var clock = new AtomicLong();
        var work = new WirelessEnergyWorkBudget(2, clock::get);
        for (int i = 0; i < 8192; i++) {
            assertTrue(work.canDispatch());
            work.dispatched();
            clock.addAndGet(100);
        }
        clock.set(2_000_000);
        assertFalse(work.canDispatch());
    }
}

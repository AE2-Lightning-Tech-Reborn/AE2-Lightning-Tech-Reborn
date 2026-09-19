package com.moakiee.ae2lt.crafting.timewheel;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TimeWheelCraftingCpuPoolPhysicalTickTest {
    @Test
    void secondVisitOnTheSamePhysicalTickIsRejectedAndTheNextTickStartsAgain() {
        var pool = new TimeWheelCraftingCpuPool(null, 1L, 0, 1L, false);

        assertTrue(pool.beginPhysicalTick(1L));
        assertFalse(pool.beginPhysicalTick(1L));
        assertTrue(pool.beginPhysicalTick(2L));
        assertFalse(pool.beginPhysicalTick(2L));
        assertTrue(pool.beginPhysicalTick(3L));
    }
}

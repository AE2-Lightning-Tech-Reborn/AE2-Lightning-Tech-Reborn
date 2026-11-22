package com.moakiee.ae2lt.compat.mmcr;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.Test;

class MmcrSettlementTest {
    @Test
    void acceptedSliceWaitsForNativeSettlementAndProbesAtMostOncePerTick() {
        var settlement = new MmcrBatchIntegration.Settlement();
        settlement.pause(10);
        assertTrue(settlement.waiting(10, () -> fail("probed during acceptance")));
        assertTrue(settlement.waiting(11, () -> false));
        assertTrue(settlement.waiting(11, () -> fail("probed twice")));
        assertTrue(settlement.waiting(12, () -> { throw new IllegalStateException(); }));
        assertFalse(settlement.waiting(13, () -> true));
        assertFalse(settlement.waiting(14, () -> fail("probed after recovery")));
    }
}

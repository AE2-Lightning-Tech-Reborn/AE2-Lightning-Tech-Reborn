package com.moakiee.ae2lt.logic.energy;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class MachineRechargeControllerTest {
    @Test
    void steadyConsumerBatchesRefillsWithoutLosingWorkOrEnergy() {
        var controller = new MachineRechargeController();
        long stored = 1_000L;
        long transferred = 0L;
        int calls = 0;
        for (int tick = 0; tick < 1_000; tick++) {
            if (controller.shouldRecharge(stored, 1_000L, 10L, Long.MAX_VALUE)) {
                long received = 1_000L - stored;
                transferred += received;
                stored += received;
                calls++;
                controller.afterRecharge(stored, 1_000L);
            }
            assertTrue(stored >= 10L, "consumer must run every tick");
            stored -= 10L;
        }
        assertEquals(10_000L, 1_000L + transferred - stored);
        assertEquals(13, calls); // Top-up on every deficit would make 999 network calls.
    }

    @Test
    void partialAndFailedTransfersKeepRefillingUntilHighWatermark() {
        var controller = new MachineRechargeController();
        assertTrue(controller.shouldRecharge(250, 1_000, 10, Long.MAX_VALUE));
        controller.afterRecharge(250, 1_000); // Empty network; do not lose the retry.
        assertTrue(controller.shouldRecharge(250, 1_000, 10, Long.MAX_VALUE));
        controller.afterRecharge(500, 1_000);
        assertTrue(controller.shouldRecharge(500, 1_000, 10, Long.MAX_VALUE));
        controller.afterRecharge(900, 1_000);
        assertFalse(controller.shouldRecharge(890, 1_000, 10, Long.MAX_VALUE));
    }

    @Test
    void suddenDemandBypassesLowWatermark() {
        var controller = new MachineRechargeController();
        assertFalse(controller.shouldRecharge(600, 1_000, 10, Long.MAX_VALUE));
        assertTrue(controller.shouldRecharge(600, 1_000, 800, Long.MAX_VALUE));
        controller.afterRecharge(1_000, 1_000);
        assertTrue(controller.shouldRecharge(200, 1_000, 800, Long.MAX_VALUE));
        assertTrue(controller.shouldRecharge(950, 1_000, 1_000, Long.MAX_VALUE));
    }

    @Test
    void limitedTransferRemainsBoundedAndSustainsConsumer() {
        var controller = new MachineRechargeController();
        long stored = 1_000;
        int calls = 0;
        for (int tick = 0; tick < 500; tick++) {
            if (controller.shouldRecharge(stored, 1_000, 100, 200)) {
                stored += Math.min(200, 1_000 - stored);
                calls++;
                controller.afterRecharge(stored, 1_000);
            }
            assertTrue(stored >= 100);
            assertTrue(stored <= 1_000);
            stored -= 100;
        }
        assertTrue(calls < 300);
    }

    @Test
    void transferSlowerThanConsumptionKeepsOriginalRefillCadence() {
        var controller = new MachineRechargeController();
        long stored = 1_000;
        long baseline = stored;
        for (int tick = 0; tick < 50; tick++) {
            baseline += Math.min(25, 1_000 - baseline);
            if (controller.shouldRecharge(stored, 1_000, 100, 25)) {
                stored += Math.min(25, 1_000 - stored);
                controller.afterRecharge(stored, 1_000);
            }
            assertEquals(baseline, stored);
            stored -= Math.min(stored, 100);
            baseline -= Math.min(baseline, 100);
        }
    }

    @Test
    void externalChargeStopsRefillAndLongCapacityDoesNotOverflow() {
        var controller = new MachineRechargeController();
        assertTrue(controller.shouldRecharge(0, Long.MAX_VALUE, 1, Long.MAX_VALUE));
        assertFalse(controller.shouldRecharge(Long.MAX_VALUE, Long.MAX_VALUE, 1, Long.MAX_VALUE));
        assertFalse(controller.shouldRecharge(Long.MAX_VALUE / 2, Long.MAX_VALUE, 1, Long.MAX_VALUE));
        assertTrue(controller.shouldRecharge(Long.MAX_VALUE / 4, Long.MAX_VALUE, 1, Long.MAX_VALUE));
        assertFalse(controller.shouldRecharge(1, 1, 1, Long.MAX_VALUE));
        assertTrue(controller.shouldRecharge(0, 1, 1, Long.MAX_VALUE));
        assertFalse(controller.shouldRecharge(0, 0, 1, Long.MAX_VALUE));
    }
}

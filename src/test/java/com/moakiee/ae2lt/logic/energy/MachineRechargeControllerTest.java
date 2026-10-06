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
            if (controller.shouldRecharge(stored, 1_000L, 10L, Long.MAX_VALUE, tick)) {
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
        assertTrue(calls <= 50, "top-ups must remain batched instead of extracting every tick");
    }

    @Test
    void initialOrReloadedIdleMachineChargesAtAnyDeficit() {
        for (long stored : new long[] {0, 250, 500, 900, 999}) {
            assertTrue(new MachineRechargeController().shouldRecharge(stored, 1_000, 0, Long.MAX_VALUE, 400));
        }
    }

    @Test
    void idleMachineWithSmallDeficitRefillsWithinOneSecond() {
        var controller = new MachineRechargeController();
        assertFalse(controller.shouldRecharge(1_000, 1_000, 0, Long.MAX_VALUE, 0));
        for (int tick = 1; tick < 20; tick++) {
            assertFalse(controller.shouldRecharge(990, 1_000, 0, Long.MAX_VALUE, tick));
        }
        assertTrue(controller.shouldRecharge(990, 1_000, 0, Long.MAX_VALUE, 20));
        controller.afterRecharge(1_000, 1_000);
        assertFalse(controller.shouldRecharge(1_000, 1_000, 0, Long.MAX_VALUE, 21));
    }

    @Test
    void partialAndFailedTransfersKeepRefillingAllTheWayToFull() {
        var controller = new MachineRechargeController();
        assertTrue(controller.shouldRecharge(250, 1_000, 10, Long.MAX_VALUE, 0));
        controller.afterRecharge(250, 1_000);
        assertTrue(controller.shouldRecharge(250, 1_000, 10, Long.MAX_VALUE, 20));
        controller.afterRecharge(900, 1_000);
        assertTrue(controller.shouldRecharge(900, 1_000, 10, Long.MAX_VALUE, 21));
        controller.afterRecharge(999, 1_000);
        assertTrue(controller.shouldRecharge(999, 1_000, 0, Long.MAX_VALUE, 22));
        controller.afterRecharge(1_000, 1_000);
        assertFalse(controller.shouldRecharge(990, 1_000, 10, Long.MAX_VALUE, 23));
    }

    @Test
    void suddenDemandBypassesTheBatchInterval() {
        var controller = new MachineRechargeController();
        assertFalse(controller.shouldRecharge(1_000, 1_000, 10, Long.MAX_VALUE, 0));
        assertFalse(controller.shouldRecharge(600, 1_000, 10, Long.MAX_VALUE, 1));
        assertTrue(controller.shouldRecharge(600, 1_000, 800, Long.MAX_VALUE, 2));
        controller.afterRecharge(1_000, 1_000);
        assertTrue(controller.shouldRecharge(200, 1_000, 800, Long.MAX_VALUE, 3));
        controller.afterRecharge(1_000, 1_000);
        assertTrue(controller.shouldRecharge(950, 1_000, 1_000, Long.MAX_VALUE, 4));
    }

    @Test
    void limitedTransferRemainsBoundedAndSustainsConsumer() {
        var controller = new MachineRechargeController();
        long stored = 1_000;
        int calls = 0;
        for (int tick = 0; tick < 500; tick++) {
            if (controller.shouldRecharge(stored, 1_000, 100, 200, tick)) {
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
            if (controller.shouldRecharge(stored, 1_000, 100, 25, tick)) {
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
        assertTrue(controller.shouldRecharge(0, Long.MAX_VALUE, 1, Long.MAX_VALUE, 0));
        assertFalse(controller.shouldRecharge(Long.MAX_VALUE, Long.MAX_VALUE, 1, Long.MAX_VALUE, 1));
        assertFalse(controller.shouldRecharge(Long.MAX_VALUE / 2, Long.MAX_VALUE, 1, Long.MAX_VALUE, 2));
        assertTrue(controller.shouldRecharge(Long.MAX_VALUE / 4, Long.MAX_VALUE, 1, Long.MAX_VALUE, 3));
        assertFalse(controller.shouldRecharge(1, 1, 1, Long.MAX_VALUE, 4));
        assertTrue(controller.shouldRecharge(0, 1, 1, Long.MAX_VALUE, 5));
        assertFalse(controller.shouldRecharge(0, 0, 1, Long.MAX_VALUE, 6));
    }

    @Test
    void clockResetDoesNotDelayRefill() {
        var controller = new MachineRechargeController();
        assertFalse(controller.shouldRecharge(1_000, 1_000, 0, Long.MAX_VALUE, 400));
        assertTrue(controller.shouldRecharge(900, 1_000, 0, Long.MAX_VALUE, 1));
    }
}

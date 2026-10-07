package com.moakiee.ae2lt.crafting.timewheel;

import static org.junit.jupiter.api.Assertions.*;

import appeng.api.stacks.KeyCounter;
import com.moakiee.ae2lt.me.key.LightningKey;
import org.junit.jupiter.api.Test;

class DeferredCraftingOutputsTest {
    @Test
    void singleOutputsAccumulateAndUpgradeWithoutLosingOwnership() {
        var queue = new DeferredCraftingOutputs();
        assertTrue(queue.enqueue(LightningKey.HIGH_VOLTAGE, 7));
        assertTrue(queue.enqueue(LightningKey.HIGH_VOLTAGE, 5));
        assertTrue(queue.enqueue(LightningKey.EXTREME_HIGH_VOLTAGE, 3));
        assertTrue(queue.enqueue(LightningKey.HIGH_VOLTAGE, 2));
        var received = new KeyCounter();
        queue.drain((key, amount) -> {
            assertFalse(queue.enqueue(key, 1));
            received.add(key, amount);
        }, (key, amount) -> fail());
        assertEquals(14, received.get(LightningKey.HIGH_VOLTAGE));
        assertEquals(3, received.get(LightningKey.EXTREME_HIGH_VOLTAGE));
        queue.drain((key, amount) -> fail("duplicate output"), (key, amount) -> fail());
    }

    @Test
    void rejectedMixedOfferLeavesExistingSingleOutputUntouched() {
        var queue = new DeferredCraftingOutputs();
        assertTrue(queue.enqueue(LightningKey.HIGH_VOLTAGE, Long.MAX_VALUE));
        assertFalse(queue.enqueue(LightningKey.HIGH_VOLTAGE, 1));
        assertFalse(queue.enqueue(LightningKey.EXTREME_HIGH_VOLTAGE, -1));
        var mixed = new KeyCounter();
        mixed.add(LightningKey.EXTREME_HIGH_VOLTAGE, 10);
        mixed.add(LightningKey.HIGH_VOLTAGE, 1);
        assertFalse(queue.enqueue(mixed));
        var received = new KeyCounter();
        queue.drain(received::add, (key, amount) -> fail());
        assertEquals(Long.MAX_VALUE, received.get(LightningKey.HIGH_VOLTAGE));
        assertEquals(0, received.get(LightningKey.EXTREME_HIGH_VOLTAGE));
    }

    @Test
    void singleCallbackFailureDoesNotReturnAlreadyTransferredOutputAgain() {
        var queue = new DeferredCraftingOutputs();
        assertTrue(queue.enqueue(LightningKey.HIGH_VOLTAGE, 4));
        assertThrows(IllegalStateException.class, () -> queue.drain((key, amount) -> {
            assertEquals(4, amount);
            throw new IllegalStateException("receiver owns the rejected remainder");
        }, (key, amount) -> fail("ownership already passed")));
        queue.drain((key, amount) -> fail("duplicate output"), (key, amount) -> fail());
        assertFalse(queue.enqueue(LightningKey.HIGH_VOLTAGE, 1));
    }

    @Test
    void failureAfterUpgradeRecoversEveryOtherOutputExactlyOnce() {
        var queue = new DeferredCraftingOutputs();
        queue.enqueue(LightningKey.HIGH_VOLTAGE, 4);
        queue.enqueue(LightningKey.EXTREME_HIGH_VOLTAGE, 7);
        var received = new KeyCounter();
        var recovered = new KeyCounter();
        assertThrows(IllegalStateException.class, () -> queue.drain((key, amount) -> {
            received.add(key, amount);
            throw new IllegalStateException("receiver owns current output");
        }, recovered::add));
        for (var key : java.util.List.of(LightningKey.HIGH_VOLTAGE, LightningKey.EXTREME_HIGH_VOLTAGE)) {
            assertEquals(key == LightningKey.HIGH_VOLTAGE ? 4 : 7, received.get(key) + recovered.get(key));
            assertTrue(received.get(key) == 0 || recovered.get(key) == 0);
        }
        queue.drain((key, amount) -> fail(), (key, amount) -> fail());
    }

    @Test
    void copiesBorrowedCounterAndClosesSinkBeforeDelivery() {
        var queue = new DeferredCraftingOutputs();
        var offered = new KeyCounter();
        offered.add(LightningKey.HIGH_VOLTAGE, 7);
        assertTrue(queue.enqueue(offered));
        offered.clear();
        long[] delivered = {0};
        queue.drain((key, amount) -> {
            assertFalse(queue.enqueue(offered));
            delivered[0] += amount;
        }, (key, amount) -> fail("unexpected fallback"));
        assertEquals(7, delivered[0]);
        assertFalse(queue.enqueue(offered));
        queue.drain((key, amount) -> fail("duplicate return"), (key, amount) -> fail());
    }

    @Test
    void overflowingOfferIsRejectedWithoutTransferringAnyOfItsOutputs() {
        var queue = new DeferredCraftingOutputs();
        var offered = new KeyCounter();
        offered.add(LightningKey.HIGH_VOLTAGE, Long.MAX_VALUE);
        assertTrue(queue.enqueue(offered));
        offered.clear();
        offered.add(LightningKey.HIGH_VOLTAGE, 1);
        offered.add(LightningKey.EXTREME_HIGH_VOLTAGE, 2);
        assertFalse(queue.enqueue(offered));
        var received = new KeyCounter();
        queue.drain(received::add, (key, amount) -> fail());
        assertEquals(Long.MAX_VALUE, received.get(LightningKey.HIGH_VOLTAGE));
        assertEquals(0, received.get(LightningKey.EXTREME_HIGH_VOLTAGE));
    }
}

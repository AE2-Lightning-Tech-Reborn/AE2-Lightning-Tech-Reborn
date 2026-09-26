package com.moakiee.ae2lt.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import net.minecraft.network.chat.Component;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Items;

class BufferedInterfaceInputTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void simulationDoesNotReserveCapacityAndSavedInputIsWritten() {
        var input = new BufferedInterfaceInput();
        var key = AEItemKey.of(Items.IRON_INGOT);
        long capacity = BufferedInterfaceInput.capacity(key.getType());
        assertEquals(capacity, input.insert(key, Long.MAX_VALUE, Actionable.SIMULATE));
        assertTrue(input.isEmpty());
        assertEquals(capacity, input.insert(key, Long.MAX_VALUE, Actionable.MODULATE));
        assertEquals(0, input.insert(key, 1, Actionable.SIMULATE));

        var saved = input.write();
        assertEquals(1, saved.size());
        assertEquals(capacity, input.amount(key));
    }

    @Test
    void partialFlushRetainsRemainderAndWaitsForNextInterval() {
        var input = new BufferedInterfaceInput();
        var key = AEItemKey.of(Items.IRON_INGOT);
        input.insert(key, 10, Actionable.MODULATE);
        var changes = new AtomicInteger();
        var network = new MEStorage() {
            @Override
            public Component getDescription() {
                return Component.literal("test");
            }

            @Override
            public void getAvailableStacks(KeyCounter out) {
            }

            @Override
            public long insert(AEKey key, long amount, Actionable mode, IActionSource source) {
                return Math.min(4, amount);
            }
        };

        input.flush(network, IActionSource.empty(), 0, 0, changes::incrementAndGet);
        assertEquals(6, input.amount(key));
        assertEquals(1, changes.get());
        assertFalse(input.isFlushDue(1, 0));
        input.flush(network, IActionSource.empty(), 5, 0, changes::incrementAndGet);
        assertEquals(2, input.amount(key));
        assertEquals(2, changes.get());
    }
}

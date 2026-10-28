package com.moakiee.ae2lt.logic;

import com.moakiee.ae2lt.logic.transfer.OverloadedIOTransfer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class OverloadedIOTransferTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void capsWorkPerResourceAndPaysOnce() {
        var source = new Storage(30, 30);
        var destination = new Storage(0, 20);
        int[] payments = {0};
        var result = OverloadedIOTransfer.move(source, destination, AEItemKey.of(Items.STONE),
                IActionSource.empty(), () -> { payments[0]++; return true; }, 12);
        assertEquals(12, result.inserted());
        assertEquals(0, result.remainder());
        assertEquals(18, source.count);
        assertEquals(12, destination.count);
        assertEquals(1, payments[0]);
    }

    @Test
    void rejectsWithoutPaymentOrExtractionWhenDestinationIsFull() {
        var source = new Storage(20, 20);
        var destination = new Storage(5, 5);
        var result = OverloadedIOTransfer.move(source, destination, AEItemKey.of(Items.STONE),
                IActionSource.empty(), () -> { throw new AssertionError("No payment"); });
        assertEquals(0, result.inserted());
        assertEquals(20, source.count);
        assertFalse(result.powerBlocked());
    }

    @Test
    void insufficientPowerNeverMovesResources() {
        var source = new Storage(20, 20);
        var destination = new Storage(0, 20);
        var result = OverloadedIOTransfer.move(source, destination, AEItemKey.of(Items.STONE),
                IActionSource.empty(), () -> false);
        assertTrue(result.powerBlocked());
        assertEquals(20, source.count);
        assertEquals(0, destination.count);
    }

    @Test
    void failedInsertionIsRefundedToSource() {
        var source = new Storage(10, 10);
        var destination = new Storage(0, 10) {
            @Override
            public long insert(AEKey key, long amount, Actionable mode, IActionSource action) {
                return mode == Actionable.SIMULATE ? amount : 3;
            }
        };
        var result = OverloadedIOTransfer.move(source, destination, AEItemKey.of(Items.STONE),
                IActionSource.empty(), () -> true);
        assertEquals(3, result.inserted());
        assertEquals(0, result.remainder());
        assertEquals(7, source.count);
    }

    private static class Storage implements MEStorage {
        long count;
        final long capacity;

        Storage(long count, long capacity) {
            this.count = count;
            this.capacity = capacity;
        }

        @Override
        public Component getDescription() { return Component.literal("Test"); }

        @Override
        public void getAvailableStacks(KeyCounter out) { }

        @Override
        public long insert(AEKey key, long amount, Actionable mode, IActionSource action) {
            long accepted = Math.min(amount, capacity - count);
            if (mode == Actionable.MODULATE) count += accepted;
            return accepted;
        }

        @Override
        public long extract(AEKey key, long amount, Actionable mode, IActionSource action) {
            long extracted = Math.min(amount, count);
            if (mode == Actionable.MODULATE) count -= extracted;
            return extracted;
        }
    }
}

package com.moakiee.ae2lt.util;

import com.moakiee.ae2lt.test.MinecraftComponentsTestBase;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LegacyItemTransferTest extends MinecraftComponentsTestBase {
    @Test
    void repeatedViewsShareSlotCapacityAndReleaseAbortedReservations() {
        var source = new ItemStackHandler(1);
        var a = LegacyTransferBridge.items(source);
        var b = LegacyTransferBridge.items(source);
        var item = ItemResource.of(new ItemStack(Items.IRON_INGOT));
        try (var tx = Transaction.openRoot()) {
            assertEquals(50, a.insert(0, item, 50, tx));
            assertEquals(14, b.insert(0, item, 50, tx));
        }
        assertTrue(source.getStackInSlot(0).isEmpty());
        try (var tx = Transaction.openRoot()) {
            assertEquals(50, a.insert(0, item, 50, tx));
            assertEquals(14, b.insert(0, item, 50, tx));
            tx.commit();
        }
        assertEquals(64, source.getStackInSlot(0).getCount());
        try (var tx = Transaction.openRoot()) {
            assertEquals(50, a.extract(0, item, 50, tx));
            assertEquals(14, b.extract(0, item, 50, tx));
            tx.commit();
        }
        assertTrue(source.getStackInSlot(0).isEmpty());
    }

    @Test
    void reservationsRespectResourceAndCustomSlotLimits() {
        for (var stack : new ItemStack[] {new ItemStack(Items.ENDER_PEARL), new ItemStack(Items.IRON_INGOT)}) {
            var source = new ItemStackHandler(1) {
                @Override public int getSlotLimit(int slot) { return 32; }
            };
            int capacity = Math.min(32, stack.getMaxStackSize());
            var a = LegacyTransferBridge.items(source);
            var b = LegacyTransferBridge.items(source);
            var item = ItemResource.of(stack);
            try (var tx = Transaction.openRoot()) {
                assertEquals(10, a.insert(0, item, 10, tx));
                assertEquals(capacity - 10, b.insert(0, item, 64, tx));
                tx.commit();
            }
            assertEquals(capacity, source.getStackInSlot(0).getCount());
        }
    }
}

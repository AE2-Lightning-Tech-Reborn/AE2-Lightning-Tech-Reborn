package com.moakiee.ae2lt.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class WirelessExportStockBatchTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    @Test void repeatedTargetsShareExtractionAndRetainUnusedStock() {
        var supply = new Supply(10_000);
        var remainder = new AtomicLong();
        var key = AEItemKey.of(Items.STONE);
        try (var batch = new WirelessExportStockBatch(supply, 64, (k, amount) -> remainder.addAndGet(amount))) {
            for (int target = 0; target < 100; target++) {
                assertEquals(10, batch.extract(key, 10, Actionable.MODULATE, IActionSource.empty()));
            }
            assertEquals(2, supply.calls);
        }
        assertEquals(10_000, supply.items + 1000 + remainder.get());
        assertEquals(280, remainder.get());
    }

    @Test void simulationIsReadOnlyAndShortSupplyCannotCreateItems() {
        var supply = new Supply(7);
        var remainder = new AtomicLong();
        var key = AEItemKey.of(Items.STONE);
        try (var batch = new WirelessExportStockBatch(supply, 64, (k, amount) -> remainder.addAndGet(amount))) {
            assertEquals(7, batch.extract(key, 10, Actionable.SIMULATE, IActionSource.empty()));
            assertEquals(7, supply.items);
            assertEquals(7, batch.extract(key, 10, Actionable.MODULATE, IActionSource.empty()));
            assertEquals(0, batch.extract(key, 10, Actionable.MODULATE, IActionSource.empty()));
        }
        assertEquals(0, supply.items + remainder.get());
    }

    @Test void exceptionalTransferReturnsRemainderAndCloseIsIdempotent() {
        var supply = new Supply(100);
        var remainder = new AtomicLong();
        var batch = new WirelessExportStockBatch(supply, 64, (key, amount) -> remainder.addAndGet(amount));
        assertThrows(IllegalStateException.class, () -> {
            try (batch) {
                assertEquals(10, batch.extract(AEItemKey.of(Items.STONE), 10, Actionable.MODULATE, IActionSource.empty()));
                throw new IllegalStateException("target rejected after the first transfer");
            }
        });
        batch.close();
        assertEquals(90, remainder.get());
    }

    private static final class Supply implements MEStorage {
        long items;
        int calls;
        Supply(long items) { this.items = items; }
        @Override public net.minecraft.network.chat.Component getDescription() {
            return net.minecraft.network.chat.Component.empty();
        }
        @Override public long extract(AEKey key, long amount, Actionable mode, IActionSource source) {
            long extracted = Math.min(items, amount);
            if (mode == Actionable.MODULATE) { items -= extracted; calls++; }
            return extracted;
        }
    }
}

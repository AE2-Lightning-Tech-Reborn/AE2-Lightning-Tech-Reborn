package com.moakiee.ae2lt.logic;

import com.moakiee.ae2lt.logic.transfer.OverloadedIOTransfer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
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
        double[] charged = {0};
        var result = OverloadedIOTransfer.move(source, destination, AEItemKey.of(Items.STONE),
                IActionSource.empty(), (amount, mode, multiplier) -> {
                    if (mode == Actionable.MODULATE) { payments[0]++; charged[0] += amount; }
                    return amount;
                }, 12);
        assertEquals(12, result.inserted());
        assertEquals(0, result.remainder());
        assertEquals(18, source.count);
        assertEquals(12, destination.count);
        assertEquals(1, payments[0]);
        assertEquals(12, charged[0]);
    }

    @Test
    void rejectsWithoutPaymentOrExtractionWhenDestinationIsFull() {
        var source = new Storage(20, 20);
        var destination = new Storage(5, 5);
        var result = OverloadedIOTransfer.move(source, destination, AEItemKey.of(Items.STONE),
                IActionSource.empty(), (amount, mode, multiplier) -> { throw new AssertionError("No payment"); });
        assertEquals(0, result.inserted());
        assertEquals(20, source.count);
        assertFalse(result.powerBlocked());
    }

    @Test
    void insufficientPowerNeverMovesResources() {
        var source = new Storage(20, 20);
        var destination = new Storage(0, 20);
        var energy = new Energy(0);
        var result = OverloadedIOTransfer.move(source, destination, AEItemKey.of(Items.STONE),
                IActionSource.empty(), energy);
        assertTrue(result.powerBlocked());
        assertEquals(0, result.inserted());
        assertEquals(0, result.remainder());
        assertEquals(20, source.count);
        assertEquals(0, destination.count);
        assertEquals(0, source.extractions);
        assertEquals(0, destination.insertions);
        assertEquals(0, energy.payments);
        assertEquals(0, energy.charged);
    }

    @Test
    void partialPowerMovesOnlyWholePaidItemsAndChargesOnce() {
        var source = new Storage(20, 20);
        var destination = new Storage(0, 20);
        var energy = new Energy(7.5);
        var result = OverloadedIOTransfer.move(source, destination, AEItemKey.of(Items.STONE),
                IActionSource.empty(), energy);
        assertEquals(7, result.inserted());
        assertEquals(0, result.remainder());
        assertTrue(result.powerBlocked());
        assertEquals(13, source.count);
        assertEquals(7, destination.count);
        assertEquals(1, energy.payments);
        assertEquals(7, energy.charged);
        assertEquals(.5, energy.stored);
    }

    @Test
    void fluidPowerUsesNativeAmountPerOperation() {
        var source = new Storage(1000, 1000);
        var destination = new Storage(0, 1000);
        var energy = new Energy(.2);
        var result = OverloadedIOTransfer.move(source, destination, AEFluidKey.of(Fluids.WATER),
                IActionSource.empty(), energy);
        assertEquals(25, result.inserted());
        assertEquals(975, source.count);
        assertEquals(25, destination.count);
        assertEquals(1, energy.payments);
        assertEquals(.2, energy.charged, 1e-12);
        assertEquals(0, energy.stored, 1e-12);
    }

    @Test
    void fluidReceiptAtNativeBoundaryDoesNotLoseOnePaidUnit() {
        var source = new Storage(2000, 2000);
        var destination = new Storage(0, 2000);
        var energy = new Energy(100);
        energy.paymentLimit = 1001 / 125.0;
        var result = OverloadedIOTransfer.move(source, destination, AEFluidKey.of(Fluids.WATER),
                IActionSource.empty(), energy);
        assertTrue(result.powerBlocked());
        assertEquals(1001, result.inserted());
        assertEquals(999, source.count);
        assertEquals(1001, destination.count);
        assertEquals(0, result.remainder());
        assertEquals(1, energy.payments);
        assertEquals(1001 / 125.0, energy.charged, 1e-12);
    }

    @Test
    void powerBelowOneNativeUnitNeverReceivesARoundingAllowance() {
        var source = new Storage(1000, 1000);
        var destination = new Storage(0, 1000);
        var energy = new Energy(Math.nextDown(1 / 125.0));
        var result = OverloadedIOTransfer.move(source, destination, AEFluidKey.of(Fluids.WATER),
                IActionSource.empty(), energy);
        assertTrue(result.powerBlocked());
        assertEquals(0, result.inserted());
        assertEquals(1000, source.count);
        assertEquals(0, destination.count);
        assertEquals(0, energy.payments);
        assertEquals(0, energy.charged);
    }

    @Test
    void configuredMultiplierIsAppliedExactlyOnce() {
        double oldMultiplier = PowerMultiplier.CONFIG.multiplier;
        try {
            PowerMultiplier.CONFIG.multiplier = 2;
            var source = new Storage(20, 20);
            var destination = new Storage(0, 20);
            var energy = new Energy(15);
            var result = OverloadedIOTransfer.move(source, destination, AEItemKey.of(Items.STONE),
                    IActionSource.empty(), energy);
            assertEquals(7, result.inserted());
            assertEquals(13, source.count);
            assertEquals(7, destination.count);
            assertEquals(1, energy.payments);
            assertEquals(14, energy.charged);
            assertEquals(1, energy.stored);
        } finally {
            PowerMultiplier.CONFIG.multiplier = oldMultiplier;
        }
    }

    @Test
    void shorterRealExtractionIsChargedByItsReceipt() {
        var source = new Storage(20, 20) {
            @Override
            public long extract(AEKey key, long amount, Actionable mode, IActionSource action) {
                return super.extract(key, mode == Actionable.MODULATE ? Math.min(amount, 4) : amount, mode, action);
            }
        };
        var destination = new Storage(0, 20);
        var energy = new Energy(100);
        var result = OverloadedIOTransfer.move(source, destination, AEItemKey.of(Items.STONE),
                IActionSource.empty(), energy);
        assertEquals(4, result.inserted());
        assertEquals(16, source.count);
        assertEquals(4, destination.count);
        assertEquals(1, energy.payments);
        assertEquals(4, energy.charged);
        assertEquals(96, energy.stored);
    }

    @Test
    void shorterRealEnergyReceiptNeverInsertsUnpaidResources() {
        var source = new Storage(20, 20);
        var destination = new Storage(0, 20);
        var energy = new Energy(20);
        energy.paymentLimit = 3;
        energy.onPayment = () -> {
            assertEquals(0, source.count, "Charge only the actual extraction receipt");
            assertEquals(0, destination.count, "Payment must precede destination insertion");
        };
        var result = OverloadedIOTransfer.move(source, destination, AEItemKey.of(Items.STONE),
                IActionSource.empty(), energy);
        assertTrue(result.powerBlocked());
        assertEquals(3, result.inserted());
        assertEquals(0, result.remainder());
        assertEquals(17, source.count);
        assertEquals(3, destination.count);
        assertEquals(1, energy.payments);
        assertEquals(3, energy.charged);
        assertEquals(17, energy.stored);
    }

    @Test
    void exhaustedRealEnergyReceiptRefundsAllExtractedResources() {
        var source = new Storage(20, 20);
        var destination = new Storage(0, 20);
        var energy = new Energy(20);
        energy.paymentLimit = 0;
        var result = OverloadedIOTransfer.move(source, destination, AEItemKey.of(Items.STONE),
                IActionSource.empty(), energy);
        assertTrue(result.powerBlocked());
        assertEquals(0, result.inserted());
        assertEquals(0, result.remainder());
        assertEquals(20, source.count);
        assertEquals(0, destination.count);
        assertEquals(0, destination.insertions);
        assertEquals(1, energy.payments);
        assertEquals(0, energy.charged);
    }

    @Test
    void refusedRefundKeepsUnpaidResourcesInTheReturnedRemainder() {
        var source = new Storage(20, 20) {
            @Override
            public long insert(AEKey key, long amount, Actionable mode, IActionSource action) { return 0; }
        };
        var destination = new Storage(0, 20);
        var energy = new Energy(20);
        energy.paymentLimit = 3;
        var result = OverloadedIOTransfer.move(source, destination, AEItemKey.of(Items.STONE),
                IActionSource.empty(), energy);
        assertTrue(result.powerBlocked());
        assertEquals(3, result.inserted());
        assertEquals(17, result.remainder());
        assertEquals(0, source.count);
        assertEquals(20, source.count + destination.count + result.remainder());
        assertEquals(1, energy.payments);
        assertEquals(3, energy.charged);
    }

    @Test
    void failedInsertionIsRefundedToSource() {
        var source = new Storage(10, 10);
        var destination = new Storage(0, 10) {
            @Override
            public long insert(AEKey key, long amount, Actionable mode, IActionSource action) {
                return super.insert(key, mode == Actionable.MODULATE ? Math.min(amount, 3) : amount, mode, action);
            }
        };
        var energy = new Energy(100);
        var result = OverloadedIOTransfer.move(source, destination, AEItemKey.of(Items.STONE),
                IActionSource.empty(), energy);
        assertEquals(3, result.inserted());
        assertEquals(0, result.remainder());
        assertEquals(7, source.count);
        assertEquals(3, destination.count);
        // The generic energy API cannot refund a paid attempt after a storage callback rejects it.
        assertEquals(1, energy.payments);
        assertEquals(10, energy.charged);
        assertEquals(90, energy.stored);
    }

    private static final class Energy implements IEnergySource {
        double stored;
        double charged;
        double paymentLimit = Double.POSITIVE_INFINITY;
        Runnable onPayment = () -> {};
        int payments;

        Energy(double stored) { this.stored = stored; }

        @Override
        public double extractAEPower(double amount, Actionable mode, PowerMultiplier multiplier) {
            double received = Math.min(amount, multiplier.divide(stored));
            if (mode == Actionable.MODULATE) {
                onPayment.run();
                received = Math.min(received, paymentLimit);
                double debit = multiplier.multiply(received);
                stored -= debit;
                charged += debit;
                payments++;
            }
            return received;
        }
    }

    private static class Storage implements MEStorage {
        long count;
        int insertions;
        int extractions;
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
            if (mode == Actionable.MODULATE) { count += accepted; insertions++; }
            return accepted;
        }

        @Override
        public long extract(AEKey key, long amount, Actionable mode, IActionSource action) {
            long extracted = Math.min(amount, count);
            if (mode == Actionable.MODULATE) { count -= extracted; extractions++; }
            return extracted;
        }
    }
}

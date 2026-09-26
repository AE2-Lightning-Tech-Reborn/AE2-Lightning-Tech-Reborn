package com.moakiee.ae2lt.util;

import com.moakiee.ae2lt.util.LegacyTransferBridge;
import com.moakiee.ae2lt.machine.overloadfactory.OverloadProcessingFactoryEnergyStorage;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Uses the production bridge and NeoForge transaction implementation. */
public class LegacyEnergyTransferTest {
    @Test
    void longCapacityMachineMustRemainChargeableAboveIntMax() {
        var actual = new OverloadProcessingFactoryEnergyStorage(4_000_000_000L, () -> {});
        actual.loadStoredEnergy(Integer.MAX_VALUE);
        var view = LegacyTransferBridge.energy(actual);
        int accepted;
        try (var tx = Transaction.openRoot()) {
            accepted = view.insert(100, tx);
            tx.commit();
        }
        System.out.println("REVIEW_ENERGY_LONG accepted=" + accepted + " reportedCapacity=" + view.getCapacityAsLong()
                + " realCapacity=" + actual.getCapacityLong());
        assertEquals(100, accepted, "configured long capacity must remain usable above Integer.MAX_VALUE");
    }
    @Test
    void twoCapabilityViewsMustNotAcknowledgeTheSameFreeCapacity() {
        var actual = new OverloadProcessingFactoryEnergyStorage(200, () -> {});
        actual.receiveEnergy(100, false);
        var north = LegacyTransferBridge.energy(actual);
        var south = LegacyTransferBridge.energy(actual);
        int accepted;
        try (var tx = Transaction.openRoot()) {
            accepted = north.insert(100, tx) + south.insert(100, tx);
            tx.commit();
        }
        int storedDelta = actual.getEnergyStored() - 100;
        System.out.println("REVIEW_ENERGY_INSERT acknowledged=" + accepted + " actualDelta=" + storedDelta);
        assertEquals(accepted, storedDelta, "acknowledged insertion must equal actual energy gained");
    }

    @Test
    void twoCapabilityViewsMustNotExtractTheSameStoredEnergy() {
        var actual = new EnergyStorage(200, 200, 200, 100);
        var north = LegacyTransferBridge.energy(actual);
        var south = LegacyTransferBridge.energy(actual);
        int extracted;
        try (var tx = Transaction.openRoot()) {
            extracted = north.extract(100, tx) + south.extract(100, tx);
            tx.commit();
        }
        int removed = 100 - actual.getEnergyStored();
        System.out.println("REVIEW_ENERGY_EXTRACT acknowledged=" + extracted + " actualDelta=" + removed);
        assertEquals(extracted, removed, "acknowledged extraction must equal actual energy removed");
    }

    @Test
    void abortedAndNestedReservationsReleaseCapacityForOtherViews() {
        var actual = new OverloadProcessingFactoryEnergyStorage(200, () -> {});
        var a = LegacyTransferBridge.energy(actual);
        var b = LegacyTransferBridge.energy(actual);
        try (var root = Transaction.openRoot()) {
            assertEquals(50, a.insert(50, root));
            try (var child = Transaction.open(root)) {
                assertEquals(150, b.insert(200, child));
                assertEquals(0, a.insert(1, child));
            }
            assertEquals(50, b.getAmountAsLong());
            assertEquals(150, b.insert(200, root));
            // Abort the root too: neither reservation reached the actual machine.
        }
        assertEquals(0, actual.getStoredEnergyLong());
        try (var root = Transaction.openRoot()) {
            assertEquals(200, a.insert(200, root));
            root.commit();
        }
        assertEquals(200, actual.getStoredEnergyLong());
    }

    @Test
    void commitCanTransferMoreThanIntMaxInOneRootTransaction() {
        var actual = new OverloadProcessingFactoryEnergyStorage(5_000_000_000L, () -> {});
        var view = LegacyTransferBridge.energy(actual);
        try (var tx = Transaction.openRoot()) {
            assertEquals(Integer.MAX_VALUE, view.insert(Integer.MAX_VALUE, tx));
            assertEquals(Integer.MAX_VALUE, view.insert(Integer.MAX_VALUE, tx));
            tx.commit();
        }
        assertEquals(2L * Integer.MAX_VALUE, actual.getStoredEnergyLong());
        assertEquals(5_000_000_000L, view.getCapacityAsLong());
    }

    @Test
    void chargingNearLongMaxDoesNotOverflow() {
        var actual = new OverloadProcessingFactoryEnergyStorage(Long.MAX_VALUE, () -> {});
        actual.loadStoredEnergy(Long.MAX_VALUE - 5);
        var view = LegacyTransferBridge.energy(actual);
        try (var tx = Transaction.openRoot()) {
            assertEquals(5, view.insert(100, tx));
            assertEquals(0, view.insert(100, tx));
            tx.commit();
        }
        assertEquals(Long.MAX_VALUE, view.getAmountAsLong());
    }
}

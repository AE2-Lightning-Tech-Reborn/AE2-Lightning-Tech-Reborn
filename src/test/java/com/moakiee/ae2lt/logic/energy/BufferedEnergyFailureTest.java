package com.moakiee.ae2lt.logic.energy;

import static org.junit.jupiter.api.Assertions.*;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import com.moakiee.ae2lt.me.key.LightningKey;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

/** Explicit-key buffer lifecycle tests need no AppFlux runtime or fake AppFlux API. */
class BufferedEnergyFailureTest {
    private static final AEKey KEY = LightningKey.HIGH_VOLTAGE;
    private static final IActionSource SOURCE = IActionSource.empty();

    @Test
    void offlineBufferRejectsNewNonDroppableCreditsButCanDrainOwnedFunds() {
        var store = new Store(0);
        var buffer = new BufferedMEStorage(store, null, null, null, () -> false);
        for (var mode : Actionable.values()) assertEquals(0, buffer.insert(KEY, 100, mode, SOURCE));
        assertEquals(0, store.energy);
        assertFalse(buffer.beginTransfer());
        assertEquals(0, buffer.beginMemoryBatch(KEY, 100, SOURCE));
        buffer.restorePendingEnergy(100);
        assertEquals(100, buffer.getPendingEnergy());
        buffer.endTick(KEY, SOURCE);
        assertEquals(100, store.energy);
        assertEquals(0, buffer.getPendingEnergy());
    }

    @Test
    void capacitySimulationDisconnectStopsNetworkRefill() {
        boolean[] online = {true};
        var store = new Store(1000);
        var cell = new Store(0) {
            @Override
            public long insert(AEKey key, long n, Actionable mode, IActionSource source) {
                if (mode == Actionable.SIMULATE) online[0] = false;
                return super.insert(key, n, mode, source);
            }
        };
        var buffer = new BufferedMEStorage(store, () -> cell, null, null, () -> online[0]);
        assertEquals(0, buffer.beginMemoryBatch(KEY, 100, SOURCE));
        assertEquals(1000, store.energy);
        assertEquals(0, store.extracts);
        assertEquals(0, cell.energy);
        assertEquals(0, buffer.getPendingEnergy());
        buffer.endBatch(KEY, SOURCE);
    }

    @Test
    void confirmedRefundRemainsOwnedWhenHostGoesOffline() {
        boolean[] online = {false};
        var store = new Store(0);
        var buffer = new BufferedMEStorage(store, null, null, null, () -> online[0]);

        assertEquals(100, buffer.returnUndelivered(100, SOURCE));
        assertEquals(100, buffer.getPendingEnergy());
        assertEquals(0, store.energy);

        online[0] = true;
        buffer.endTick(KEY, SOURCE);
        assertEquals(100, store.energy);
        assertEquals(0, buffer.getPendingEnergy());
    }

    @Test
    void negativeOrOverreportedRefundIsNeverReplayed() {
        for (long receipt : new long[] {-1, 101, Long.MAX_VALUE}) {
            int[] writes = {0};
            var store = new Store(0) {
                public long insert(AEKey key, long n, Actionable mode, IActionSource source) {
                    long accepted = super.insert(key, n, mode, source);
                    if (mode == Actionable.MODULATE) { writes[0]++; return receipt; }
                    return accepted;
                }
            };
            var buffer = new BufferedMEStorage(store);
            buffer.restorePendingEnergy(100);
            for (int i = 0; i < 10_000; i++) buffer.flushAll(KEY, SOURCE);
            assertEquals(100, store.energy);
            assertEquals(0, buffer.getPendingEnergy());
            assertEquals(1, writes[0]);
        }
    }

    @Test
    void malformedBatchWithdrawalDoesNotMintOwnedBudget() {
        var store = new Store(1000) {
            public long extract(AEKey key, long n, Actionable mode, IActionSource source) {
                super.extract(key, n, mode, source);
                return Long.MAX_VALUE;
            }
        };
        var buffer = new BufferedMEStorage(store);
        assertEquals(0, buffer.beginMemoryBatch(KEY, 100, SOURCE));
        assertEquals(0, buffer.getPendingEnergy());
        assertEquals(900, store.energy);
    }

    @Test
    void rejectedNestedTransferDoesNotUnlockTheOuterTransaction() {
        var buffer = new BufferedMEStorage(new Store(0));
        assertTrue(buffer.beginTransfer());
        assertFalse(buffer.beginTransfer());
        assertFalse(buffer.beginTransfer());
        buffer.endTransfer();
        assertTrue(buffer.beginTransfer());
        buffer.endTransfer();
    }

    @Test
    void transferGuardIsLocalToEachBuffer() {
        var first = new BufferedMEStorage(new Store(0));
        var second = new BufferedMEStorage(new Store(0));
        assertTrue(first.beginTransfer());
        assertTrue(second.beginTransfer());
        first.endTransfer();
        second.endTransfer();
    }

    @Test
    void pendingPersistNotifiesOnChangeButNotOnEveryRejectedRetry() {
        var store = new Store(100);
        int[] notifications = {0};
        var buffer = new BufferedMEStorage(store, null, null, () -> notifications[0]++);
        buffer.beginMemoryBatch(KEY, 100, SOURCE);
        store.capacity = 0;
        buffer.endBatch(KEY, SOURCE);
        for (int i = 0; i < 1000; i++) buffer.endTick(KEY, SOURCE);
        assertEquals(1, notifications[0]);
        store.capacity = 100;
        buffer.endTick(KEY, SOURCE);
        assertEquals(2, notifications[0]);
        assertEquals(0, buffer.getPendingEnergy());
    }

    @Test
    void restoredPendingBudgetIsUsedWithoutSecondNetworkWithdrawal() {
        var store = new Store(0);
        var buffer = new BufferedMEStorage(store);
        buffer.restorePendingEnergy(100);
        assertEquals(100, buffer.beginMemoryBatch(KEY, 100, SOURCE));
        assertEquals(0, store.extracts);
        buffer.endBatch(KEY, SOURCE);
        assertEquals(100, store.energy);
        buffer.restorePendingEnergy(-1);
        assertEquals(0, buffer.getPendingEnergy());
    }

    @Test
    void distributorPersistenceIsSnapshotOnlyAndLoadIsNotAdditive() throws Exception {
        var distributor = new WirelessEnergyDistributor(new PersistenceHost());
        var store = new Store(100);
        var buffer = new BufferedMEStorage(store);
        buffer.beginMemoryBatch(KEY, 100, SOURCE);
        var field = WirelessEnergyDistributor.class.getDeclaredField("bufferedStorage");
        field.setAccessible(true);
        field.set(distributor, buffer);
        var tag = new net.minecraft.nbt.CompoundTag();
        for (int i = 0; i < 1000; i++) distributor.writeToNBT(tag);
        assertEquals(100, buffer.getPendingEnergy());
        assertEquals(0, store.energy);
        var restored = new WirelessEnergyDistributor(new PersistenceHost());
        for (int i = 0; i < 1000; i++) restored.readFromNBT(tag);
        assertEquals(100, restored.getBufferedEnergy());
        restored.getBufferedStorage().rebindDelegate(store);
        restored.getBufferedStorage().endBatch(KEY, SOURCE);
        assertEquals(100, store.energy);
        restored.writeToNBT(tag);
        assertFalse(tag.contains("ae2lt:pending_wireless_fe"));
    }

    @Test
    void invalidPersistedPendingValuesDoNotCreateEnergy() {
        var distributor = new WirelessEnergyDistributor(new PersistenceHost());
        var tag = new net.minecraft.nbt.CompoundTag();
        tag.putLong("ae2lt:pending_wireless_fe", 100);
        distributor.readFromNBT(tag);
        assertEquals(100, distributor.getBufferedEnergy());
        tag.putLong("ae2lt:pending_wireless_fe", -1);
        distributor.readFromNBT(tag);
        assertEquals(0, distributor.getBufferedEnergy());
        tag.putString("ae2lt:pending_wireless_fe", "100");
        distributor.readFromNBT(tag);
        assertEquals(0, distributor.getBufferedEnergy());
        tag.remove("ae2lt:pending_wireless_fe");
        distributor.readFromNBT(tag);
        assertEquals(0, distributor.getBufferedEnergy());
    }

    @Test
    void replacingSnapshotCancelsPreviousDismantleHandoff() {
        for (boolean dismantleImport : new boolean[] {false, true}) {
            var distributor = new WirelessEnergyDistributor(new PersistenceHost());
            var snapshot = new net.minecraft.nbt.CompoundTag();
            snapshot.putLong("ae2lt:pending_wireless_fe", 100);
            distributor.readFromNBT(snapshot);
            distributor.writeDismantleEnergyToNBT(new net.minecraft.nbt.CompoundTag());
            assertTrue(distributor.hasDismantleDropHandoff());

            snapshot.putLong("ae2lt:pending_wireless_fe", 200);
            if (dismantleImport) distributor.importDismantleEnergyFromNBT(snapshot);
            else distributor.readFromNBT(snapshot);
            distributor.consumeDismantleDropHandoff();

            assertEquals(200, distributor.getBufferedEnergy());
            assertFalse(distributor.hasDismantleDropHandoff());
        }
    }

    @Test
    void dismantleImportReplacesOldFundsEvenWhenSnapshotHasNoValidEnergy() {
        var distributor = new WirelessEnergyDistributor(new PersistenceHost());
        for (var invalid : new net.minecraft.nbt.CompoundTag[] {
                new net.minecraft.nbt.CompoundTag(), invalidPendingTag()}) {
            var valid = new net.minecraft.nbt.CompoundTag();
            valid.putLong("ae2lt:pending_wireless_fe", 100);
            distributor.importDismantleEnergyFromNBT(valid);
            assertEquals(100, distributor.getBufferedEnergy());
            distributor.importDismantleEnergyFromNBT(invalid);
            assertEquals(0, distributor.getBufferedEnergy());
        }
    }

    private static net.minecraft.nbt.CompoundTag invalidPendingTag() {
        var tag = new net.minecraft.nbt.CompoundTag();
        tag.putString("ae2lt:pending_wireless_fe", "100");
        return tag;
    }

    static class PersistenceHost implements WirelessEnergyDistributor.Host {
        public appeng.api.networking.IManagedGridNode getMainNode() { return null; }
        public IActionSource actionSource() { return SOURCE; }
        public boolean isHostRemoved() { return false; }
        public java.util.List<WirelessEnergyAPI.Target> getValidTargets() { return java.util.List.of(); }
        public int getValidTargetsVersion() { return 0; }
    }

    @Test
    void rejectedFlushKeepsOwnedBudgetAcrossManyLifecycleCalls() {
        var store = new Store(100);
        var buffer = new BufferedMEStorage(store);
        assertEquals(100, buffer.beginMemoryBatch(KEY, 100, SOURCE));
        store.capacity = 0;
        for (int i = 0; i < 10_000; i++) {
            assertEquals(0, buffer.flushAll(KEY, SOURCE));
            assertEquals(100, buffer.getBufferedEnergy());
        }
        store.capacity = 100;
        assertEquals(100, buffer.flushAll(KEY, SOURCE));
        assertEquals(100, store.energy);
        assertEquals(0, buffer.getBufferedEnergy());
    }

    @Test
    void knownPartialCreditIsCommittedBeforeSecondInsertThrows() {
        var network = new Store(0);
        var cell = new Store(100);
        var buffer = new BufferedMEStorage(network, () -> cell);
        assertEquals(100, buffer.beginMemoryBatch(KEY, 100, SOURCE));
        cell.capacity = 40;
        network.failInsert = true;
        assertDoesNotThrow(() -> buffer.endBatch(KEY, SOURCE));
        assertEquals(40, cell.energy);
        network.failInsert = false;
        assertEquals(0, buffer.endTick(KEY, SOURCE));
        assertEquals(100, cell.energy + network.energy);
    }

    @Test
    void partialPullRemainsOwnedWhenRefillThrows() {
        var cell = new Store(40);
        var network = new Store(60);
        network.failExtract = true;
        var buffer = new BufferedMEStorage(network, () -> cell);
        assertThrows(IllegalStateException.class, () -> buffer.beginMemoryBatch(KEY, 100, SOURCE));
        network.failExtract = false;
        buffer.endBatch(KEY, SOURCE);
        assertEquals(100, cell.energy + network.energy);
    }

    @Test
    void gridRebindRetainsRejectedRefundWithoutExtractingItAgain() {
        var oldStore = new Store(100);
        var buffer = new BufferedMEStorage(oldStore);
        buffer.beginMemoryBatch(KEY, 100, SOURCE);
        oldStore.capacity = 0;
        buffer.flushAll(KEY, SOURCE);
        var replacement = new Store(0);
        buffer.rebindDelegate(replacement);
        assertEquals(100, buffer.beginMemoryBatch(KEY, 100, SOURCE));
        assertEquals(0, replacement.extracts);
        buffer.endBatch(KEY, SOURCE);
        assertEquals(100, replacement.energy);
        assertEquals(0, oldStore.energy);
    }

    static class Store implements MEStorage {
        long energy, capacity = Long.MAX_VALUE;
        int extracts;
        boolean failInsert, failExtract;
        Store(long energy) { this.energy = energy; }
        public long extract(AEKey key, long n, Actionable mode, IActionSource source) {
            if (failExtract && mode == Actionable.MODULATE) throw new IllegalStateException("extract failure");
            if (mode == Actionable.MODULATE) extracts++;
            long taken = Math.max(0, Math.min(n, energy));
            if (mode == Actionable.MODULATE) energy -= taken;
            return taken;
        }
        public long insert(AEKey key, long n, Actionable mode, IActionSource source) {
            long accepted = Math.max(0, Math.min(n, capacity - energy));
            if (mode == Actionable.MODULATE) energy += accepted;
            if (failInsert && mode == Actionable.MODULATE) throw new IllegalStateException("insert failure AFTER credit");
            return accepted;
        }
        public Component getDescription() { return Component.literal("buffer test"); }
    }
}

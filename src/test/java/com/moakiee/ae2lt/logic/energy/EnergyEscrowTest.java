package com.moakiee.ae2lt.logic.energy;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class EnergyEscrowTest {
    @Test void partialReceiptAndRefusedRefundAreConserved() {
        var dirty = new AtomicInteger();
        var e = new EnergyEscrow(100, 0, dirty::incrementAndGet);
        long offer = e.debit(80); e.quarantine(offer); e.settle(offer, 23);
        assertEquals(77, e.available()); assertEquals(0, e.uncertain());
        offer = e.debit(77); e.quarantine(offer); e.settle(offer, 0);
        assertEquals(77, e.available()); assertTrue(dirty.get() > 0);
    }
    @Test void missingReceiptIsNotSpendableAndCannotSettleTwice() {
        var e = new EnergyEscrow(90, 0, () -> {});
        e.quarantine(e.debit(60));
        assertEquals(30, e.debit(100)); assertEquals(60, e.uncertain());
        e.settle(60, 40); assertEquals(20, e.available());
        assertThrows(IllegalArgumentException.class, () -> e.settle(60, 40));
    }
    @Test void rejectsOverflowAndNegativeBalance() {
        var e = new EnergyEscrow(Long.MAX_VALUE - 1, 1, () -> {});
        assertEquals(0, e.room());
        assertThrows(IllegalArgumentException.class, () -> e.credit(1));
        assertThrows(IllegalArgumentException.class, () -> new EnergyEscrow(-1, 0, () -> {}));
        assertThrows(IllegalArgumentException.class, () -> new EnergyEscrow(Long.MAX_VALUE, 1, () -> {}));
    }
    @Test void ledgerRoundTripKeepsOneCanonicalAccountAndUncertainBalance() {
        var l = new WirelessEnergyLedger(); var id = UUID.randomUUID();
        l.account(id).credit(41); l.account(id).quarantine(3);
        var restored = WirelessEnergyLedger.load(l.save(new CompoundTag()));
        assertSame(restored.account(id), restored.account(id));
        assertEquals(41, restored.account(id).available()); assertEquals(3, restored.account(id).uncertain());
        restored.account(id).debit(20);
        var again = WirelessEnergyLedger.load(restored.save(new CompoundTag()));
        assertEquals(21, again.account(id).available());
        assertEquals(0, again.account(UUID.randomUUID()).available());
    }
    @Test void deterministicReceiptsPreserveEveryFe() {
        var random = new Random(0xFE20261013L);
        var e = new EnergyEscrow(0, 0, () -> {});
        long extracted = 0, delivered = 0;
        for (int i = 0; i < 10000; i++) {
            long add = random.nextInt(100); e.credit(add); extracted += add;
            long offered = e.debit(random.nextInt(150)); e.quarantine(offered);
            long accepted = offered == 0 ? 0 : random.nextLong(offered + 1);
            e.settle(offered, accepted); delivered += accepted;
            assertEquals(extracted, delivered + e.available() + e.uncertain());
        }
    }
    @Test void networkBudgetSharedBoundedAndResetsOncePerTick() {
        var budget = new WirelessEnergyGridService();
        assertEquals(60, budget.reserve(7, 60, 100, 2));
        assertEquals(40, budget.reserve(7, Long.MAX_VALUE, 100, 2));
        assertEquals(0, budget.reserve(7, 1, 100, 2));
        assertEquals(100, budget.reserve(8, 100, 100, 2));
        assertEquals(0, budget.reserve(8, 100, 100, 2));
    }
    @Test void migratingLegacyRefundToLedgerDoesNotCopyOrReplayUncertainReceipts() {
        var store = new BufferedEnergyFailureTest.Store(0);
        var buffer = new BufferedMEStorage(store);
        buffer.restoreSnapshot(70, 30);
        var ledger = new WirelessEnergyLedger();
        var id = UUID.randomUUID();
        buffer.attachLedger(ledger.account(id));
        buffer.attachLedger(ledger.account(id));
        buffer.restorePendingEnergy(100);
        buffer.endTick(com.moakiee.ae2lt.me.key.LightningKey.HIGH_VOLTAGE, appeng.api.networking.security.IActionSource.empty());
        assertEquals(70, store.energy);
        assertEquals(0, ledger.account(id).available());
        assertEquals(30, WirelessEnergyLedger.load(ledger.save(new CompoundTag())).account(id).uncertain());
    }

    @Test void ownershipMovesBeforeEitherPersistenceCallbackCanReenter() {
        EnergyEscrow[] accounts = new EnergyEscrow[2];
        accounts[0] = new EnergyEscrow(70, 30, () -> {
            assertEquals(0, accounts[0].available());
            assertEquals(0, accounts[0].uncertain());
            assertEquals(70, accounts[1].available());
            assertEquals(30, accounts[1].uncertain());
        });
        accounts[1] = new EnergyEscrow(0, 0, () -> {});
        accounts[0].transferTo(accounts[1]);
        accounts[0].transferTo(accounts[1]);
        assertEquals(70, accounts[1].available());
        assertEquals(30, accounts[1].uncertain());
    }
}

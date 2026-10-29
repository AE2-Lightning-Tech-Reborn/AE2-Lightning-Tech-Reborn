package com.moakiee.ae2lt.logic.energy;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class AeEnergyBalanceTest {
    @Test void fractionalConversionDoesNotRoundSmallRequestsIntoFreePower() {
        var fe = new EnergyEscrow(1, 0, () -> {});
        var ae = new AeEnergyBalance("0", () -> {});
        ae.convert(fe, 1, .5);
        for (int i = 0; i < 5; i++) assertEquals(.1, ae.extract(.1));
        assertEquals(0, ae.available()); assertEquals(0, fe.available());
        assertEquals(0, ae.extract(.1));
    }
    @Test void simulationChangesNeitherBalanceNorDirtyState() {
        var dirty = new AtomicInteger();
        var ae = new AeEnergyBalance("0.125", dirty::incrementAndGet);
        assertEquals(2, ae.neededFe(1, .5));
        assertEquals(1, ae.simulate(1, 2, .5));
        assertEquals("0.125", ae.saved()); assertEquals(0, dirty.get());
    }
    @Test void rateChangesDoNotRevalueAlreadyConvertedEnergy() {
        var fe = new EnergyEscrow(2, 0, () -> {});
        var ae = new AeEnergyBalance("0", () -> {});
        ae.convert(fe, 1, .5); assertEquals(.3, ae.extract(.3));
        ae.convert(fe, 1, .25);
        assertEquals(.45, ae.extract(1)); assertEquals(0, ae.available());
    }
    @Test void unresolvedWithdrawalIsPersistentButNotSpendable() {
        var ledger = new WirelessEnergyLedger(); var id = UUID.randomUUID();
        var ae = ledger.aeAccount(id);
        ae.beginWithdrawal(100);
        var restored = WirelessEnergyLedger.load(ledger.save(new CompoundTag()));
        assertEquals(100, restored.aeAccount(id).pendingFeWithdrawal());
        assertEquals(0, restored.account(id).available()); assertEquals(0, restored.aeAccount(id).available());
        assertThrows(IllegalStateException.class, () -> restored.aeAccount(id).beginWithdrawal(100));
    }
    @Test void actualReceiptIsOwnedBeforeConversionAndSurvivesRestart() {
        var ledger = new WirelessEnergyLedger(); var id = UUID.randomUUID();
        var ae = ledger.aeAccount(id); var fe = ledger.account(id);
        ae.beginWithdrawal(100); ae.completeWithdrawal(fe, 7);
        ae.convert(fe, 3, .5); assertEquals(.1, ae.extract(.1));
        var restored = WirelessEnergyLedger.load(ledger.save(new CompoundTag()));
        assertEquals(4, restored.account(id).available());
        assertEquals("1.4", restored.aeAccount(id).saved());
        assertEquals(0, restored.aeAccount(id).pendingFeWithdrawal());
        assertSame(restored.aeAccount(id), restored.aeAccount(id));
    }
    @Test void invalidRateAndUnownedConversionCannotCreateEnergy() {
        var fe = new EnergyEscrow(1, 0, () -> {}); var ae = new AeEnergyBalance("0", () -> {});
        for (double rate : new double[] {0, -1, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> ae.convert(fe, 1, rate));
        }
        assertThrows(IllegalArgumentException.class, () -> ae.convert(fe, 2, .5));
        assertEquals(1, fe.available()); assertEquals(0, ae.available());
    }
    @Test void randomizedDecimalPaymentsConserveConvertedEnergyExactly() {
        var random = new Random(20261013);
        var fe = new EnergyEscrow(20000, 0, () -> {}); var ae = new AeEnergyBalance("0", () -> {});
        BigDecimal purchased = BigDecimal.ZERO, delivered = BigDecimal.ZERO;
        for (int i = 0; i < 10000; i++) {
            double rate = (1 + random.nextInt(100)) / 100.0;
            ae.convert(fe, 1, rate); purchased = purchased.add(BigDecimal.valueOf(rate));
            delivered = delivered.add(BigDecimal.valueOf(ae.extract((1 + random.nextInt(100)) / 100.0)));
            assertEquals(0, purchased.compareTo(delivered.add(new BigDecimal(ae.saved()))));
        }
    }
    @Test void budgetPreviewCannotConsumeOrDuplicateNextTickAllowance() {
        var s = new WirelessEnergyGridService();
        assertEquals(100, s.preview(1, 1000, 100, 2));
        assertEquals(100, s.preview(1, 1000, 100, 2));
        assertEquals(40, s.reserve(1, 40, 100, 2));
        assertEquals(60, s.preview(1, 1000, 100, 2));
        assertEquals(100, s.preview(2, 1000, 100, 2));
        assertEquals(60, s.reserve(1, 1000, 100, 2));
        assertEquals(0, s.preview(1, 1000, 100, 2));
    }
}

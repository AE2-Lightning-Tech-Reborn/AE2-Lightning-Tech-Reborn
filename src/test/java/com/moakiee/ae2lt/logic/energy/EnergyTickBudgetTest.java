package com.moakiee.ae2lt.logic.energy;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class EnergyTickBudgetTest {
    @Test void ordinaryWorkLeavesBothFeAndVisitForMaintenance() {
        var b = new EnergyTickBudget(1);
        assertEquals(1, b.reserve(1, 100, 3, 20, 1));
        assertEquals(1, b.reserve(1, 100, 3, 20, 1));
        assertEquals(0, b.reserve(1, 100, 3, 20, 1));
        assertEquals(98, b.reserve(1000, 100, 3, 0, 0));
        assertEquals(0, b.reserve(1, 100, 3, 0, 0));
    }
    @Test void ordinaryWorkCannotSpendMaintenanceFeEvenWithVisitsLeft() {
        var b = new EnergyTickBudget(1);
        assertEquals(80, b.reserve(Long.MAX_VALUE, 100, 32, 20, 1));
        assertEquals(0, b.reserve(1, 100, 32, 20, 1));
        assertEquals(20, b.reserve(20, 100, 32, 0, 0));
    }
    @Test void splitAndRepeatedMergeShareOneRemainingAllowance() {
        var old = new EnergyTickBudget(7);
        assertEquals(60, old.reserve(60, 100, 10, 0, 0));
        var child = new EnergyTickBudget(7); child.merge(old);
        assertEquals(20, child.reserve(20, 100, 10, 0, 0));
        old.merge(child); old.merge(child);
        assertEquals(20, old.reserve(100, 100, 10, 0, 0));
        assertEquals(0, child.reserve(1, 100, 10, 0, 0));
    }
    @Test void independentlySpentMergedGridsCannotResetBudget() {
        var a = new EnergyTickBudget(7); var b = new EnergyTickBudget(7);
        assertEquals(70, a.reserve(70, 100, 10, 0, 0));
        assertEquals(60, b.reserve(60, 100, 10, 0, 0));
        a.merge(b);
        assertEquals(0, a.preview(100, 100, 10, 0, 0));
        assertEquals(0, b.reserve(100, 100, 10, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> a.merge(new EnergyTickBudget(8)));
    }
    @Test void handoffAliasesExpireAtNextTickAndAreNotRestoredFromWorldSave() {
        var ledger = new WirelessEnergyLedger(); var b = new EnergyTickBudget(7);
        ledger.registerBudget(b);
        assertSame(b, ledger.transferredBudget(7, b.id));
        var restored = WirelessEnergyLedger.load(ledger.save(new net.minecraft.nbt.CompoundTag()));
        assertNull(restored.transferredBudget(7, b.id));
        assertNull(ledger.transferredBudget(8, b.id));
    }
}

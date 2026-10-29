package com.moakiee.ae2lt.logic.provider;

import static org.junit.jupiter.api.Assertions.*;

import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.GenericStack;
import com.moakiee.ae2lt.me.key.LightningKey;

class UnlimitedReturnInventoryStressTest {
    private static final LightningKey KEY = LightningKey.HIGH_VOLTAGE;

    @Test
    void zeroAndNegativeRequestsDoNotChangeFullInventory() {
        var inv = UnlimitedReturnInventory.create(() -> {}, null);
        inv.setStack(0, new GenericStack(KEY, Long.MAX_VALUE));
        for (var mode : Actionable.values()) {
            assertEquals(0, inv.insert(0, KEY, 0, mode));
            assertEquals(0, inv.insert(0, KEY, -1, mode));
            assertEquals(Long.MAX_VALUE, inv.getAmount(0));
        }
    }

    @Test
    void nearLimitSimulationAcknowledgesOnlyRepresentableAmount() {
        var inv = UnlimitedReturnInventory.create(() -> {}, null);
        inv.setStack(0, new GenericStack(KEY, Long.MAX_VALUE - 7));
        assertEquals(7, inv.insert(0, KEY, 100, Actionable.SIMULATE));
        assertEquals(Long.MAX_VALUE - 7, inv.getAmount(0));
    }

    @Test
    void nearLimitMutationCannotWrapOrDeleteOwnedResources() {
        var inv = UnlimitedReturnInventory.create(() -> {}, null);
        inv.setStack(0, new GenericStack(KEY, Long.MAX_VALUE - 7));
        assertEquals(7, inv.insert(0, KEY, 100, Actionable.MODULATE));
        assertEquals(Long.MAX_VALUE, inv.getAmount(0));
        assertEquals(0, inv.insert(0, KEY, 1, Actionable.MODULATE));
        assertEquals(Long.MAX_VALUE, inv.getAmount(0));
    }

    @Test
    void storageFacadeCannotAcknowledgeUnstoredResources() {
        var inv = UnlimitedReturnInventory.create(() -> {}, null);
        inv.setStack(0, new GenericStack(KEY, Long.MAX_VALUE - 7));
        assertEquals(7, inv.insert(KEY, 100, Actionable.SIMULATE, IActionSource.empty()));
        assertEquals(Long.MAX_VALUE - 7, inv.getAmount(0));
        assertEquals(7, inv.insert(KEY, 100, Actionable.MODULATE, IActionSource.empty()));
        assertEquals(Long.MAX_VALUE, inv.getAmount(0));
    }

    @Test
    void randomizedNearLimitInsertionsConserveAcknowledgedUnits() {
        var random = new SplittableRandom(20261004L);
        var inv = UnlimitedReturnInventory.create(() -> {}, null);
        for (int round = 0; round < 10_000; round++) {
            long initial = Long.MAX_VALUE - random.nextLong(10_000);
            long request = random.nextBoolean() ? Long.MAX_VALUE : random.nextLong(1, 20_000);
            long expected = Math.min(request, Long.MAX_VALUE - initial);
            inv.setStack(0, new GenericStack(KEY, initial));
            assertEquals(expected, inv.insert(0, KEY, request, Actionable.SIMULATE), "simulation round " + round);
            assertEquals(initial, inv.getAmount(0));
            assertEquals(expected, inv.insert(0, KEY, request, Actionable.MODULATE), "mutation round " + round);
            assertEquals(initial + expected, inv.getAmount(0));
        }
    }

    @Test
    void projectedGuiPageObeysTheSameLongLimit() {
        var controller = new OverloadedReturnInventoryController(72, () -> {}, null);
        controller.full().setStack(0, new GenericStack(KEY, Long.MAX_VALUE - 7));
        controller.copyFullToPage();
        assertEquals(7, controller.pageView().insert(0, KEY, 100, Actionable.MODULATE));
        assertEquals(Long.MAX_VALUE, controller.full().getAmount(0));
        assertEquals(Long.MAX_VALUE, controller.pageView().getAmount(0));
    }

    @Test
    void externalInsertBeforeGuiInsertKeepsEveryAcknowledgedResource() {
        var controller = new OverloadedReturnInventoryController(72, () -> {}, null);
        assertEquals(10, controller.full().insert(0, KEY, 10, Actionable.MODULATE));
        controller.copyFullToPage();
        assertEquals(5, controller.full().insert(0, KEY, 5, Actionable.MODULATE));

        assertEquals(1, controller.pageView().insert(0, KEY, 1, Actionable.MODULATE));

        assertEquals(16, controller.full().getAmount(0));
        assertEquals(16, controller.pageView().getAmount(0));
        controller.setCurrentPage(1);
        controller.setCurrentPage(0);
        assertEquals(16, controller.pageView().getAmount(0));
    }

    @Test
    void guiInsertCannotReviveResourcesAlreadyReturnedToTheNetwork() {
        var controller = new OverloadedReturnInventoryController(72, () -> {}, null);
        assertEquals(10, controller.full().insert(0, KEY, 10, Actionable.MODULATE));
        controller.copyFullToPage();
        // Network return removes the owned stack before the next menu broadcast.
        controller.full().setStack(0, null);

        assertEquals(1, controller.pageView().insert(0, KEY, 1, Actionable.MODULATE));

        assertEquals(1, controller.full().getAmount(0));
        assertEquals(1, controller.pageView().getAmount(0));
    }

    @Test
    void reentrantAdmissionAndChangeCallbacksCannotCreditTheSameSpaceTwice() {
        UnlimitedReturnInventory[] holder = {null};
        holder[0] = UnlimitedReturnInventory.create(() ->
                assertEquals(0, holder[0].insert(0, KEY, 1, Actionable.MODULATE)), null);
        var inv = holder[0];
        int[] admissions = {0};
        inv.setAdmissionPolicy(key -> {
            admissions[0]++;
            assertEquals(0, inv.insert(0, KEY, 1, Actionable.MODULATE));
            return true;
        });
        assertEquals(7, inv.insert(KEY, 7, Actionable.MODULATE, IActionSource.empty()));
        assertEquals(7, inv.getAmount(0));
        assertEquals(1, admissions[0]);
    }

    @Test
    void admissionFailureReleasesTheInsertGuardWithoutChangingOwnedResources() {
        var inv = UnlimitedReturnInventory.create(() -> {}, null);
        inv.setStack(0, new GenericStack(KEY, 100));
        inv.setAdmissionPolicy(key -> { throw new IllegalStateException("admission unavailable"); });
        assertThrows(IllegalStateException.class, () -> inv.insert(0, KEY, 1, Actionable.MODULATE));
        assertEquals(100, inv.getAmount(0));
        inv.setAdmissionPolicy(key -> true);
        assertEquals(1, inv.insert(0, KEY, 1, Actionable.MODULATE));
        assertEquals(101, inv.getAmount(0));
    }
}

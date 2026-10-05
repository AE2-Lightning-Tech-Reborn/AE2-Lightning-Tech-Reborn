package com.moakiee.ae2lt.crafting.report;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.fml.loading.LoadingModList;
import org.junit.jupiter.api.Test;

class CraftingReportInventoryTest {
    static {
        LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static final AEKey IRON = AEItemKey.of(Items.IRON_INGOT);
    private static final AEKey GOLD = AEItemKey.of(Items.GOLD_INGOT);

    @Test
    void missingIsSubtractedFromOriginalStockWithoutReadingNewInventory() {
        var retained = new CraftingReportInventory();
        var live = stock(100, 30);
        var first = retained.captureCalculation(() -> CraftingReportInventory.readAvailableStacks(() -> live));
        live.set(IRON, 500);
        live.set(GOLD, 900);
        first.clear();
        retained.recordMissing(new GenericStack(IRON, 20));
        retained.subtractMissing();

        var replan = readFrozen(retained);
        assertEquals(80, replan.get(IRON));
        assertEquals(30, replan.get(GOLD));
        replan.set(IRON, 1_000);
        assertEquals(80, readFrozen(retained).get(IRON), "workers must not mutate retained stock");
    }

    @Test
    void retriesDoNotDoubleSubtractAndLaterReplansContinueDecreasing() {
        var retained = captured(stock(100, 30));
        retained.recordMissing(new GenericStack(IRON, 20));
        retained.recordMissing(new GenericStack(IRON, 20));
        retained.recordMissing(new GenericStack(IRON, 25));
        retained.recordMissing(new GenericStack(GOLD, 7));
        assertEquals(100, readFrozen(retained).get(IRON), "retry keeps the original plan snapshot");
        retained.subtractMissing();
        assertEquals(75, readFrozen(retained).get(IRON));
        assertEquals(23, readFrozen(retained).get(GOLD));
        retained.subtractMissing();
        assertEquals(75, readFrozen(retained).get(IRON), "one error is consumed only once");
        retained.recordMissing(new GenericStack(IRON, 5));
        retained.subtractMissing();
        assertEquals(70, readFrozen(retained).get(IRON));
    }

    @Test
    void deductionsClampAtZeroAndCannotCreateNewKeysOrOverflow() {
        var retained = captured(stock(5, Long.MAX_VALUE));
        retained.recordMissing(new GenericStack(IRON, Long.MAX_VALUE));
        retained.recordMissing(new GenericStack(GOLD, Long.MAX_VALUE));
        retained.recordMissing(new GenericStack(AEItemKey.of(Items.DIAMOND), 1));
        retained.subtractMissing();
        assertTrue(readFrozen(retained).isEmpty());
    }

    @Test
    void missingOneComponentVariantDoesNotReduceAnotherVariant() {
        var markedStack = new ItemStack(Items.IRON_INGOT);
        markedStack.set(DataComponents.CUSTOM_NAME, Component.literal("marked"));
        var marked = AEItemKey.of(markedStack);
        var initial = stock(100, 0);
        initial.set(marked, 50);
        var retained = captured(initial);
        retained.recordMissing(new GenericStack(marked, 10));
        retained.subtractMissing();
        assertEquals(100, readFrozen(retained).get(IRON));
        assertEquals(40, readFrozen(retained).get(marked));
    }

    @Test
    void captureIsScopedAndRestoredAfterNestedCallsAndExceptions() {
        var outer = captured(stock(10, 0));
        var inner = captured(stock(20, 0));
        outer.captureCalculation(() -> {
            assertThrows(IllegalStateException.class, () -> inner.captureCalculation(() -> {
                assertEquals(20, CraftingReportInventory.readAvailableStacks(KeyCounter::new).get(IRON));
                throw new IllegalStateException("test");
            }));
            assertEquals(10, CraftingReportInventory.readAvailableStacks(KeyCounter::new).get(IRON));
            assertEquals(30, CompletableFuture.supplyAsync(
                    () -> CraftingReportInventory.readAvailableStacks(() -> stock(30, 0)).get(IRON)).join());
            return null;
        });
        var ordinary = stock(40, 0);
        assertSame(ordinary, CraftingReportInventory.readAvailableStacks(() -> ordinary));
    }

    private static CraftingReportInventory captured(KeyCounter stock) {
        var result = new CraftingReportInventory();
        result.captureCalculation(() -> CraftingReportInventory.readAvailableStacks(() -> stock));
        return result;
    }

    private static KeyCounter readFrozen(CraftingReportInventory snapshot) {
        return snapshot.captureCalculation(() -> CraftingReportInventory.readAvailableStacks(() -> {
            throw new AssertionError("replan must not read live inventory");
        }));
    }

    private static KeyCounter stock(long iron, long gold) {
        var stock = new KeyCounter();
        stock.set(IRON, iron);
        stock.set(GOLD, gold);
        return stock;
    }
}

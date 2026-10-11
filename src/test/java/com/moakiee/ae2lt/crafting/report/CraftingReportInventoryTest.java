package com.moakiee.ae2lt.crafting.report;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageService;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.crafting.CraftingPlan;
import appeng.crafting.execution.CraftingCpuHelper;
import appeng.crafting.inv.ListCraftingInventory;
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
    void failedKeyIsClampedWithoutReadingNewWholeInventory() {
        var retained = new CraftingReportInventory();
        var live = stock(100, 30);
        var first = retained.captureCalculation(() -> CraftingReportInventory.readAvailableStacks(() -> live));
        live.set(IRON, 500);
        live.set(GOLD, 900);
        first.clear();
        recordAvailable(retained, IRON, 80);
        retained.clampToAvailable();

        var replan = readFrozen(retained);
        assertEquals(80, replan.get(IRON));
        assertEquals(30, replan.get(GOLD));
        replan.set(IRON, 1_000);
        assertEquals(80, readFrozen(retained).get(IRON), "workers must not mutate retained stock");
    }

    @Test
    void retriesKeepLowestObservationAndLaterReplansCannotIncreaseStock() {
        var retained = captured(stock(100, 30));
        recordAvailable(retained, IRON, 80);
        recordAvailable(retained, IRON, 80);
        recordAvailable(retained, IRON, 75);
        recordAvailable(retained, IRON, 90);
        recordAvailable(retained, GOLD, 23);
        assertEquals(100, readFrozen(retained).get(IRON), "retry keeps the original plan snapshot");
        retained.clampToAvailable();
        assertEquals(75, readFrozen(retained).get(IRON));
        assertEquals(23, readFrozen(retained).get(GOLD));
        retained.clampToAvailable();
        assertEquals(75, readFrozen(retained).get(IRON), "one error is consumed only once");
        recordAvailable(retained, IRON, 500);
        retained.clampToAvailable();
        assertEquals(75, readFrozen(retained).get(IRON), "new stock must not increase the snapshot");
        recordAvailable(retained, IRON, 70);
        retained.clampToAvailable();
        assertEquals(70, readFrozen(retained).get(IRON));
    }

    @Test
    void limitsCannotCreateNewKeysOrOverflow() {
        var retained = captured(stock(5, Long.MAX_VALUE));
        recordAvailable(retained, IRON, Long.MAX_VALUE);
        recordAvailable(retained, GOLD, Long.MAX_VALUE);
        recordAvailable(retained, AEItemKey.of(Items.DIAMOND), 10);
        retained.clampToAvailable();
        assertEquals(5, readFrozen(retained).get(IRON));
        assertEquals(Long.MAX_VALUE, readFrozen(retained).get(GOLD));
        assertEquals(0, readFrozen(retained).get(AEItemKey.of(Items.DIAMOND)));

        recordAvailable(retained, IRON, 0);
        recordAvailable(retained, GOLD, 0);
        retained.clampToAvailable();
        assertTrue(readFrozen(retained).isEmpty());
    }

    @Test
    void exhaustedLargeSnapshotIsRemovedAfterOneSmallFailedRequest() {
        var retained = captured(stock(1_000, 30));
        var source = IActionSource.empty();
        var network = new TestStorage(stock(0, 30));
        var deficit = submit(network, source, 100);
        assertEquals(new GenericStack(IRON, 100), deficit);

        retained.recordMissing(deficit, network, source);
        retained.clampToAvailable();
        assertEquals(0, readFrozen(retained).get(IRON));
        assertEquals(30, readFrozen(retained).get(GOLD));
        assertEquals(1, network.simulations);
        assertSame(source, network.lastSimulationSource);
    }

    @Test
    void partialNativeExtractionIsRolledBackBeforeReadOnlyProbeAndClampsInOneStep() {
        var retained = captured(stock(1_000, 30));
        var source = IActionSource.empty();
        var network = new TestStorage(stock(25, 30));
        var deficit = submit(network, source, 100);
        assertEquals(new GenericStack(IRON, 75), deficit);
        assertEquals(25, network.stock.get(IRON), "native submission must have returned the partial extraction");
        int extractionsBeforeProbe = network.modulations;

        retained.recordMissing(deficit, network, source);
        assertEquals(extractionsBeforeProbe, network.modulations, "recording the limit must not take items again");
        assertEquals(1, network.simulations);
        assertEquals(Long.MAX_VALUE, network.lastSimulationAmount);
        assertSame(source, network.lastSimulationSource);
        retained.clampToAvailable();
        assertEquals(25, readFrozen(retained).get(IRON), "use actual availability, not 1000 - 75");
        assertEquals(30, readFrozen(retained).get(GOLD));
        assertNull(submit(network, source, readFrozen(retained).get(IRON)));
        assertEquals(0, network.stock.get(IRON));
    }

    @Test
    void missingOneComponentVariantDoesNotReduceAnotherVariant() {
        var markedStack = new ItemStack(Items.IRON_INGOT);
        markedStack.set(DataComponents.CUSTOM_NAME, Component.literal("marked"));
        var marked = AEItemKey.of(markedStack);
        var initial = stock(100, 0);
        initial.set(marked, 50);
        var retained = captured(initial);
        recordAvailable(retained, marked, 40);
        retained.clampToAvailable();
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

    private static void recordAvailable(CraftingReportInventory retained, AEKey key, long available) {
        var stock = new KeyCounter();
        stock.set(key, available);
        retained.recordMissing(new GenericStack(key, 1), new TestStorage(stock), IActionSource.empty());
    }

    private static GenericStack submit(TestStorage network, IActionSource source, long amount) {
        var storage = proxy(IStorageService.class, "getInventory", network);
        var grid = proxy(IGrid.class, "getStorageService", storage);
        var used = stock(amount, 0);
        used.removeZeros();
        var plan = new CraftingPlan(new GenericStack(GOLD, 1), 8, false, false,
                used, new KeyCounter(), new KeyCounter(), Map.of());
        var cpuInventory = new ListCraftingInventory(key -> {});
        var deficit = CraftingCpuHelper.tryExtractInitialItems(plan, grid, cpuInventory, source);
        assertEquals(deficit == null ? amount : 0L, cpuInventory.list.get(IRON));
        return deficit;
    }

    private static <T> T proxy(Class<T> type, String getter, Object value) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type},
                (instance, method, args) -> {
                    if (method.getName().equals(getter)) return value;
                    throw new AssertionError("Unexpected call: " + method);
                }));
    }

    private static final class TestStorage implements MEStorage {
        private final KeyCounter stock;
        private int modulations;
        private int simulations;
        private long lastSimulationAmount;
        private IActionSource lastSimulationSource;

        private TestStorage(KeyCounter stock) {
            this.stock = stock;
        }

        @Override
        public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
            long extracted = Math.min(stock.get(what), amount);
            if (mode == Actionable.MODULATE) {
                modulations++;
                stock.set(what, stock.get(what) - extracted);
            } else {
                simulations++;
                lastSimulationAmount = amount;
                lastSimulationSource = source;
            }
            return extracted;
        }

        @Override
        public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
            if (mode == Actionable.MODULATE) stock.add(what, amount);
            return amount;
        }

        @Override
        public void getAvailableStacks(KeyCounter out) {
            fail("only the failed key may be probed; do not enumerate live inventory");
        }

        @Override
        public Component getDescription() {
            return Component.literal("replan test storage");
        }
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

package com.moakiee.ae2lt.crafting.timewheel;

import static org.junit.jupiter.api.Assertions.*;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageService;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.crafting.inv.ListCraftingInventory;
import appeng.menu.me.common.IncrementalUpdateHelper;
import com.moakiee.ae2lt.me.key.LightningKey;
import java.lang.reflect.Proxy;
import org.junit.jupiter.api.Test;

/** Exercises the inventory and change feed consumed by AE2's crafting status menu. */
class TimeWheelStorageStatusTest {
    private static final AEKey OUTPUT = LightningKey.HIGH_VOLTAGE;

    @Test
    void completedDirectOutputIsVisibleImmediatelyAndWhenOpeningTheMenu() throws Exception {
        var fixture = new Fixture();
        fixture.remainders.insert(OUTPUT, 6, Actionable.MODULATE);

        assertTrue(fixture.cpu.isBusy());
        assertTrue(fixture.logic.isCantStoreItems());
        assertEquals(6, fixture.logic.getStored(OUTPUT));
        var items = new KeyCounter();
        fixture.logic.getAllItems(items);
        assertEquals(6, items.get(OUTPUT));
        assertEquals(0, fixture.logic.getWaitingFor(OUTPUT));
        assertEquals(0, fixture.logic.getPendingOutputs(OUTPUT));
    }

    @Test
    void partialAndCompleteRecoveryUpdateAnAlreadyOpenMenu() throws Exception {
        var fixture = new Fixture();
        fixture.remainders.insert(OUTPUT, 6, Actionable.MODULATE);
        fixture.changes.commitChanges();

        fixture.logic.tickCraftingLogic(null, null);
        assertEquals(6, fixture.logic.getStored(OUTPUT));
        assertTrue(fixture.logic.isCantStoreItems());
        assertFalse(fixture.changes.hasChanges(), "blocked storage does not change the quantity");

        fixture.capacity = 2;
        fixture.logic.tickCraftingLogic(null, null);
        assertEquals(4, fixture.logic.getStored(OUTPUT));
        assertEquals(2, fixture.stored);
        assertTrue(fixture.logic.isCantStoreItems());
        assertTrue(fixture.changes.hasChanges(), "send the reduced quantity to an open menu");
        assertEquals(OUTPUT, fixture.changes.iterator().next());
        fixture.changes.commitChanges();

        fixture.capacity = 4;
        fixture.logic.tickCraftingLogic(null, null);
        assertEquals(0, fixture.logic.getStored(OUTPUT));
        assertEquals(6, fixture.stored);
        assertFalse(fixture.logic.isCantStoreItems());
        assertFalse(fixture.cpu.isBusy());
        assertTrue(fixture.changes.hasChanges(), "remove the drained row from an open menu");
        assertEquals(OUTPUT, fixture.changes.iterator().next());
    }

    @Test
    void ordinaryInventoryAndDirectReturnsAppearAsOneStoredQuantity() throws Exception {
        var fixture = new Fixture();
        fixture.logic.getInventory().insert(OUTPUT, 2, Actionable.MODULATE);
        fixture.remainders.insert(OUTPUT, 6, Actionable.MODULATE);

        assertEquals(8, fixture.logic.getStored(OUTPUT));
        var items = new KeyCounter();
        fixture.logic.getAllItems(items);
        assertEquals(8, items.get(OUTPUT));
    }

    @Test
    void combinedStoredQuantityCannotOverflow() throws Exception {
        var fixture = new Fixture();
        fixture.logic.getInventory().insert(OUTPUT, Long.MAX_VALUE - 1, Actionable.MODULATE);
        fixture.remainders.insert(OUTPUT, 6, Actionable.MODULATE);

        assertEquals(Long.MAX_VALUE, fixture.logic.getStored(OUTPUT));
        var items = new KeyCounter();
        fixture.logic.getAllItems(items);
        assertEquals(Long.MAX_VALUE, items.get(OUTPUT));
    }

    private static final class Fixture {
        final TimeWheelCraftingCPU cpu;
        final Ae2LtTimeWheelCraftingCpuLogic logic;
        final ListCraftingInventory remainders;
        final IncrementalUpdateHelper changes = new IncrementalUpdateHelper();
        long capacity;
        long stored;

        Fixture() throws Exception {
            var storage = proxy(MEStorage.class, (name, args) -> {
                if (!name.equals("insert")) return null;
                long accepted = Math.min(capacity, (long) args[1]);
                if (args[2] == Actionable.MODULATE) {
                    capacity -= accepted;
                    stored += accepted;
                }
                return accepted;
            });
            var service = proxy(IStorageService.class, (name, args) ->
                    name.equals("getInventory") ? storage : null);
            var grid = proxy(IGrid.class, (name, args) ->
                    name.equals("getStorageService") ? service : null);
            var host = proxy(TimeWheelCraftingCpuHost.class, (name, args) -> switch (name) {
                case "getGrid" -> grid;
                case "isCpuActive" -> true;
                case "getActionSource" -> IActionSource.empty();
                default -> null;
            });
            cpu = new TimeWheelCraftingCPU(host, 1024, 1, 1, false);
            logic = cpu.getCraftingLogic();
            var field = Ae2LtTimeWheelCraftingCpuLogic.class.getDeclaredField("directOutputRemainders");
            field.setAccessible(true);
            remainders = (ListCraftingInventory) field.get(logic);
            logic.addListener(changes::addChange);
        }
    }

    private static <T> T proxy(Class<T> type, java.util.function.BiFunction<String, Object[], Object> call) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, (p, method, args) -> {
            if (method.getName().equals("hashCode")) return System.identityHashCode(p);
            if (method.getName().equals("equals")) return p == args[0];
            var result = call.apply(method.getName(), args);
            if (result != null) return result;
            if (method.getReturnType() == boolean.class) return false;
            if (method.getReturnType() == int.class) return 0;
            if (method.getReturnType() == long.class) return 0L;
            return null;
        }));
    }
}

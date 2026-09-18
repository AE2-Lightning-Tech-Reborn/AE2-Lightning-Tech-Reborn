package com.moakiee.ae2lt.crafting.timewheel;

import static com.moakiee.ae2lt.crafting.timewheel.TimeWheelDispatchBoundaryTest.field;
import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.function.Predicate;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingService;
import appeng.menu.me.crafting.CraftingCPURecord;
import com.google.common.collect.ImmutableSet;
import com.moakiee.ae2lt.me.key.LightningKey;
import org.junit.jupiter.api.Test;

class TimeWheelManualCpuSelectionTest {
    @Test
    void originalSelectorReproducesCapacityLossRetargeting() throws Exception {
        var f = new Fixture(false);
        f.selectPool();
        f.capacity(4);
        f.refresh();
        assertSame(f.other, f.selected);
    }

    @Test
    void capacityLossRetainsExplicitTargetWithItsCurrentCapacity() throws Exception {
        var f = new Fixture(true);
        f.selectPool();
        f.capacity(4);
        f.refresh();
        assertSame(f.pool, f.selected);
        assertEquals(4L, field(CraftingCPURecord.class, "size").getLong(f.candidates().get(f.index())));
        assertEquals(2, f.candidates().size());
    }

    @Test
    void reorderingEligibleCpusKeepsTheSelectedObject() throws Exception {
        var f = new Fixture(true);
        f.selectPool();
        var faster = new TimeWheelCraftingCpuPool(f.pool.getHost(), 1024, 32, 1, false);
        f.available = ImmutableSet.of(f.other, faster, f.pool);
        f.refresh();
        assertSame(f.pool, f.selected);
        assertEquals(1, f.index());
    }

    @Test
    void busyMaintenanceCpuDoesNotChangeSelection() throws Exception {
        var f = new Fixture(true);
        f.selectPool();
        var busy = new TimeWheelCraftingCPU(f.pool.getHost(), 1024, 16, 1, false);
        busy.getCraftingLogic().getInventory().insert(LightningKey.HIGH_VOLTAGE, 1, Actionable.MODULATE);
        f.available = ImmutableSet.of(busy, f.other, f.pool);
        f.refresh();
        assertSame(f.pool, f.selected);
        assertFalse(f.candidates().stream().map(TimeWheelManualCpuSelectionTest::cpuOf).anyMatch(cpu -> cpu == busy));
        f.available = ImmutableSet.of(f.other, f.pool);
        f.refresh();
        assertSame(f.pool, f.selected);
    }

    @Test
    void disappearingTargetDoesNotSwitchToAnotherCpuOrAutomatic() throws Exception {
        var f = new Fixture(true);
        f.selectPool();
        f.available = ImmutableSet.of(f.other);
        f.refresh();
        assertSame(f.pool, f.selected);
        f.available = ImmutableSet.of();
        f.refresh();
        assertSame(f.pool, f.selected);
        assertEquals(1, f.candidates().size());
        assertEquals(0, f.index());
    }

    @Test
    void repeatedRefreshesDoNotAccumulateUnavailableRowsAndSamePoolCanRecover() throws Exception {
        var f = new Fixture(true);
        f.selectPool();
        f.capacity(4);
        for (int i = 0; i < 80; i++) {
            f.refresh();
            assertSame(f.pool, f.selected);
            assertEquals(2, f.candidates().size());
        }
        f.capacity(1024);
        f.refresh();
        assertSame(f.pool, f.selected);
        assertEquals(2, f.candidates().size());
        f.cycle(true);
        assertSame(f.other, f.selected);
        f.refresh();
        assertSame(f.other, f.selected);
    }

    @Test
    void cyclingAwayFromUnavailablePoolKeepsNewSelectionOnTheNextRefresh() throws Exception {
        var f = new Fixture(true);
        f.selectPool();
        f.capacity(4);
        f.refresh();
        f.cycle(true);
        assertSame(f.other, f.selected);
        assertEquals(1, f.candidates().size());
        f.refresh();
        assertSame(f.other, f.selected);
    }

    @Test
    void playerCanExplicitlyChooseAutomaticAfterTargetDisappears() throws Exception {
        var f = new Fixture(true);
        f.selectPool();
        f.available = ImmutableSet.of();
        f.refresh();
        f.cycle(false);
        assertNull(f.selected);
        assertEquals(-1, f.index());
        assertTrue(f.candidates().isEmpty());
        f.available = ImmutableSet.of(f.pool, f.other);
        f.refresh();
        assertNull(f.selected);
    }

    @Test
    void automaticModeStaysAutomaticWhileCandidatesChange() throws Exception {
        var f = new Fixture(true);
        f.refresh();
        f.capacity(4);
        f.refresh();
        assertNull(f.selected);
        f.capacity(1024);
        f.refresh();
        assertNull(f.selected);
    }

    @Test
    void poolsWithTheSameNameAreNotInterchangeable() throws Exception {
        var f = new Fixture(true);
        f.selectPool();
        var replacement = new TimeWheelCraftingCpuPool(f.pool.getHost(), 1024, 16, 1, false);
        f.available = ImmutableSet.of(replacement, f.other);
        f.refresh();
        assertSame(f.pool, f.selected);
        assertNotSame(replacement, f.selected);
    }

    @Test
    void captureDoesNotPinOtherCpuImplementationsOrVirtualCpus() throws Exception {
        var f = new Fixture(true);
        var virtual = new TimeWheelCraftingCPU(f.pool.getHost(), 1024, 16, 1, false);
        for (var cpu : List.of(f.other, virtual)) {
            var records = List.of(new CraftingCPURecord(1024, 16, cpu));
            assertNull(f.binding.capture(records, 0, TimeWheelManualCpuSelectionTest::cpuOf));
        }
    }

    /** Uses AE2's real list rebuilding and cycling, then the production reconciliation helper.
     * Reconciles before publishing the final callback; full Mixin transformation is not run here. */
    private static final class Fixture {
        final TimeWheelCraftingCpuPool pool;
        final ICraftingCPU other;
        final IGrid grid;
        final Class<?> cyclerType = Class.forName("appeng.menu.me.crafting.CraftingCPUCycler");
        final Object cycler;
        final boolean fix;
        final TimeWheelManualCpuSelection binding = new TimeWheelManualCpuSelection();
        ImmutableSet<ICraftingCPU> available;
        ICraftingCPU selected;
        boolean rebuilding;
        boolean notified;

        Fixture(boolean fix) throws Exception {
            this.fix = fix;
            var host = proxy(TimeWheelCraftingCpuPoolHost.class,
                    (name, args) -> name.equals("isCpuActive") ? true : null);
            pool = new TimeWheelCraftingCpuPool(host, 1024, 16, 1, false);
            other = proxy(ICraftingCPU.class,
                    (name, args) -> name.equals("getAvailableStorage") ? 1024L : null);
            available = ImmutableSet.of(pool, other);
            var service = proxy(ICraftingService.class,
                    (name, args) -> name.equals("getCpus") ? available : null);
            grid = proxy(IGrid.class, (name, args) -> name.equals("getCraftingService") ? service : null);
            var listenerType = Class.forName(cyclerType.getName() + "$ChangeListener");
            var listener = proxy(listenerType, (name, args) -> {
                if (name.equals("onChange")) {
                    notified = true;
                    if (!rebuilding) selected = args[0] == null ? null : cpuOf((CraftingCPURecord) args[0]);
                }
                return null;
            });
            var ctor = cyclerType.getDeclaredConstructor(Predicate.class, listenerType);
            ctor.setAccessible(true);
            Predicate<ICraftingCPU> filter = cpu -> cpu.getAvailableStorage() >= 8 && !cpu.isBusy();
            cycler = ctor.newInstance(filter, listener);
            invoke("setAllowNoSelection", new Class<?>[] {boolean.class}, true);
        }

        void selectPool() throws Exception {
            refresh();
            cycle(true);
            assertSame(pool, selected);
        }

        void capacity(long bytes) throws Exception { field(pool.getClass(), "remainingStorage").setLong(pool, bytes); }

        void refresh() throws Exception {
            int previousIndex = index();
            var captured = binding.capture(
                    candidates(), previousIndex, TimeWheelManualCpuSelectionTest::cpuOf);
            rebuilding = fix;
            notified = false;
            invoke("detectAndSendChanges", new Class<?>[] {IGrid.class}, grid);
            if (fix && notified) {
                setIndex(binding.restore(candidates(), previousIndex, captured, TimeWheelManualCpuSelectionTest::cpuOf));
                rebuilding = false;
                invoke("notifyListener", new Class<?>[0]);
            }
            rebuilding = false;
        }

        void cycle(boolean forward) throws Exception {
            if (fix) setIndex(binding.prepareCycle(candidates(), index(), forward));
            invoke("cycleCpu", new Class<?>[] {boolean.class}, forward);
        }

        @SuppressWarnings("unchecked")
        List<CraftingCPURecord> candidates() throws Exception {
            return (List<CraftingCPURecord>) field(cyclerType, "cpus").get(cycler);
        }
        int index() throws Exception { return field(cyclerType, "selectedCpu").getInt(cycler); }
        void setIndex(int index) throws Exception { field(cyclerType, "selectedCpu").setInt(cycler, index); }
        void invoke(String name, Class<?>[] parameterTypes, Object... arguments) throws Exception {
            var method = cyclerType.getDeclaredMethod(name, parameterTypes);
            method.setAccessible(true);
            method.invoke(cycler, arguments);
        }
    }

    private static ICraftingCPU cpuOf(CraftingCPURecord record) {
        try {
            return (ICraftingCPU) field(CraftingCPURecord.class, "cpu").get(record);
        } catch (Exception e) { throw new AssertionError(e); }
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

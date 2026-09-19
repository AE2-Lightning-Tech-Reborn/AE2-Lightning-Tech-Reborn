package com.moakiee.ae2lt.debug;

import java.lang.reflect.Proxy;
import java.util.function.Predicate;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingService;
import com.google.common.collect.ImmutableSet;
import com.moakiee.ae2lt.crafting.timewheel.TimeWheelCraftingCpuPool;
import com.moakiee.ae2lt.crafting.timewheel.TimeWheelCraftingCpuPoolHost;
import com.moakiee.ae2lt.mixin.CraftingCPURecordAccessor;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Exercises the actual Mixin-transformed AE2 selector via runCpuSelectionGameTestServer. */
@GameTestHolder("ae2lt_cpu_selection")
@PrefixGameTestTemplate(false)
public final class TimeWheelCpuSelectionGameTests {
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void capacityLossAndRecoveryKeepTheManualTarget(GameTestHelper helper) throws Exception {
        var f = new Fixture();
        f.selectPool();
        f.capacity(4);
        for (int i = 0; i < 80; i++) {
            f.refresh();
            require(f.selected == f.pool, "capacity loss changed the explicit CPU");
            require(f.displayedBytes == 4, "capacity loss was not sent to the menu");
        }
        f.capacity(1024);
        f.refresh();
        require(f.displayedBytes == 1024, "capacity recovery left stale available bytes in the menu");
        f.capacity(512);
        f.refresh();
        require(f.displayedBytes == 512, "capacity changed without a list rebuild but was not sent to the menu");
        f.cycle(true);
        require(f.selected == f.other, "recovered pool could not be deselected");
        f.refresh();
        require(f.selected == f.other, "recovered placeholder changed the new selection");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void missingTargetWaitsForExplicitReselection(GameTestHelper helper) throws Exception {
        var f = new Fixture();
        f.selectPool();
        f.available = ImmutableSet.of(f.other);
        f.refresh();
        require(f.selected == f.pool, "missing pool switched to another CPU");
        f.cycle(true);
        f.refresh();
        require(f.selected == f.other, "leaving unavailable pool changed the next selection");
        f = new Fixture();
        f.selectPool();
        f.available = ImmutableSet.of();
        f.refresh();
        require(f.selected == f.pool, "empty list silently switched to automatic");
        f.cycle(false);
        require(f.selected == null, "player could not explicitly choose automatic");
        f.available = ImmutableSet.of(f.pool, f.other);
        f.refresh();
        require(f.selected == null, "automatic selection became manual");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void newlyAvailableCpuCannotDisplaceSelectedPool(GameTestHelper helper) throws Exception {
        var f = new Fixture();
        f.selectPool();
        var faster = new TimeWheelCraftingCpuPool(f.pool.getHost(), 1024, 32, 1, false);
        f.available = ImmutableSet.of(faster, f.other, f.pool);
        f.refresh();
        require(f.selected == f.pool, "reordering changed the selected CPU identity");
        helper.succeed();
    }

    private static final class Fixture {
        final TimeWheelCraftingCpuPool pool;
        final ICraftingCPU other;
        final IGrid grid;
        final Class<?> cyclerType = Class.forName("appeng.menu.me.crafting.CraftingCPUCycler");
        final Object cycler;
        ImmutableSet<ICraftingCPU> available;
        ICraftingCPU selected;
        long displayedBytes;

        Fixture() throws Exception {
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
                    var record = (CraftingCPURecordAccessor) args[0];
                    selected = record == null ? null : record.ae2lt$getCpu();
                    displayedBytes = record == null ? 0 : record.ae2lt$getSize();
                }
                return null;
            });
            var ctor = cyclerType.getDeclaredConstructor(Predicate.class, listenerType);
            ctor.setAccessible(true);
            Predicate<ICraftingCPU> filter = cpu -> cpu.getAvailableStorage() >= 8 && !cpu.isBusy();
            cycler = ctor.newInstance(filter, listener);
            invoke("setAllowNoSelection", boolean.class, true);
        }

        void selectPool() throws Exception {
            refresh();
            cycle(true);
            require(selected == pool, "could not select the LT pool");
        }
        void capacity(long bytes) throws Exception {
            var field = TimeWheelCraftingCpuPool.class.getDeclaredField("remainingStorage");
            field.setAccessible(true);
            field.setLong(pool, bytes);
        }
        void refresh() throws Exception { invoke("detectAndSendChanges", IGrid.class, grid); }
        void cycle(boolean forward) throws Exception { invoke("cycleCpu", boolean.class, forward); }
        void invoke(String name, Class<?> type, Object value) throws Exception {
            var method = cyclerType.getDeclaredMethod(name, type);
            method.setAccessible(true);
            method.invoke(cycler, value);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
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

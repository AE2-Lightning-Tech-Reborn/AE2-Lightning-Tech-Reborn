package com.moakiee.ae2lt.crafting.big;

import static org.junit.jupiter.api.Assertions.*;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.networking.security.IActionHost;
import com.google.common.collect.ImmutableSet;
import com.moakiee.ae2lt.crafting.timewheel.TimeWheelCraftingCpuPool;
import com.moakiee.ae2lt.mixin.thunderbolt.TianshuPlanningServiceMixin;
import com.moakiee.thunderbolt.api.crafting.ICraftingPlanningService;
import com.moakiee.thunderbolt.api.crafting.PlanningChoice;
import com.moakiee.thunderbolt.core.crafting.planner.CpSatPlanningEngine;
import com.moakiee.thunderbolt.core.crafting.planner.ThunderboltV2PlanningEngine;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Set;

/** Exercises the production menu gate and resolver injection with real CPU-pool instances. */
class TianshuPlanningBoundaryTest {
    private static final PlanningChoice TIANSHU = PlanningChoice.engine(ThunderboltV2PlanningEngine.ID);

    @Test
    void anotherCpuCannotEnableTianshuOrEvenQueryItsPlanningPolicy() throws Exception {
        var other = proxy(ICraftingCPU.class, (method, args) -> null);
        var grid = grid(ImmutableSet.of(other), null);
        assertFalse(BigCraftingMenus.hasTianshu(grid));
        assertFalse(BigCraftingMenus.planningEnabled(grid));
        assertNull(BigCraftingMenus.planningContext(host(grid)));
        assertEquals(List.of(PlanningChoice.VANILLA), inject(grid,
                List.of(TIANSHU, PlanningChoice.VANILLA)));
    }

    @Test
    void fullCpuPoolStillEnablesTianshuPlanning() throws Exception {
        // No remaining storage and no free virtual CPU are required to own planning.
        var pool = new TimeWheelCraftingCpuPool(null, 0, 0, 1, false);
        var choices = List.of(TIANSHU, PlanningChoice.VANILLA);
        var grid = grid(ImmutableSet.of(pool), choices);
        assertTrue(BigCraftingMenus.hasTianshu(grid));
        assertTrue(BigCraftingMenus.planningEnabled(grid));
        assertSame(grid, BigCraftingMenus.planningContext(host(grid)).grid());
        assertEquals(choices, inject(grid, choices));
    }

    @Test
    void vanillaOptOutDisablesBigInputDespiteAnOnlineTianshuPool() throws Exception {
        var pool = new TimeWheelCraftingCpuPool(null, 1024, 0, 1, false);
        var choices = List.of(PlanningChoice.VANILLA);
        var grid = grid(ImmutableSet.of(pool), choices);
        assertTrue(BigCraftingMenus.hasTianshu(grid));
        assertFalse(BigCraftingMenus.planningEnabled(grid));
        assertNull(BigCraftingMenus.planningContext(host(grid)));
        assertEquals(choices, inject(grid, choices));
    }

    @Test
    void extendedRequestsKeepAnExplicitCpSatSelection() throws Exception {
        var pool = new TimeWheelCraftingCpuPool(null, 0, 0, 1, false);
        var choices = List.of(PlanningChoice.engine(CpSatPlanningEngine.ID), PlanningChoice.VANILLA);
        var grid = grid(ImmutableSet.of(pool), choices);
        assertSame(grid, BigCraftingMenus.planningContext(host(grid)).grid());
        assertEquals(choices, inject(grid, choices));
    }

    private static IActionHost host(IGrid grid) {
        var node = proxy(IGridNode.class, (method, args) -> switch (method) {
            case "isActive" -> true;
            case "getGrid" -> grid;
            default -> null;
        });
        return proxy(IActionHost.class, (method, args) ->
                method.equals("getActionableNode") ? node : null);
    }

    private static List<PlanningChoice> inject(IGrid grid, List<PlanningChoice> choices) throws Exception {
        var hook = new TianshuPlanningServiceMixin() {};
        var field = TianshuPlanningServiceMixin.class.getDeclaredField("grid");
        field.setAccessible(true);
        field.set(hook, grid);
        var method = TianshuPlanningServiceMixin.class.getDeclaredMethod(
                "ae2lt$requireTianshuCpu", List.class);
        method.setAccessible(true);
        @SuppressWarnings("unchecked")
        var result = (List<PlanningChoice>) method.invoke(hook, choices);
        return result;
    }

    private static IGrid grid(ImmutableSet<ICraftingCPU> cpus, List<PlanningChoice> choices) {
        var service = proxy(ICraftingService.class, (method, args) ->
                method.equals("getCpus") ? cpus : null);
        var planning = proxy(ICraftingPlanningService.class, (method, args) -> {
            if (choices == null) fail("A grid without Tianshu must not enter its planning policy");
            return choices;
        });
        return proxy(IGrid.class, (method, args) -> switch (method) {
            case "getActiveMachines" -> Set.of();
            case "getCraftingService" -> service;
            case "getService" -> {
                assertSame(ICraftingPlanningService.class, args[0]);
                if (choices == null) fail("A grid without Tianshu must not query its planning policy");
                yield planning;
            }
            default -> null;
        });
    }

    private interface Call {
        Object invoke(String method, Object[] args);
    }

    private static <T> T proxy(Class<T> type, Call call) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type},
                (instance, method, args) -> switch (method.getName()) {
                    case "hashCode" -> System.identityHashCode(instance);
                    case "equals" -> instance == args[0];
                    case "toString" -> type.getSimpleName();
                    default -> call.invoke(method.getName(), args);
                }));
    }
}

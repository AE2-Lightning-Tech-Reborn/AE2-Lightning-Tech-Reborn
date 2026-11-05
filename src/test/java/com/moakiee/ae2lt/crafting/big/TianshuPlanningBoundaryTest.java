package com.moakiee.ae2lt.crafting.big;

import static org.junit.jupiter.api.Assertions.*;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingService;
import com.google.common.collect.ImmutableSet;
import com.moakiee.ae2lt.crafting.timewheel.TimeWheelCraftingCpuPool;
import com.moakiee.ae2lt.crafting.algorithm.ExclusiveCraftingLockSource;
import com.moakiee.ae2lt.crafting.algorithm.ExclusiveCraftingPlanning;
import com.moakiee.ae2lt.mixin.thunderbolt.TianshuPlanningServiceMixin;
import com.moakiee.thunderbolt.api.crafting.ICraftingPlanningService;
import com.moakiee.thunderbolt.api.crafting.CraftingAlgorithmSelection;
import com.moakiee.thunderbolt.api.crafting.CraftingPlanningEngines;
import com.moakiee.thunderbolt.api.crafting.PlanningChoice;
import com.moakiee.thunderbolt.core.crafting.planner.ThunderboltV2PlanningEngine;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraftforge.fml.loading.LoadingModList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import net.minecraft.resources.ResourceLocation;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Set;

/** Exercises the production menu gate and resolver injection with real CPU-pool instances. */
class TianshuPlanningBoundaryTest {
    private static final PlanningChoice TIANSHU = PlanningChoice.engine(ThunderboltV2PlanningEngine.ID);

    @BeforeAll
    static void bootstrap() {
        if (LoadingModList.get() == null) LoadingModList.of(List.of(), List.of(), null);
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void anotherCpuCannotEnableTianshuOrEvenQueryItsPlanningPolicy() throws Exception {
        var other = proxy(ICraftingCPU.class, (method, args) -> null);
        var grid = grid(ImmutableSet.of(other), null);
        assertFalse(BigCraftingMenus.hasTianshu(grid));
        assertFalse(BigCraftingMenus.planningEnabled(grid));
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
        assertEquals(choices, inject(grid, choices));
    }

    @Test
    void vanillaOptOutDisablesBigInputDespiteAnOnlineTianshuPool() throws Exception {
        var pool = new TimeWheelCraftingCpuPool(null, 1024, 0, 1, false);
        var choices = List.of(PlanningChoice.VANILLA);
        var grid = grid(ImmutableSet.of(pool), choices);
        assertTrue(BigCraftingMenus.hasTianshu(grid));
        assertFalse(BigCraftingMenus.planningEnabled(grid));
        assertEquals(choices, inject(grid, choices));
    }

    @Test
    void gtlOnlyExclusiveLockIsPreservedBeforeTheTianshuPresenceFilter() throws Exception {
        var selected = ThunderboltV2PlanningEngine.ID;
        var grid = lockedGrid(selected);
        assertEquals(ExclusiveCraftingPlanning.resolve(grid).candidates(), inject(grid,
                List.of(PlanningChoice.VANILLA)));
    }

    @Test
    void exclusiveVanillaSelectionOverridesAnEarlierAddonEngine() throws Exception {
        var grid = lockedGrid(CraftingPlanningEngines.VANILLA_ID);
        assertEquals(List.of(PlanningChoice.VANILLA), inject(grid,
                List.of(TIANSHU, PlanningChoice.VANILLA)));
    }

    private static IGrid lockedGrid(ResourceLocation algorithm) {
        var lock = new ExclusiveCraftingLockSource() {
            public boolean ae2lt$isExclusiveLockActive() { return true; }
            public ResourceLocation ae2lt$getExclusiveAlgorithm() { return algorithm; }
            public int ae2lt$getLockCpuPriority() { return 0; }
            public int ae2lt$getLockProviderPriority() { return 0; }
            public void ae2lt$cycleExclusiveAlgorithm() { }
            public void ae2lt$setExclusiveSelection(CraftingAlgorithmSelection selection) { }
        };
        var node = proxy(IGridNode.class, (method, args) -> method.equals("getOwner") ? lock : null);
        return proxy(IGrid.class, (method, args) -> switch (method) {
            case "getMachines" -> Set.of();
            case "getNodes" -> List.of(node);
            default -> throw new AssertionError("An exclusive lock must not enumerate CPU availability: " + method);
        });
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
            case "getActiveMachines", "getMachines" -> Set.of();
            case "getNodes" -> List.of();
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

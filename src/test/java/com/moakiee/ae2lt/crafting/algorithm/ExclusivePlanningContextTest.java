package com.moakiee.ae2lt.crafting.algorithm;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import com.moakiee.thunderbolt.api.crafting.CraftingAlgorithmSelection;
import com.moakiee.thunderbolt.api.crafting.CraftingPlanningEngines;
import com.moakiee.thunderbolt.core.crafting.planner.ThunderboltV2PlanningEngine;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ExclusivePlanningContextTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void lockedV2AndExplicitVanillaBothOwnTheRouterButInactiveSourcesDoNot() {
        assertFalse(ExclusivePlanningContext.isExclusiveCalculation());
        for (var algorithm : List.of(ThunderboltV2PlanningEngine.ID, CraftingPlanningEngines.VANILLA_ID)) {
            assertEquals("future", ExclusivePlanningContext.calculate(grid(true, algorithm), () -> {
                assertTrue(ExclusivePlanningContext.isExclusiveCalculation());
                return "future";
            }));
            assertFalse(ExclusivePlanningContext.isExclusiveCalculation());
        }
        ExclusivePlanningContext.calculate(grid(false, ThunderboltV2PlanningEngine.ID), () -> {
            assertFalse(ExclusivePlanningContext.isExclusiveCalculation());
            return null;
        });
    }

    @Test
    void nestedUnlockedGridRetainsItsOwnGraphChoiceAndRestoresOuterLock() {
        ExclusivePlanningContext.calculate(grid(true, ThunderboltV2PlanningEngine.ID), () -> {
            assertTrue(ExclusivePlanningContext.isExclusiveCalculation());
            ExclusivePlanningContext.calculate(grid(false, ThunderboltV2PlanningEngine.ID), () -> {
                assertFalse(ExclusivePlanningContext.isExclusiveCalculation());
                return null;
            });
            assertTrue(ExclusivePlanningContext.isExclusiveCalculation());
            return null;
        });
        assertFalse(ExclusivePlanningContext.isExclusiveCalculation());
    }

    @Test
    void failureRestoresTheOuterContextAndDoesNotPoisonTheNextCalculation() {
        var failure = new IllegalStateException("capture failed");
        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> ExclusivePlanningContext.calculate(grid(true, ThunderboltV2PlanningEngine.ID), () -> {
                    throw failure;
                })));
        assertFalse(ExclusivePlanningContext.isExclusiveCalculation());
        ExclusivePlanningContext.calculate(grid(true, ThunderboltV2PlanningEngine.ID), () -> {
            assertThrows(IllegalStateException.class, () -> ExclusivePlanningContext.calculate(null, () -> {
                assertFalse(ExclusivePlanningContext.isExclusiveCalculation());
                throw failure;
            }));
            assertTrue(ExclusivePlanningContext.isExclusiveCalculation());
            return null;
        });
        assertFalse(ExclusivePlanningContext.isExclusiveCalculation());
    }

    @Test
    void asynchronousWorkAndOtherThreadsDoNotInheritTheSynchronousRouterLock() {
        ExclusivePlanningContext.calculate(grid(true, ThunderboltV2PlanningEngine.ID), () -> {
            CompletableFuture.runAsync(() -> assertFalse(ExclusivePlanningContext.isExclusiveCalculation())).join();
            assertTrue(ExclusivePlanningContext.isExclusiveCalculation());
            return null;
        });
    }

    private static IGrid grid(boolean active, ResourceLocation algorithm) {
        var lock = new Lock(active, algorithm);
        var node = (IGridNode) Proxy.newProxyInstance(IGridNode.class.getClassLoader(),
                new Class<?>[] {IGridNode.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getOwner" -> lock;
                    case "getService" -> null;
                    default -> null;
                });
        return (IGrid) Proxy.newProxyInstance(IGrid.class.getClassLoader(), new Class<?>[] {IGrid.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getMachines" -> Set.of();
                    case "getNodes" -> List.of(node);
                    default -> null;
                });
    }

    private record Lock(boolean active, ResourceLocation algorithm) implements ExclusiveCraftingLockSource {
        public boolean ae2lt$isExclusiveLockActive() { return active; }
        public ResourceLocation ae2lt$getExclusiveAlgorithm() { return algorithm; }
        public int ae2lt$getLockCpuPriority() { return 0; }
        public int ae2lt$getLockProviderPriority() { return 0; }
        public void ae2lt$cycleExclusiveAlgorithm() { }
        public void ae2lt$setExclusiveSelection(CraftingAlgorithmSelection selection) { }
    }
}

package com.moakiee.ae2lt.crafting.algorithm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.lang.reflect.Proxy;

import org.junit.jupiter.api.Test;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;

import com.moakiee.thunderbolt.ae2.crafting.CapturedPlanningChoice;
import com.moakiee.thunderbolt.api.crafting.CraftingAlgorithmSelection;
import com.moakiee.thunderbolt.api.crafting.CraftingAlgorithmProvider;
import com.moakiee.thunderbolt.api.crafting.CraftingPlanningEngines;
import com.moakiee.thunderbolt.api.crafting.PlanningChoice;
import com.moakiee.thunderbolt.api.crafting.PlanningRequest;
import com.moakiee.thunderbolt.core.crafting.planner.CpSatPlanningEngine;
import com.moakiee.thunderbolt.core.crafting.planner.ThunderboltV2PlanningEngine;

class ExclusiveCraftingPlanningTest {
    @Test
    void ownedAlgorithmsExposeV2AndCpSat() {
        assertEquals(
                List.of(ThunderboltV2PlanningEngine.ID, CpSatPlanningEngine.ID),
                ExclusiveCraftingPlanning.ownedAlgorithms());
    }

    @Test
    void selectableAlwaysIncludesV2AndVanillaAndCpSatOnlyWhenRegistered() {
        var selectable = ExclusiveCraftingPlanning.selectable();

        assertEquals(ThunderboltV2PlanningEngine.ID, selectable.get(0));
        assertEquals(CraftingPlanningEngines.VANILLA_ID, selectable.get(selectable.size() - 1));
        assertEquals(
                CraftingPlanningEngines.isKnown(CpSatPlanningEngine.ID),
                selectable.contains(CpSatPlanningEngine.ID));
    }

    @Test
    void cycleLocksOneKnownAlgorithmAtATime() {
        var first = ExclusiveCraftingPlanning.cycle(ThunderboltV2PlanningEngine.ID);
        if (CraftingPlanningEngines.isKnown(CpSatPlanningEngine.ID)) {
            assertEquals(CpSatPlanningEngine.ID, first);
            assertEquals(
                    CraftingPlanningEngines.VANILLA_ID,
                    ExclusiveCraftingPlanning.cycle(first));
        } else {
            assertEquals(CraftingPlanningEngines.VANILLA_ID, first);
        }
        assertEquals(
                ThunderboltV2PlanningEngine.ID,
                ExclusiveCraftingPlanning.cycle(CraftingPlanningEngines.VANILLA_ID));
    }

    @Test
    void unknownSelectionFallsBackToV2() {
        assertEquals(
                ThunderboltV2PlanningEngine.ID,
                ExclusiveCraftingPlanning.normalize(new ResourceLocation("ae2lt", "missing")));
        assertEquals(0, ExclusiveCraftingPlanning.displayIndex(null));
        assertEquals(2, ExclusiveCraftingPlanning.displayIndex(CraftingPlanningEngines.VANILLA_ID));
        assertEquals(
                CraftingPlanningEngines.VANILLA_ID,
                ExclusiveCraftingPlanning.algorithmAtDisplayIndex(2));
        assertEquals(
                "ae2lt.tianshu.gui.algorithm.v2",
                ExclusiveCraftingPlanning.translationKey(null));
        assertEquals(
                "ae2lt.tianshu.gui.algorithm.vanilla",
                ExclusiveCraftingPlanning.translationKey(CraftingPlanningEngines.VANILLA_ID));
        var next = ExclusiveCraftingPlanning.cycle(ThunderboltV2PlanningEngine.ID);
        assertEquals(next, ExclusiveCraftingPlanning.cycle(null));
        assertEquals(next, ExclusiveCraftingPlanning.cycle(new ResourceLocation("ae2lt", "missing")));
    }

    @Test
    void exclusiveEngineCandidatesKeepVanillaOnlyAsAConfigureSentinel() {
        var v2 = ExclusiveCraftingPlanning.candidatesForSelected(ThunderboltV2PlanningEngine.ID);
        assertEquals(List.of(
                PlanningChoice.engine(ThunderboltV2PlanningEngine.ID),
                PlanningChoice.VANILLA), v2);
        assertEquals(
                List.of(PlanningChoice.VANILLA),
                ExclusiveCraftingPlanning.candidatesForSelected(CraftingPlanningEngines.VANILLA_ID));
    }

    @Test
    void stripVanillaDropsTheFallbackAfterCapture() {
        var engine = new CapturedPlanningChoice(
                PlanningChoice.engine(ThunderboltV2PlanningEngine.ID),
                ThunderboltV2PlanningEngine.INSTANCE,
                null);
        var stripped = ExclusiveCraftingPlanning.stripVanilla(
                List.of(engine, CapturedPlanningChoice.vanilla()));

        assertEquals(List.of(engine), stripped);
        assertTrue(ExclusiveCraftingPlanning.stripVanilla(
                List.of(CapturedPlanningChoice.vanilla())).isEmpty());
        assertFalse(stripped.get(0).choice().kind() == PlanningChoice.Kind.VANILLA);
    }

    @Test
    void lockSourcesPreferHigherCpuThenProviderPriority() {
        var inactive = new StubLock(false, CraftingPlanningEngines.VANILLA_ID, 100, 100);
        var low = new StubLock(true, CraftingPlanningEngines.VANILLA_ID, 1, 50);
        var highCpu = new StubLock(true, ThunderboltV2PlanningEngine.ID, 2, 0);
        var highProvider = new StubLock(true, CraftingPlanningEngines.VANILLA_ID, 2, 10);

        assertNull(ExclusiveCraftingPlanning.exclusiveAlgorithmFromLockSources(List.of(inactive)));
        assertEquals(
                ThunderboltV2PlanningEngine.ID,
                ExclusiveCraftingPlanning.exclusiveAlgorithmFromLockSources(List.of(low, highCpu)));
        assertEquals(
                CraftingPlanningEngines.VANILLA_ID,
                ExclusiveCraftingPlanning.exclusiveAlgorithmFromLockSources(
                        List.of(highCpu, highProvider)));
        var samePriority = new StubLock(true, CraftingPlanningEngines.VANILLA_ID, 2, 0);
        assertEquals(ThunderboltV2PlanningEngine.ID,
                ExclusiveCraftingPlanning.exclusiveAlgorithmFromLockSources(List.of(highCpu, samePriority)));
        assertEquals(CraftingPlanningEngines.VANILLA_ID,
                ExclusiveCraftingPlanning.exclusiveAlgorithmFromLockSources(List.of(samePriority, highCpu)));
    }

    @Test
    void gridLocksPreserveServicePrecedenceAndFollowLiveTopologyAndSelection() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        var owner = new StubLock(true, CraftingPlanningEngines.VANILLA_ID, 100, 100);
        var service = new StubLock(true, ThunderboltV2PlanningEngine.ID, 2, 0);
        var provider = Proxy.newProxyInstance(CraftingAlgorithmProvider.class.getClassLoader(),
                new Class<?>[] {CraftingAlgorithmProvider.class, ExclusiveCraftingLockSource.class},
                (proxy, method, arguments) -> method.invoke(service, arguments));
        var serviceNode = proxy(IGridNode.class, (proxy, method, arguments) -> switch (method.getName()) {
            case "getService" -> provider;
            case "getOwner" -> owner;
            default -> null;
        });
        var weakerOwner = new StubLock(true, CraftingPlanningEngines.VANILLA_ID, 1, 0);
        var ownerNode = proxy(IGridNode.class, (proxy, method, arguments) -> switch (method.getName()) {
            case "getService" -> null;
            case "getOwner" -> weakerOwner;
            default -> null;
        });
        var nodes = new ArrayList<>(List.of(ownerNode, serviceNode));
        var grid = proxy(IGrid.class, (proxy, method, arguments) -> switch (method.getName()) {
            case "getMachines" -> Set.of();
            case "getNodes" -> nodes;
            default -> null;
        });
        var decision = ExclusiveCraftingPlanning.resolve(grid);
        assertEquals(ThunderboltV2PlanningEngine.ID, decision.algorithm());
        service.algorithm = CraftingPlanningEngines.VANILLA_ID;
        assertEquals(CraftingPlanningEngines.VANILLA_ID, ExclusiveCraftingPlanning.resolve(grid).algorithm());
        assertEquals(ThunderboltV2PlanningEngine.ID, decision.algorithm());
        nodes.remove(1);
        assertEquals(CraftingPlanningEngines.VANILLA_ID, ExclusiveCraftingPlanning.exclusiveAlgorithm(grid));
        nodes.clear();
        assertFalse(ExclusiveCraftingPlanning.resolve(grid).locked());
    }

    @Test
    void nullGridDoesNotLockAnExclusiveAlgorithm() {
        assertNull(ExclusiveCraftingPlanning.exclusiveAlgorithm(null));
        assertFalse(ExclusiveCraftingPlanning.locksExclusiveAlgorithm(null));
        assertFalse(ExclusiveCraftingPlanning.locksExclusiveEngine(null));
        var decision = ExclusiveCraftingPlanning.resolve(null);
        assertFalse(decision.locked());
        assertFalse(decision.engineLocked());
        assertTrue(decision.candidates().isEmpty());
    }

    @Test
    void nodeLessRequesterIsAcceptedOnlyForThisGridsExclusiveV2() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        var lock = new StubLock(true, ThunderboltV2PlanningEngine.ID, 0, 0);
        var service = proxy(ICraftingService.class, (proxy, method, args) -> null);
        var node = proxy(IGridNode.class, (proxy, method, args) -> switch (method.getName()) {
            case "getService" -> null;
            case "getOwner" -> lock;
            default -> null;
        });
        var grid = proxy(IGrid.class, (proxy, method, args) -> switch (method.getName()) {
            case "getMachines" -> Set.of();
            case "getNodes" -> List.of(node);
            case "getCraftingService" -> service;
            default -> null;
        });
        ICraftingSimulationRequester requester = () -> null;
        var request = new PlanningRequest(null, service, null, new TestKey(),
                1L, null, requester);

        assertTrue(ExclusiveCraftingPlanning.acceptsNodeLessV2Request(grid, request));
        assertFalse(ExclusiveCraftingPlanning.acceptsNodeLessV2Request(
                grid, new PlanningRequest(null, proxy(ICraftingService.class,
                        (proxy, method, args) -> null), null,
                        request.output(), 1L, null, requester)));
        lock.algorithm = CraftingPlanningEngines.VANILLA_ID;
        assertFalse(ExclusiveCraftingPlanning.acceptsNodeLessV2Request(grid, request));
        assertFalse(ExclusiveCraftingPlanning.acceptsNodeLessV2Request(null, request));
    }

    private static <T> T proxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(
                type.getClassLoader(), new Class<?>[] {type}, handler));
    }

    private static final class TestKey extends AEKey {
        @Override public AEKeyType getType() { return null; }
        @Override public AEKey dropSecondary() { return this; }
        @Override public CompoundTag toTag() { return new CompoundTag(); }
        @Override public Object getPrimaryKey() { return "v2-test"; }
        @Override public ResourceLocation getId() { return new ResourceLocation("ae2lt", "v2_test"); }
        @Override public void writeToPacket(FriendlyByteBuf data) { }
        @Override protected Component computeDisplayName() { return Component.literal("v2-test"); }
        @Override public void addDrops(long amount, List<ItemStack> drops, Level level, BlockPos pos) { }
    }

    private static final class StubLock implements ExclusiveCraftingLockSource {
        private final boolean active;
        private ResourceLocation algorithm;
        private final int cpu;
        private final int provider;

        private StubLock(boolean active, ResourceLocation algorithm, int cpu, int provider) {
            this.active = active;
            this.algorithm = algorithm;
            this.cpu = cpu;
            this.provider = provider;
        }

        @Override
        public boolean ae2lt$isExclusiveLockActive() {
            return active;
        }

        @Override
        public ResourceLocation ae2lt$getExclusiveAlgorithm() {
            return algorithm;
        }

        @Override
        public int ae2lt$getLockCpuPriority() {
            return cpu;
        }

        @Override
        public int ae2lt$getLockProviderPriority() {
            return provider;
        }

        @Override
        public void ae2lt$cycleExclusiveAlgorithm() {
            algorithm = ExclusiveCraftingPlanning.cycle(algorithm);
        }

        @Override
        public void ae2lt$setExclusiveSelection(CraftingAlgorithmSelection selection) {
            algorithm = selection.algorithmId();
        }
    }
}

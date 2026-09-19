package com.moakiee.ae2lt.crafting.algorithm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import net.minecraft.resources.ResourceLocation;

import com.moakiee.thunderbolt.ae2.crafting.CapturedPlanningChoice;
import com.moakiee.thunderbolt.api.crafting.CraftingAlgorithmSelection;
import com.moakiee.thunderbolt.api.crafting.CraftingPlanningEngines;
import com.moakiee.thunderbolt.api.crafting.PlanningChoice;
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
    }

    @Test
    void nullGridDoesNotLockAnExclusiveAlgorithm() {
        assertNull(ExclusiveCraftingPlanning.exclusiveAlgorithm(null));
        assertFalse(ExclusiveCraftingPlanning.locksExclusiveAlgorithm(null));
        assertFalse(ExclusiveCraftingPlanning.locksExclusiveEngine(null));
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

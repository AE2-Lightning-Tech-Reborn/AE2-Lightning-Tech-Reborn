package com.moakiee.ae2lt.crafting.big;

import static org.junit.jupiter.api.Assertions.*;

import com.moakiee.thunderbolt.api.crafting.PlanningChoice;
import com.moakiee.thunderbolt.core.crafting.planner.CpSatPlanningEngine;
import com.moakiee.thunderbolt.core.crafting.planner.ThunderboltV2PlanningEngine;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;

class BigCraftingPlanningPolicyTest {
    private static final PlanningChoice TIANSHU = PlanningChoice.engine(ThunderboltV2PlanningEngine.ID);
    private static final PlanningChoice CP_SAT = PlanningChoice.engine(CpSatPlanningEngine.ID);
    private static final PlanningChoice OTHER = PlanningChoice.engine(
            ResourceLocation.fromNamespaceAndPath("other", "exact"));

    @Test
    void absentCpuRemovesEveryTianshuEngineAndPreservesOtherPlanners() {
        assertEquals(List.of(OTHER, PlanningChoice.VANILLA), BigCraftingPlanningPolicy.resolve(
                false, List.of(TIANSHU, OTHER, CP_SAT, PlanningChoice.VANILLA)));
        assertFalse(BigCraftingPlanningPolicy.enabled(false, List.of(TIANSHU, PlanningChoice.VANILLA)));
    }

    @Test
    void onlineCpuTakesOverEvenWhenAnotherPlannerHasHigherPriority() {
        assertEquals(List.of(TIANSHU, PlanningChoice.VANILLA), BigCraftingPlanningPolicy.resolve(
                true, List.of(OTHER, TIANSHU, PlanningChoice.VANILLA)));
        assertTrue(BigCraftingPlanningPolicy.enabled(true, List.of(TIANSHU, PlanningChoice.VANILLA)));
    }

    @Test
    void explicitVanillaSelectionDisablesAllTianshuEntryPoints() {
        assertEquals(List.of(PlanningChoice.VANILLA), BigCraftingPlanningPolicy.resolve(
                true, List.of(PlanningChoice.VANILLA)));
        assertFalse(BigCraftingPlanningPolicy.enabled(true, List.of(PlanningChoice.VANILLA)));
    }

    @Test
    void selectedTianshuCpSatEngineRemainsSelected() {
        assertEquals(List.of(CP_SAT, PlanningChoice.VANILLA), BigCraftingPlanningPolicy.resolve(
                true, List.of(CP_SAT, TIANSHU, PlanningChoice.VANILLA)));
        assertTrue(BigCraftingPlanningPolicy.enabled(true, List.of(CP_SAT, PlanningChoice.VANILLA)));
    }

    @Test
    void absentCpuNeverTurnsAnUnrelatedChainIntoTianshuPlanning() {
        var unrelated = List.of(OTHER, PlanningChoice.VANILLA);
        assertEquals(unrelated, BigCraftingPlanningPolicy.resolve(false, unrelated));
        assertFalse(BigCraftingPlanningPolicy.ownsEngine(OTHER.engineId()));
        assertFalse(BigCraftingPlanningPolicy.ownsEngine(null));
    }
}

package com.moakiee.ae2lt.crafting.big;

import com.moakiee.thunderbolt.api.crafting.PlanningChoice;
import com.moakiee.thunderbolt.core.crafting.planner.CpSatPlanningEngine;
import com.moakiee.thunderbolt.core.crafting.planner.ThunderboltV2PlanningEngine;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/** Tianshu planning belongs only to grids with an online Tianshu CPU. */
public final class BigCraftingPlanningPolicy {
    private BigCraftingPlanningPolicy() {}

    public static List<PlanningChoice> resolve(boolean hasTianshu, List<PlanningChoice> choices) {
        if (!hasTianshu) {
            return choices.stream().filter(choice -> !ownsEngine(choice.engineId())).toList();
        }
        // An explicit native-planner selection remains authoritative.
        if (!choices.isEmpty() && choices.getFirst().kind() == PlanningChoice.Kind.VANILLA) {
            return choices;
        }
        var selected = !choices.isEmpty() && ownsEngine(choices.getFirst().engineId())
                ? choices.getFirst() : PlanningChoice.engine(ThunderboltV2PlanningEngine.ID);
        return List.of(selected, PlanningChoice.VANILLA);
    }

    public static boolean enabled(boolean hasTianshu, List<PlanningChoice> choices) {
        return hasTianshu && !choices.isEmpty() && ownsEngine(choices.getFirst().engineId());
    }

    public static boolean ownsEngine(ResourceLocation selectedEngine) {
        return ThunderboltV2PlanningEngine.ID.equals(selectedEngine)
                || CpSatPlanningEngine.ID.equals(selectedEngine);
    }
}

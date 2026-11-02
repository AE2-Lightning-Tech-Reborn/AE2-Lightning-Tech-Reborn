package com.moakiee.ae2lt.mixin.thunderbolt;

import appeng.api.networking.IGrid;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.moakiee.ae2lt.crafting.algorithm.ExclusiveCraftingPlanning;
import com.moakiee.ae2lt.crafting.big.BigCraftingMenus;
import com.moakiee.ae2lt.crafting.big.BigCraftingPlanningPolicy;
import com.moakiee.thunderbolt.api.crafting.PlanningChoice;
import com.moakiee.thunderbolt.core.crafting.algorithm.CraftingPlanningService;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

/** Apply the same CPU-presence boundary to native, long, and dedicated big-order entry points. */
@Mixin(value = CraftingPlanningService.class, remap = false)
public abstract class TianshuPlanningServiceMixin {
    @Shadow @Final private IGrid grid;

    @ModifyReturnValue(method = "resolve", at = @At("RETURN"))
    private List<PlanningChoice> ae2lt$requireTianshuCpu(List<PlanningChoice> choices) {
        // Resolve the same explicit owner as the ordinary AE2 entry before applying the
        // Tianshu-only default. GTL Transfinite locks do not require a Tianshu CPU, and
        // dedicated exact/menu callers must not silently select a different engine.
        var decision = ExclusiveCraftingPlanning.resolve(grid);
        if (decision.locked()) return decision.candidates();
        return BigCraftingPlanningPolicy.resolve(BigCraftingMenus.hasTianshu(grid), choices);
    }
}

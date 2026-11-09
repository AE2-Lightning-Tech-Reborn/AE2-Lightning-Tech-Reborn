package com.moakiee.ae2lt.mixin.gtlcore;

import java.util.concurrent.Future;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.api.stacks.AEKey;
import appeng.me.service.CraftingService;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.moakiee.ae2lt.crafting.algorithm.ExclusiveCraftingPlanning;
import com.moakiee.ae2lt.crafting.algorithm.ExclusivePlanningContext;

/** Keep GTLCore GRAPH on unlocked grids; locked grids must produce plans their selected CPU can run. */
@Pseudo
@Mixin(targets = "org.gtlcore.gtlcore.integration.ae2.graph.CraftingEngineRouter", remap = false)
public abstract class GtlGraphPlanningMixin {
    @Inject(method = "useGraph", at = @At("HEAD"), cancellable = true)
    private static void ae2lt$respectExclusiveAlgorithm(CallbackInfoReturnable<Boolean> cir) {
        if (ExclusivePlanningContext.isExclusiveCalculation()) {
            cir.setReturnValue(false);
        }
    }

    // Long-amount and ORIGINAL-inventory-lock menus call this router directly, before entering
    // CraftingService. Delegate once through the scoped native entry; its useGraph check is then
    // false, so the existing exclusive V2/CP-SAT/vanilla capture runs without recursion.
    @Inject(method = "begin(Lorg/gtlcore/gtlcore/integration/ae2/graph/GtlPatternCatalog;"
            + "Lappeng/api/networking/IGrid;Lappeng/me/service/CraftingService;"
            + "Lnet/minecraft/world/level/Level;Lappeng/api/networking/crafting/ICraftingSimulationRequester;"
            + "Lappeng/api/stacks/AEKey;JLappeng/api/networking/crafting/CalculationStrategy;)"
            + "Ljava/util/concurrent/Future;", at = @At("HEAD"), cancellable = true)
    private static void ae2lt$routeLockedRequest(
            @Coerce Object catalog, IGrid grid, CraftingService service, Level level,
            ICraftingSimulationRequester requester, AEKey what, long amount, CalculationStrategy strategy,
            CallbackInfoReturnable<Future<ICraftingPlan>> cir) {
        if (grid.getCraftingService() == service && ExclusiveCraftingPlanning.locksExclusiveAlgorithm(grid)) {
            cir.setReturnValue(service.beginCraftingCalculation(level, requester, what, amount, strategy));
        }
    }
}

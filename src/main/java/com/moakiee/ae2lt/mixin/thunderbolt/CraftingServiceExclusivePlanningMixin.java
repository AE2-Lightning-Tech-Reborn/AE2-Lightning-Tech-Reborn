package com.moakiee.ae2lt.mixin.thunderbolt;

import java.util.List;
import java.util.concurrent.Future;

import com.llamalad7.mixinextras.sugar.Local;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.api.stacks.AEKey;
import appeng.crafting.CraftingCalculation;
import appeng.me.service.CraftingService;
import net.minecraft.world.level.Level;

import com.moakiee.ae2lt.crafting.algorithm.ExclusiveCraftingPlanning;
import com.moakiee.ae2lt.crafting.algorithm.ExclusivePlanningLock;
import com.moakiee.thunderbolt.ae2.crafting.CraftingPlanningControl;

/**
 * Thunderbolt configures planning immediately before {@code ExecutorService.submit}.
 * Mixin 0.8.5 places higher-priority {@code BEFORE} injectors closer to that invoke,
 * so priority 1100 runs after Thunderbolt's default-1000 configure and can lock one
 * algorithm. Exclusive engine lists still end in VANILLA for Thunderbolt's
 * configure contract; {@link ExclusivePlanningLock} then fails that vanilla
 * candidate closed.
 */
@Mixin(value = CraftingService.class, remap = false, priority = 1100)
public abstract class CraftingServiceExclusivePlanningMixin {
    @Shadow
    @Final
    private IGrid grid;

    @Inject(
            method = "beginCraftingCalculation",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/concurrent/ExecutorService;submit(Ljava/util/concurrent/Callable;)Ljava/util/concurrent/Future;",
                    shift = At.Shift.BEFORE))
    private void ae2lt$lockExclusivePlanning(
            Level level,
            ICraftingSimulationRequester simRequester,
            AEKey what,
            long amount,
            CalculationStrategy strategy,
            CallbackInfoReturnable<Future<ICraftingPlan>> cir,
            @Local CraftingCalculation job) {
        if (!ExclusiveCraftingPlanning.locksExclusiveAlgorithm(this.grid)) {
            return;
        }
        ((CraftingPlanningControl) job).thunderbolt$configurePlanning(
                ExclusiveCraftingPlanning.candidatesForConfigure(this.grid, List.of()),
                this.grid);
        if (job instanceof ExclusivePlanningLock lock) {
            lock.ae2lt$setExclusiveEngine(
                    ExclusiveCraftingPlanning.locksExclusiveEngine(this.grid));
        }
    }
}

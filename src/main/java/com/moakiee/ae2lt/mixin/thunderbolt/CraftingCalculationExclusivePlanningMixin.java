package com.moakiee.ae2lt.mixin.thunderbolt;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import appeng.api.networking.crafting.ICraftingPlan;
import appeng.crafting.CraftingCalculation;

import com.moakiee.ae2lt.crafting.algorithm.ExclusivePlanningLock;

/**
 * Thunderbolt's {@code WrapMethod} on {@code computePlan} is the outer planner
 * router and only calls the original for vanilla. MixinExtras nests higher
 * priority wrappers outside, so this mixin stays at 900: the original vanilla
 * body is skipped for an exclusive engine lock, while Thunderbolt can still
 * run V2 / CP-SAT first.
 */
@Mixin(value = CraftingCalculation.class, remap = false, priority = 900)
public abstract class CraftingCalculationExclusivePlanningMixin implements ExclusivePlanningLock {
    @Unique
    private boolean ae2lt$exclusiveEngine;

    @Override
    public void ae2lt$setExclusiveEngine(boolean lock) {
        this.ae2lt$exclusiveEngine = lock;
    }

    @Override
    public boolean ae2lt$isExclusiveEngine() {
        return this.ae2lt$exclusiveEngine;
    }

    @WrapMethod(method = "computePlan")
    private ICraftingPlan ae2lt$skipVanillaIfExclusive(Operation<ICraftingPlan> original)
            throws InterruptedException {
        if (this.ae2lt$exclusiveEngine) {
            return null;
        }
        return original.call();
    }
}

package com.moakiee.ae2lt.mixin.thunderbolt;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import appeng.api.networking.IGrid;

import com.moakiee.ae2lt.crafting.algorithm.ExclusiveCraftingPlanning;
import com.moakiee.thunderbolt.api.crafting.PlanningRequest;
import com.moakiee.thunderbolt.core.crafting.planner.ThunderboltV2PlanningEngine;

@Mixin(value = ThunderboltV2PlanningEngine.class, remap = false)
public abstract class ThunderboltV2NodeLessRequesterMixin {
    @Inject(method = "check", at = @At("HEAD"), cancellable = true)
    private void ae2lt$acceptNodeLessExclusiveRequest(
            IGrid grid, PlanningRequest request, CallbackInfoReturnable<Boolean> cir) {
        if (ExclusiveCraftingPlanning.acceptsNodeLessV2Request(grid, request)) {
            cir.setReturnValue(true);
        }
    }
}

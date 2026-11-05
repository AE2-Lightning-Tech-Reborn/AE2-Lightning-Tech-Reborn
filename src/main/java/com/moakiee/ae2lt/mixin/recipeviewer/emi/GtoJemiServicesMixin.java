package com.moakiee.ae2lt.mixin.recipeviewer.emi;

import com.moakiee.ae2lt.client.compat.GtoRecipeViewerBootstrap;
import java.util.Set;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Native EMI pages coexist with JEI's transfer, catalyst and layout services. */
@Pseudo
@Mixin(targets = "dev.emi.emi.jemi.JemiUtil", remap = false)
public abstract class GtoJemiServicesMixin {
    @Inject(method = "getHandledMods()Ljava/util/Set;", at = @At("RETURN"), cancellable = true, require = 1)
    private static void ae2lt$restoreJeiCallbacks(CallbackInfoReturnable<Set<String>> ci) {
        ci.setReturnValue(GtoRecipeViewerBootstrap.withJeiServices(ci.getReturnValue()));
    }
}

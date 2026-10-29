package com.moakiee.ae2lt.mixin.recipeviewer.emi;

import java.util.List;
import com.moakiee.ae2lt.client.compat.GtoRecipeViewerBootstrap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Run after GTO's overwrite, including its immutable plugin list. */
@Pseudo
@Mixin(targets = "dev.emi.emi.forge.EmiForgePlatform", remap = false, priority = 900)
public abstract class GtoEmiPluginDiscoveryMixin {
    @Inject(method = "getPlugins", at = @At("RETURN"), cancellable = true, require = 1)
    private static void ae2lt$addPlugin(CallbackInfoReturnable<List<Object>> cir) {
        cir.setReturnValue(GtoRecipeViewerBootstrap.withEmi(cir.getReturnValue()));
    }
}

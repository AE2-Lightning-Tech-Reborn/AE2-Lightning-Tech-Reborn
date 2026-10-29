package com.moakiee.ae2lt.mixin.recipeviewer.jei;

import java.util.List;
import mezz.jei.api.IModPlugin;
import com.moakiee.ae2lt.client.compat.GtoRecipeViewerBootstrap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "mezz.jei.forge.startup.ForgePluginFinder", remap = false, priority = 900)
public abstract class GtoJeiPluginDiscoveryMixin {
    @Inject(method = "getModPlugins", at = @At("RETURN"), cancellable = true, require = 1)
    private static void ae2lt$addPlugin(CallbackInfoReturnable<List<IModPlugin>> cir) {
        cir.setReturnValue(GtoRecipeViewerBootstrap.withJei(cir.getReturnValue()));
    }
}

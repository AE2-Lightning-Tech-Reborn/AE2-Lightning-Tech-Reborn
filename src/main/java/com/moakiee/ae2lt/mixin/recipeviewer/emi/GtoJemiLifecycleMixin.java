package com.moakiee.ae2lt.mixin.recipeviewer.emi;

import com.moakiee.ae2lt.client.compat.GtoRecipeViewerBootstrap;
import dev.emi.emi.api.stack.EmiStackInteraction;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The supported Dev23/Dev25 EMI builds keep this hover provider alive during JEI unload. */
@Pseudo
@Mixin(targets = "dev.emi.emi.jemi.JemiPlugin", remap = false)
public abstract class GtoJemiLifecycleMixin {
    @Shadow public static volatile IJeiRuntime runtime;

    @Inject(method = "lambda$register$5(Lnet/minecraft/client/gui/screens/Screen;II)"
            + "Ldev/emi/emi/api/stack/EmiStackInteraction;", at = @At("HEAD"), cancellable = true, require = 1)
    private static void ae2lt$snapshotRuntime(Screen screen, int x, int y,
            CallbackInfoReturnable<EmiStackInteraction> cir) {
        cir.setReturnValue(GtoRecipeViewerBootstrap.jemiStackAt(runtime, screen, x, y));
    }
}

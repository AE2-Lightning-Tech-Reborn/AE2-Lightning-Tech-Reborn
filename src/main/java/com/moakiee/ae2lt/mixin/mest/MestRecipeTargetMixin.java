package com.moakiee.ae2lt.mixin.mest;

import com.lhy.mest.integration.MestRecipeTransferContext;
import com.lhy.mest.terminal.MESTMenu;
import com.moakiee.ae2lt.integration.mest.client.MestTianshuClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = MestRecipeTransferContext.class, remap = false)
public abstract class MestRecipeTargetMixin {
    @Inject(method = "targetFor(Lcom/lhy/mest/terminal/MESTMenu;)Lcom/lhy/mest/integration/MestRecipeTransferContext$Target;", at = @At("HEAD"), cancellable = true)
    private static void ae2lt$target(MESTMenu menu, CallbackInfoReturnable<MestRecipeTransferContext.Target> cir) {
        var panel = MestTianshuClient.recipePanel(menu);
        if (panel != null) cir.setReturnValue(panel.isEncoding() ? MestRecipeTransferContext.Target.PATTERN_ENCODING : MestRecipeTransferContext.Target.CRAFTING);
    }
    @Inject(method = "bothRecipeModulesVisible(Lcom/lhy/mest/terminal/MESTMenu;)Z", at = @At("HEAD"), cancellable = true)
    private static void ae2lt$singleFocusedTarget(MESTMenu menu, CallbackInfoReturnable<Boolean> cir) {
        if (MestTianshuClient.recipePanel(menu) != null) cir.setReturnValue(false);
    }
}

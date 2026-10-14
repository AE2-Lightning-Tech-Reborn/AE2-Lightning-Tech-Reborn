package com.moakiee.ae2lt.mixin.mest;

import appeng.api.stacks.GenericStack;
import com.lhy.mest.compat.UselessPatternBridge;
import com.lhy.mest.terminal.MESTMenu;
import com.moakiee.ae2lt.integration.mest.client.MestTianshuClient;
import com.moakiee.ae2lt.integration.useless.UselessModCompat;
import com.moakiee.ae2lt.menu.TianshuPatternEncodingTermMenu;
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = UselessPatternBridge.class, remap = false)
public abstract class MestUselessBridgeMixin {
    @Inject(method = "isUselessEntry", at = @At("HEAD"), cancellable = true)
    private static void ae2lt$optionalRecipeClass(Object recipe, CallbackInfoReturnable<Boolean> cir) {
        if (!UselessModCompat.isLoaded()) cir.setReturnValue(false);
    }
    @Inject(method = "encode", at = @At("HEAD"), cancellable = true)
    private static void ae2lt$omni(MESTMenu outer, Object recipe, List<List<GenericStack>> inputs,
            List<GenericStack> outputs, CallbackInfoReturnable<Boolean> cir) {
        var panel = MestTianshuClient.recipePanel(outer);
        if (panel == null || !(panel.source.getMenu() instanceof TianshuPatternEncodingTermMenu menu)) return;
        var pattern = UselessModCompat.encodeViewerRecipe(recipe, menu.getPlayer().level());
        if (pattern.isEmpty()) { cir.setReturnValue(false); return; }
        menu.selectOmniversalPattern(pattern); cir.setReturnValue(true);
    }
}

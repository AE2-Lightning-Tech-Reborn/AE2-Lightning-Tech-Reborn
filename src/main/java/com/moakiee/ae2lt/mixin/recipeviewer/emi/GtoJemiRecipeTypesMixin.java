package com.moakiee.ae2lt.mixin.recipeviewer.emi;

import com.moakiee.ae2lt.client.compat.GtoJemiRecipeTypes;
import java.util.List;
import mezz.jei.api.recipe.RecipeType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** JEMI's showTypes compares UIDs directly; JEI furnace and EMI smelting have different UIDs. */
@Pseudo
@Mixin(targets = "dev.emi.emi.jemi.runtime.JemiRecipesGui", remap = false)
public abstract class GtoJemiRecipeTypesMixin {
    @Inject(method = "showTypes(Ljava/util/List;)V", at = @At("HEAD"), cancellable = true, require = 1)
    private void ae2lt$nativeCategories(List<RecipeType<?>> types, CallbackInfo ci) {
        GtoJemiRecipeTypes.show(types);
        ci.cancel();
    }
}

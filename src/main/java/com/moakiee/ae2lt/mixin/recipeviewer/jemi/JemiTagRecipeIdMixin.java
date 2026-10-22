package com.moakiee.ae2lt.mixin.recipeviewer.jemi;

import mezz.jei.library.plugins.jei.tags.ITagInfoRecipe;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** JEI's tag recipe IDs omit the registry, colliding for matching block and item tags. */
@Pseudo
@Mixin(targets = "dev.emi.emi.jemi.JemiRecipe", remap = false)
public abstract class JemiTagRecipeIdMixin {
    @Shadow public Object recipe;
    @Shadow public ResourceLocation id;

    @Inject(method = "<init>", at = @At("TAIL"), require = 1)
    private void ae2lt$qualifyTagId(CallbackInfo ci) {
        if (recipe instanceof ITagInfoRecipe info && "ae2lt".equals(info.getTag().location().getNamespace())) {
            var tag = info.getTag();
            var registry = tag.registry().location();
            id = new ResourceLocation("jei", "/ae2lt/tag/" + registry.getNamespace() + "/"
                    + registry.getPath() + "/" + tag.location().getPath());
        }
    }
}

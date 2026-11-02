package com.moakiee.ae2lt.mixin.recipeviewer.jei;

import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The parent field is shared by the supported JEI 15 layouts. */
@Pseudo
@Mixin(targets = "mezz.jei.gui.recipes.RecipesGui", remap = false)
public interface JeiRecipesGuiAccessor {
    @Accessor("parentScreen")
    Screen ae2lt$getParentScreen();
}

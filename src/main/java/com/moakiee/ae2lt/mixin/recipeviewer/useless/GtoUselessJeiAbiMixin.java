package com.moakiee.ae2lt.mixin.recipeviewer.useless;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

/** Mark only the audited legacy ingredient classes for LT's resource-gated ABI extension. */
@Pseudo
@Mixin(targets = {"com.sorrowmist.useless.compat.jei.GenericStackJeiIngredientProviders",
        "com.sorrowmist.useless.compat.jei.AEKeyJeiIngredients",
        "com.sorrowmist.useless.compat.jei.AEKeyJeiIngredients$2"}, remap = false)
public abstract class GtoUselessJeiAbiMixin { }

package com.moakiee.ae2lt.machine.overloadfactory.recipe;

import java.util.Collection;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.item.crafting.*;

/** Installs a native RecipeMap, as the data reload does, without a running server. */
final class TestRecipeManager extends RecipeManager {
    TestRecipeManager() { super(RegistryAccess.EMPTY); }
    void replaceRecipes(Collection<? extends RecipeHolder<?>> recipes) {
        try {
            var field = RecipeManager.class.getDeclaredField("recipes");
            field.setAccessible(true);
            field.set(this, RecipeMap.create(new java.util.ArrayList<RecipeHolder<?>>(recipes)));
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
}

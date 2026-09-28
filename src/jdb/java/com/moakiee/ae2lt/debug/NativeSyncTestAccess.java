package com.moakiee.ae2lt.debug;
import java.util.Collection;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.crafting.*;
public final class NativeSyncTestAccess {
    public static RecipeManager manager(ServerLevel level) { return com.moakiee.ae2lt.recipe.compat.LegacyRecipeAccess.manager(level); }
    public static void replaceRecipes(RecipeManager manager, Collection<RecipeHolder<?>> recipes) {
        try { var field = RecipeManager.class.getDeclaredField("recipes"); field.setAccessible(true); field.set(manager, RecipeMap.create(recipes)); }
        catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
}

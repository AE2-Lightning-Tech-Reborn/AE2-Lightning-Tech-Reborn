package com.moakiee.ae2lt.recipe.compat;

import java.util.List;
import java.util.Optional;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

/** Server recipe access preserving the 1.21 service call sites. */
public final class LegacyRecipeAccess {
    private LegacyRecipeAccess() {}

    public static RecipeManager manager(Level level) {
        if (level instanceof ServerLevel serverLevel) return serverLevel.recipeAccess();
        throw new IllegalArgumentException("Full recipes are only available on the server");
    }

    @SuppressWarnings("unchecked")
    public static <R extends Recipe<?>> List<RecipeHolder<R>> recipesOfType(RecipeManager manager, RecipeType<R> type) {
        return manager.getRecipes().stream()
                .filter(holder -> holder.value().getType() == type)
                .map(holder -> (RecipeHolder<R>) holder)
                .toList();
    }

    public static Optional<RecipeHolder<?>> byId(RecipeManager manager, Identifier id) {
        return manager.byKey(ResourceKey.create(Registries.RECIPE, id));
    }
}

package com.moakiee.ae2lt.client.compat;

import dev.emi.emi.api.EmiApi;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.recipe.RecipeType;
import net.minecraft.resources.ResourceLocation;

/** Matches JEI's vanilla recipe identities to the native EMI categories JEMI already uses. */
public final class GtoJemiRecipeTypes {
    private static final Map<ResourceLocation, ResourceLocation> VANILLA = Map.ofEntries(
            Map.entry(RecipeTypes.SMELTING.getUid(), new ResourceLocation("minecraft", "smelting")),
            Map.entry(RecipeTypes.ANVIL.getUid(), new ResourceLocation("minecraft", "anvil_repairing")),
            Map.entry(RecipeTypes.CAMPFIRE_COOKING.getUid(), new ResourceLocation("minecraft", "campfire_cooking")),
            Map.entry(RecipeTypes.COMPOSTING.getUid(), new ResourceLocation("minecraft", "composting")));

    private GtoJemiRecipeTypes() {}

    static ResourceLocation resolve(ResourceLocation jeiId, Predicate<ResourceLocation> registered) {
        if (registered.test(jeiId)) return jeiId;
        var mapped = VANILLA.get(jeiId);
        return mapped != null && registered.test(mapped) ? mapped : jeiId;
    }

    public static void show(List<RecipeType<?>> types) {
        var categories = EmiApi.getRecipeManager().getCategories();
        for (var type : types) {
            var id = resolve(type.getUid(), candidate -> categories.stream().anyMatch(c -> c.getId().equals(candidate)));
            for (var category : categories) {
                if (category.getId().equals(id)) EmiApi.displayRecipeCategory(category);
            }
        }
    }
}

package com.moakiee.ae2lt.machine.lightningassembly.recipe;

import static org.junit.jupiter.api.Assertions.*;

import com.moakiee.ae2lt.machine.lightningchamber.recipe.LightningSimulationIngredient;
import com.moakiee.ae2lt.me.key.LightningKey;
import java.util.LinkedHashMap;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraftforge.common.crafting.conditions.ICondition;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class LightningAssemblyRecipeCacheTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @AfterEach
    void clearCache() {
        LightningAssemblyRecipeService.invalidateSortedRecipeCache();
    }

    @Test
    void refreshesReplacedRecipeEvenWhenAllOrderingFieldsStayTheSame() {
        var manager = new RecipeManager(ICondition.IContext.EMPTY);
        var recipes = new LinkedHashMap<ResourceLocation, LightningAssemblyRecipe>();
        var original = recipe("reload", Items.IRON_INGOT, Items.GOLD_INGOT);
        recipes.put(original.getId(), original);
        var cached = LightningAssemblyRecipeService.getSortedRecipes(manager, recipes);
        assertSame(original, cached.get(0));
        assertSame(cached, LightningAssemblyRecipeService.getSortedRecipes(manager, recipes));

        var replacement = recipe("reload", Items.DIAMOND, Items.EMERALD);
        recipes.put(replacement.getId(), replacement);
        var refreshed = LightningAssemblyRecipeService.getSortedRecipes(manager, recipes);
        assertSame(replacement, refreshed.get(0), "Replaced input/output must not use the old recipe instance");
        assertTrue(refreshed.get(0).getResultStack().is(Items.EMERALD));
        assertTrue(refreshed.get(0).inputs().get(0).ingredient().test(new ItemStack(Items.DIAMOND)));
    }

    @Test
    void refreshesWhenDifferentRecipeIdsHaveTheSameHash() {
        var manager = new RecipeManager(ICondition.IContext.EMPTY);
        var recipes = new LinkedHashMap<ResourceLocation, LightningAssemblyRecipe>();
        var original = recipe("an", Items.IRON_INGOT, Items.GOLD_INGOT);
        var replacement = recipe("c0", Items.DIAMOND, Items.EMERALD);
        assertNotEquals(original.getId(), replacement.getId());
        assertEquals(original.getId().hashCode(), replacement.getId().hashCode());
        recipes.put(original.getId(), original);
        LightningAssemblyRecipeService.getSortedRecipes(manager, recipes);
        recipes.clear();
        recipes.put(replacement.getId(), replacement);
        assertSame(replacement, LightningAssemblyRecipeService.getSortedRecipes(manager, recipes).get(0));
    }

    private static LightningAssemblyRecipe recipe(String path, Item input, Item output) {
        return new LightningAssemblyRecipe(new ResourceLocation("test", path), 0,
                List.of(new LightningSimulationIngredient(Ingredient.of(input), 2)),
                new ItemStack(output), 100, 1, LightningKey.Tier.HIGH_VOLTAGE);
    }
}

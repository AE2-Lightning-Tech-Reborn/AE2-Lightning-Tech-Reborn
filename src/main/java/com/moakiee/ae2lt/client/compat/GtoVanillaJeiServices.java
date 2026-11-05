package com.moakiee.ae2lt.client.compat;

import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.fml.ModList;

/** Restore the JEI services used by LT transfers; GTO's native EMI browser remains in charge. */
public final class GtoVanillaJeiServices {
    private GtoVanillaJeiServices() {}

    public static boolean enabled() {
        var mods = ModList.get();
        return mods != null && mods.isLoaded("gtocore") && mods.isLoaded("emi");
    }

    public static void categories(IRecipeCategoryRegistration registration) {
        if (!enabled()) return;
        // GTO overwrites VanillaPlugin itself and omits FurnaceSmeltingCategory. Re-adding
        // that plugin cannot restore it. Use the installed JEI implementation directly.
        var helpers = registration.getJeiHelpers();
        try {
            var type = mezz.jei.library.plugins.vanilla.cooking.FurnaceSmeltingCategory.class;
            mezz.jei.api.recipe.category.IRecipeCategory<?> category;
            try {
                var constructor = type.getConstructor(mezz.jei.api.helpers.IGuiHelper.class, java.util.List.class);
                var fuels = mezz.jei.library.plugins.vanilla.cooking.fuel.FuelRecipeMaker
                        .getFuelRecipes(helpers.getIngredientManager()).stream()
                        .flatMap(recipe -> recipe.getInputs().stream()).toList();
                category = constructor.newInstance(helpers.getGuiHelper(), fuels);
            } catch (NoSuchMethodException legacy) {
                category = type.getConstructor(mezz.jei.api.helpers.IGuiHelper.class)
                        .newInstance(helpers.getGuiHelper());
            }
            registration.addRecipeCategories(category);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot restore the installed JEI smelting service", failure);
        }
    }

    public static void recipes(IRecipeRegistration registration) {
        if (!enabled() || Minecraft.getInstance().level == null) return;
        registration.addRecipes(RecipeTypes.SMELTING, Minecraft.getInstance().level.getRecipeManager()
                .getAllRecipesFor(net.minecraft.world.item.crafting.RecipeType.SMELTING));
    }

    public static void catalysts(IRecipeCatalystRegistration registration) {
        if (enabled()) registration.addRecipeCatalyst(new ItemStack(Blocks.FURNACE), RecipeTypes.SMELTING);
    }
}

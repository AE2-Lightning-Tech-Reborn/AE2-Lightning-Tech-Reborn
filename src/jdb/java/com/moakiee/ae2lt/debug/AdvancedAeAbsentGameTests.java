package com.moakiee.ae2lt.debug;

import com.moakiee.ae2lt.machine.overloadfactory.recipe.OverloadProcessingRecipeCatalog;
import com.moakiee.ae2lt.registry.ModRecipeTypes;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Contains no references to optional API classes, including in method signatures. */
@GameTestHolder("ae2lt_advancedae")
@PrefixGameTestTemplate(false)
public final class AdvancedAeAbsentGameTests {
    @GameTest(template = "empty")
    public static void nativeRecipesRemainAvailableWithoutAdvancedAe(GameTestHelper helper) {
        helper.assertTrue(!ModList.get().isLoaded("advanced_ae"), "AdvancedAE must be absent for this profile");
        var manager = helper.getLevel().getRecipeManager();
        var nativeRecipes = manager.getAllRecipesFor(ModRecipeTypes.OVERLOAD_PROCESSING_TYPE.get());
        helper.assertTrue(!nativeRecipes.isEmpty(), "native factory recipes were not loaded");
        var catalog = OverloadProcessingRecipeCatalog.recipes(manager);
        helper.assertTrue(catalog.size() == nativeRecipes.size() && catalog.containsAll(nativeRecipes),
                "optional-mod absence changed native recipe availability");
        helper.succeed();
    }
}

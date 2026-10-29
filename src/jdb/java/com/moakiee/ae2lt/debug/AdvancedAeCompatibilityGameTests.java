package com.moakiee.ae2lt.debug;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Runs the reaction contract against the actual selected AdvancedAE release. */
@GameTestHolder("ae2lt_advancedae")
@PrefixGameTestTemplate(false)
public final class AdvancedAeCompatibilityGameTests {
    @GameTest(template = "empty", timeoutTicks = 250)
    public static void borrowedRecipeSettlesEveryResourceOnce(GameTestHelper helper) {
        OverloadFactoryReactionGameTests.completesBorrowedFluidRecipeAndSettlesEveryResourceOnce(helper);
    }

    @GameTest(template = "empty")
    public static void borrowsAllLoadedRecipesWithoutReplacingSources(GameTestHelper helper) {
        OverloadFactoryReactionGameTests.borrowsEveryLoadedReactionWithoutRegisteringCopies(helper);
    }

    @GameTest(template = "empty")
    public static void fluidOutputAndSavedRecipeResumeKeepTheirContract(GameTestHelper helper) {
        OverloadFactoryReactionGameTests.selectsFluidOutputAndResumesUsingOriginalRecipeId(helper);
    }

    @GameTest(template = "empty")
    public static void recipeReloadAndFluidTagsRemainAuthoritative(GameTestHelper helper) {
        OverloadFactoryReactionGameTests.sourceReloadAndFluidTagsRemainAuthoritative(helper);
    }
}

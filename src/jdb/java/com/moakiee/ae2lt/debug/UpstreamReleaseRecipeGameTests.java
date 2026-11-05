package com.moakiee.ae2lt.debug;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Focused entry points for upstream's real Forge recipe and consumption regressions. */
@GameTestHolder("ae2lt_release_recipes")
@PrefixGameTestTemplate(false)
public final class UpstreamReleaseRecipeGameTests {
    @GameTest(templateNamespace = "ae2lt_release_recipes", template = "empty", timeoutTicks = 300)
    public static void stationUsesDiamondsAndAnvilAndConsumesPigmee(GameTestHelper helper) {
        PigmeeBuildingGameTests.otherPigmeeRecipesStillConsumePigmee(helper);
    }

    @GameTest(templateNamespace = "ae2lt_release_recipes", template = "empty", timeoutTicks = 300)
    public static void ioPortUsesSingularityAndRegistersNativeMenu(GameTestHelper helper) {
        OverloadedIOPortGameTests.recipeAndMenuRegistration(helper);
    }
}

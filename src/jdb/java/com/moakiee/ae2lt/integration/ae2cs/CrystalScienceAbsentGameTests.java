package com.moakiee.ae2lt.integration.ae2cs;

import com.moakiee.ae2lt.registry.ModItems;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Ensures the optional mixins and conditional recipe do not require AE2CS. */
@GameTestHolder("ae2lt_cs_absent")
@PrefixGameTestTemplate(false)
public final class CrystalScienceAbsentGameTests {
    @GameTest(template = "empty")
    public static void ltStartsWithoutCrystalScience(GameTestHelper helper) {
        if (ModList.get().isLoaded("ae2cs")) {
            throw new GameTestAssertException("AE2CS must be absent in this smoke fixture");
        }
        if (!ModItems.OVERLOAD_PARALLEL_CARD.isBound()) {
            throw new GameTestAssertException("LT card was not registered without AE2CS");
        }
        if (helper.getLevel().getRecipeManager().byKey(ResourceLocation.parse(
                "ae2lt:lightning_assembly/overload_parallel_card")).isPresent()) {
            throw new GameTestAssertException("CS integration recipe loaded without AE2CS");
        }
        helper.succeed();
    }
}

package com.moakiee.ae2lt.integration.emi;

import com.moakiee.ae2lt.integration.recipeviewer.RecipeDecor;
import com.moakiee.ae2lt.blockentity.CrystalCatalyzerBlockEntity;
import com.moakiee.ae2lt.machine.crystalcatalyzer.recipe.CrystalCatalyzerRecipe;
import com.moakiee.ae2lt.machine.crystalcatalyzer.recipe.Mode;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;

final class EmiCrystalCatalyzerRecipe extends EmiBackedRecipe<CrystalCatalyzerRecipe> {
    private static final ResourceLocation TEXTURE =
            EmiRecipeWidgets.texture("guis/crystal_catalyzer.png");
    private static final int WIDTH = 128;
    private static final int HEIGHT = 104;

    private final EmiStack fluid;

    EmiCrystalCatalyzerRecipe(RecipeHolder<CrystalCatalyzerRecipe> holder) {
        super(AE2LTEmiCategories.CRYSTAL_CATALYZER, holder, WIDTH, HEIGHT);
        fluid = EmiRecipeWidgets.fluid(recipe.fluidInput());
        inputs.add(fluid);
        recipe.catalyst().ifPresent(catalyst ->
                inputs.add(EmiRecipeWidgets.ingredient(catalyst, recipe.catalystCount())));
        outputs.add(EmiStack.of(recipe.getOutputTemplate()));
    }

    @Override
    public void addWidgets(WidgetHolder widgets) {
        EmiRecipeWidgets.addDecor(widgets, EmiRecipeWidgets.decor(widgets)
                .texture(TEXTURE, 0, 0, WIDTH, 62, 22, 14));

        var fluidInput = recipe.fluidInput();
        var fluidSlot = widgets.addTank(fluid, 4, 4, 16, 53, Math.max(1, fluidInput.getAmount()))
                .drawBack(false)
                .appendTooltip(Component.translatable(
                        "jei.ae2lt.crystal_catalyzer.fluid_fixed",
                        fluidInput.getAmount()));
        if (!recipe.isWaterRecipe() || recipe.mode() != Mode.CRYSTAL) {
            fluidSlot.appendTooltip(Component.translatable("jei.ae2lt.crystal_catalyzer.normal_only"));
        }

        if (inputs.size() > 1) {
            int perInstance = Math.max(1, recipe.catalystCount());
            EmiRecipeWidgets.addLargeStackSlot(widgets, inputs.get(1), 34, 16)
                    .drawBack(false)
                    .appendTooltip(Component.translatable("jei.ae2lt.crystal_catalyzer.catalyst_kept"))
                    .appendTooltip(Component.translatable(
                            "jei.ae2lt.crystal_catalyzer.catalyst_parallel",
                            perInstance));
        }

        EmiRecipeWidgets.addLargeStackSlot(widgets, outputs.getFirst(), 95, 16)
                .drawBack(false)
                .recipeContext(this)
                .appendTooltip(Component.translatable(
                        "jei.ae2lt.crystal_catalyzer.output_parallel"));

        // Keep the status lines inside the panel when EMI clamps its height.
        boolean compactText = widgets.getHeight() < HEIGHT;
        int firstLineY = compactText ? 60 : 64;
        int lineSpacing = compactText ? 8 : 10;
        var decor = EmiRecipeWidgets.decor(widgets)
                .horizontalRevealWallClock(TEXTURE, 52, 19, 35, 10, 176, 18,
                        recipe.mode() == Mode.CRYSTAL ? 1_000 : 2_000);
        statusText(decor, Component.translatable(
                "jei.ae2lt.crystal_catalyzer.energy",
                EmiRecipeWidgets.compactEnergy(recipe.energyPerCycle())), firstLineY, compactText);
        statusText(decor, Component.translatable(
                "jei.ae2lt.crystal_catalyzer.time",
                Component.translatable(recipe.mode().translationKey()),
                recipe.mode() == Mode.CRYSTAL ? "1s" : "2s"), firstLineY + lineSpacing, compactText);
        statusText(decor, Component.translatable(
                "jei.ae2lt.crystal_catalyzer.lightning",
                recipe.lightningCost(),
                EmiRecipeWidgets.tierName(recipe.lightningTier())), firstLineY + lineSpacing * 2, compactText);
        statusText(decor, Component.translatable(
                "jei.ae2lt.crystal_catalyzer.matrix_note",
                CrystalCatalyzerBlockEntity.MATRIX_OUTPUT_MULTIPLIER), firstLineY + lineSpacing * 3, compactText);
        EmiRecipeWidgets.addDecor(widgets, decor);
    }

    private static void statusText(RecipeDecor decor, Component text, int y, boolean compact) {
        if (compact) {
            decor.scaledCenteredText(text, y, 8, EmiRecipeWidgets.TEXT_COLOR, 0.8f);
        } else {
            decor.centeredText(text, y, EmiRecipeWidgets.TEXT_COLOR);
        }
    }
}

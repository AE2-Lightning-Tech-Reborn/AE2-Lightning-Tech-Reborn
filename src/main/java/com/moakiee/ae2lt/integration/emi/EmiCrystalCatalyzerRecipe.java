package com.moakiee.ae2lt.integration.emi;

import static com.moakiee.ae2lt.integration.RecipeEnergyFormat.compactEnergy;

import com.moakiee.ae2lt.blockentity.CrystalCatalyzerBlockEntity;
import com.moakiee.ae2lt.machine.crystalcatalyzer.recipe.CrystalCatalyzerRecipe;
import com.moakiee.ae2lt.machine.crystalcatalyzer.recipe.Mode;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

final class EmiCrystalCatalyzerRecipe extends EmiBackedRecipe<CrystalCatalyzerRecipe> {
    private static final ResourceLocation TEXTURE =
            EmiRecipeWidgets.texture("guis/crystal_catalyzer.png");
    private static final int WIDTH = 128;
    private static final int HEIGHT = 104;

    private final EmiStack fluid;

    EmiCrystalCatalyzerRecipe(ResourceLocation id, CrystalCatalyzerRecipe recipe) {
        super(AE2LTEmiCategories.CRYSTAL_CATALYZER, id, recipe, WIDTH, HEIGHT);
        fluid = EmiRecipeWidgets.fluid(recipe.fluidInput());
        inputs.add(fluid);
        recipe.catalyst().ifPresent(catalyst ->
                inputs.add(EmiRecipeWidgets.ingredient(catalyst, recipe.catalystCount())));
        outputs.add(EmiStack.of(recipe.getOutputTemplate()));
    }

    @Override
    public void addWidgets(WidgetHolder widgets) {
        widgets.addTexture(TEXTURE, 0, 0, WIDTH, 62, 22, 14);
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

        int matrixMultiplier = CrystalCatalyzerBlockEntity.MATRIX_OUTPUT_MULTIPLIER;
        EmiRecipeWidgets.addLargeStackSlot(widgets, outputs.get(0), 95, 16)
                .drawBack(false)
                .recipeContext(this)
                .appendTooltip(Component.translatable(
                        "jei.ae2lt.crystal_catalyzer.output_parallel"));

        widgets.addAnimatedTexture(
                TEXTURE,
                52,
                19,
                35,
                10,
                176,
                18,
                recipe.mode() == Mode.CRYSTAL ? 1_000 : 2_000,
                true,
                false,
                false);

        // Keep the status lines inside the panel when EMI clamps its height.
        boolean compactText = widgets.getHeight() < HEIGHT;
        int firstLineY = compactText ? 60 : 64;
        int lineSpacing = compactText ? 8 : 10;

        statusText(
                widgets,
                Component.translatable(
                        "jei.ae2lt.crystal_catalyzer.energy",
                        compactEnergy(recipe.energyPerCycle())),
                firstLineY,
                compactText);
        statusText(
                widgets,
                Component.translatable(
                        "jei.ae2lt.crystal_catalyzer.time",
                        Component.translatable(recipe.mode().translationKey()),
                        recipe.mode() == Mode.CRYSTAL ? "1s" : "2s"),
                firstLineY + lineSpacing,
                compactText);
        statusText(
                widgets,
                Component.translatable(
                        "jei.ae2lt.crystal_catalyzer.lightning",
                        recipe.lightningCost(),
                        EmiRecipeWidgets.tierName(recipe.lightningTier())),
                firstLineY + lineSpacing * 2,
                compactText);
        statusText(
                widgets,
                Component.translatable(
                        "jei.ae2lt.crystal_catalyzer.matrix_note",
                        matrixMultiplier),
                firstLineY + lineSpacing * 3,
                compactText);
    }

    private static void statusText(WidgetHolder widgets, Component text, int y, boolean compact) {
        if (!compact) {
            EmiRecipeWidgets.centeredText(widgets, text, WIDTH / 2, y);
            return;
        }

        widgets.addDrawable(0, y, WIDTH, 8, (graphics, mouseX, mouseY, delta) -> {
            var font = Minecraft.getInstance().font;
            float scale = 0.8f;
            graphics.pose().pushPose();
            graphics.pose().scale(scale, scale, 1);
            int x = Math.round((WIDTH / 2f) / scale - font.width(text) / 2f);
            graphics.drawString(font, text, x, 0, EmiRecipeWidgets.TEXT_COLOR, false);
            graphics.pose().popPose();
        });
    }
}

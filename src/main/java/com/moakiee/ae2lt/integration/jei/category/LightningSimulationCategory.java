package com.moakiee.ae2lt.integration.jei.category;

import com.moakiee.ae2lt.integration.jei.JeiRecipeDecor;
import com.moakiee.ae2lt.integration.recipeviewer.RecipeDecor;
import java.util.Arrays;
import java.util.List;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.client.gui.LargeStackCountRenderer;
import com.moakiee.ae2lt.integration.jei.LargeStackJeiItemRenderer;
import com.moakiee.ae2lt.machine.lightningchamber.recipe.LightningSimulationRecipe;
import com.moakiee.ae2lt.machine.lightningchamber.recipe.LightningSimulationRecipeService;
import com.moakiee.ae2lt.me.key.LightningKey;
import com.moakiee.ae2lt.registry.ModBlocks;

import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;

public class LightningSimulationCategory implements IRecipeCategory<LightningSimulationRecipe> {
    public static final RecipeType<LightningSimulationRecipe> TYPE =
            RecipeType.create(AE2LightningTech.MODID, "lightning_simulation", LightningSimulationRecipe.class);

    private static final ResourceLocation BACKGROUND_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(AE2LightningTech.MODID, "textures/guis/lightning_simulation_room.png");

    // Crop the machine GUI work area without clipping the input slots or reaction chamber border.
    private static final int BACKGROUND_U = 5;
    private static final int BACKGROUND_V = 14;
    private static final int BACKGROUND_WIDTH = 168;
    private static final int BACKGROUND_HEIGHT = 64;
    private static final int WIDTH = BACKGROUND_WIDTH;

    private static final int SLOT_INPUT_X = 39 - BACKGROUND_U;    // 34
    private static final int SLOT_INPUT_Y = 22 - BACKGROUND_V;    // 8
    private static final int SLOT_INPUT_SPACING = 18;
    private static final int SLOT_OUTPUT_X = 119 - BACKGROUND_U;  // 114
    private static final int SLOT_OUTPUT_Y = 41 - BACKGROUND_V;   // 27

    private static final int ENERGY_TEXT_Y = BACKGROUND_HEIGHT + 2;     // 66
    private static final int LIGHTNING_TEXT_Y = BACKGROUND_HEIGHT + 12; // 76
    private static final int SUBSTITUTION_TEXT_Y = BACKGROUND_HEIGHT + 22; // 86
    private static final int HEIGHT = SUBSTITUTION_TEXT_Y + 10;         // 96, with room for the last text line

    private final IDrawable icon;
    private final JeiRecipeDecor<LightningSimulationRecipe> decor = new JeiRecipeDecor<>(LightningSimulationCategory::decor);

    public LightningSimulationCategory(mezz.jei.api.helpers.IGuiHelper guiHelper) {
        this.icon = guiHelper.createDrawableItemStack(ModBlocks.LIGHTNING_SIMULATION_CHAMBER.toStack());
    }

    @Override
    public RecipeType<LightningSimulationRecipe> getRecipeType() {
        return TYPE;
    }

    @Override
    public int getWidth() {
        return WIDTH;
    }

    @Override
    public int getHeight() {
        return HEIGHT;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("jei.ae2lt.lightning_simulation.title");
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, LightningSimulationRecipe recipe, IFocusGroup focuses) {
        for (int index = 0; index < recipe.inputs().size(); index++) {
            var input = recipe.inputs().get(index);
            builder.addSlot(RecipeIngredientRole.INPUT, SLOT_INPUT_X, SLOT_INPUT_Y + index * SLOT_INPUT_SPACING)
                    .setCustomRenderer(VanillaTypes.ITEM_STACK, LargeStackJeiItemRenderer.INSTANCE)
                    .addItemStacks(expandIngredient(input.ingredient(), input.count()))
                    .addRichTooltipCallback((recipeSlotView, tooltip) ->
                            LargeStackCountRenderer.appendCountTooltip(tooltip, input.count()));
        }

        builder.addSlot(RecipeIngredientRole.OUTPUT, SLOT_OUTPUT_X, SLOT_OUTPUT_Y)
                .setCustomRenderer(VanillaTypes.ITEM_STACK, LargeStackJeiItemRenderer.INSTANCE)
                .addItemStack(recipe.getResultStack())
                .addRichTooltipCallback((recipeSlotView, tooltip) ->
                        LargeStackCountRenderer.appendCountTooltip(tooltip, recipe.getResultStack().getCount()));
    }

    @Override
    public void draw(
            LightningSimulationRecipe recipe,
            IRecipeSlotsView recipeSlotsView,
            GuiGraphics guiGraphics,
            double mouseX,
            double mouseY) {
        decor.draw(recipe, guiGraphics, mouseX, mouseY);
    }

    /** Machine art and info text as an LDLib2 decoration (no live process overlay in JEI). */
    private static RecipeDecor decor(LightningSimulationRecipe recipe) {
        var decor = RecipeDecor.of(WIDTH, HEIGHT)
                .texture(BACKGROUND_TEXTURE, 0, 0, BACKGROUND_WIDTH, BACKGROUND_HEIGHT,
                        BACKGROUND_U, BACKGROUND_V)
                .centeredText(Component.translatable(
                        "jei.ae2lt.lightning_simulation.energy",
                        formatCompactEnergy(recipe.totalEnergy())), ENERGY_TEXT_Y, 0x404040)
                .centeredText(Component.translatable(
                        "jei.ae2lt.lightning_simulation.lightning",
                        recipe.lightningCost(),
                        Component.translatable(recipe.lightningTier() == LightningKey.Tier.EXTREME_HIGH_VOLTAGE
                        ? "ae2lt.gui.lightning_simulation.tier.extreme_high_voltage"
                        : "ae2lt.gui.lightning_simulation.tier.high_voltage")), LIGHTNING_TEXT_Y, 0x404040);
        if (recipe.lightningTier() == LightningKey.Tier.EXTREME_HIGH_VOLTAGE) {
            decor.centeredText(Component.translatable(
                    "jei.ae2lt.lightning_simulation.substitution",
                    LightningSimulationRecipeService.getEquivalentHighVoltageCost(
                            recipe.lightningTier(),
                            recipe.lightningCost())), SUBSTITUTION_TEXT_Y, 0x404040);
        }
        return decor;
    }


    private static List<ItemStack> expandIngredient(Ingredient ingredient, int count) {
        return Arrays.stream(ingredient.getItems())
                .map(stack -> stack.copyWithCount(count))
                .toList();
    }

    private static String formatCompactEnergy(long energy) {
        if (energy >= 1_000_000L) {
            return formatCompactValue(energy / 1_000_000D, "m");
        }
        if (energy >= 1_000L) {
            return formatCompactValue(energy / 1_000D, "k");
        }
        return Long.toString(energy);
    }

    private static String formatCompactValue(double value, String suffix) {
        double rounded = Math.round(value * 10.0D) / 10.0D;
        if (Math.abs(rounded - Math.rint(rounded)) < 0.0001D) {
            return Long.toString(Math.round(rounded)) + suffix;
        }
        return rounded + suffix;
    }
}

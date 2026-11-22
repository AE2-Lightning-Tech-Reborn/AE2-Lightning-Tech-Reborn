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
import com.moakiee.ae2lt.machine.lightningassembly.recipe.LightningAssemblyRecipe;
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

public class LightningAssemblyCategory implements IRecipeCategory<LightningAssemblyRecipe> {
    public static final RecipeType<LightningAssemblyRecipe> TYPE =
            RecipeType.create(AE2LightningTech.MODID, "lightning_assembly", LightningAssemblyRecipe.class);

    private static final ResourceLocation BACKGROUND_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(AE2LightningTech.MODID, "textures/guis/lightning_assembly_chamber.png");

    // Crop the machine GUI work area for JEI without clipping the bottom input row or output slot.
    private static final int BACKGROUND_U = 19;
    private static final int BACKGROUND_V = 10;
    private static final int BACKGROUND_WIDTH = 156;
    private static final int BACKGROUND_HEIGHT = 78;

    private static final int WIDTH = BACKGROUND_WIDTH;

    // Category coordinates = GUI coordinates - background offset.
    private static final int INPUT_START_X = 29 - BACKGROUND_U; // 10
    private static final int INPUT_START_Y = 31 - BACKGROUND_V; // 21
    private static final int INPUT_SPACING = 18;
    private static final int OUTPUT_X = 126 - BACKGROUND_U;     // 107
    private static final int OUTPUT_Y = 49 - BACKGROUND_V;      // 39

    private static final int ENERGY_TEXT_Y = BACKGROUND_HEIGHT + 2;   // 80
    private static final int LIGHTNING_TEXT_Y = BACKGROUND_HEIGHT + 12; // 90
    private static final int HEIGHT = LIGHTNING_TEXT_Y + 10;          // 100, with room for the last text line

    private final IDrawable icon;
    private final JeiRecipeDecor<LightningAssemblyRecipe> decor = new JeiRecipeDecor<>(LightningAssemblyCategory::decor);

    public LightningAssemblyCategory(mezz.jei.api.helpers.IGuiHelper guiHelper) {
        this.icon = guiHelper.createDrawableItemStack(ModBlocks.LIGHTNING_ASSEMBLY_CHAMBER.toStack());
    }

    @Override
    public RecipeType<LightningAssemblyRecipe> getRecipeType() {
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
        return Component.translatable("jei.ae2lt.lightning_assembly.title");
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, LightningAssemblyRecipe recipe, IFocusGroup focuses) {
        for (int index = 0; index < recipe.inputs().size() && index < 9; index++) {
            var input = recipe.inputs().get(index);
            int col = index % 3;
            int row = index / 3;
            builder.addSlot(
                            RecipeIngredientRole.INPUT,
                            INPUT_START_X + col * INPUT_SPACING,
                            INPUT_START_Y + row * INPUT_SPACING)
                    .setCustomRenderer(VanillaTypes.ITEM_STACK, LargeStackJeiItemRenderer.INSTANCE)
                    .addItemStacks(expandIngredient(input.ingredient(), input.count()))
                    .addRichTooltipCallback((recipeSlotView, tooltip) ->
                            LargeStackCountRenderer.appendCountTooltip(tooltip, input.count()));
        }

        builder.addSlot(RecipeIngredientRole.OUTPUT, OUTPUT_X, OUTPUT_Y)
                .setCustomRenderer(VanillaTypes.ITEM_STACK, LargeStackJeiItemRenderer.INSTANCE)
                .addItemStack(recipe.getResultStack())
                .addRichTooltipCallback((recipeSlotView, tooltip) ->
                        LargeStackCountRenderer.appendCountTooltip(tooltip, recipe.getResultStack().getCount()));
    }

    @Override
    public void draw(
            LightningAssemblyRecipe recipe,
            IRecipeSlotsView recipeSlotsView,
            GuiGraphics guiGraphics,
            double mouseX,
            double mouseY) {
        decor.draw(recipe, guiGraphics, mouseX, mouseY);
    }

    /** Machine art and info text as an LDLib2 decoration (no live process overlay in JEI). */
    private static RecipeDecor decor(LightningAssemblyRecipe recipe) {
        return RecipeDecor.of(WIDTH, HEIGHT)
                .texture(BACKGROUND_TEXTURE, 0, 0, BACKGROUND_WIDTH, BACKGROUND_HEIGHT,
                        BACKGROUND_U, BACKGROUND_V)
                .centeredText(Component.translatable(
                        "jei.ae2lt.lightning_assembly.energy",
                        formatCompactEnergy(recipe.totalEnergy())), ENERGY_TEXT_Y, 0x404040)
                .centeredText(Component.translatable(
                        "jei.ae2lt.lightning_assembly.lightning",
                        recipe.lightningCost(),
                        Component.translatable(recipe.lightningTier() == LightningKey.Tier.EXTREME_HIGH_VOLTAGE
                        ? "ae2lt.gui.lightning_simulation.tier.extreme_high_voltage"
                        : "ae2lt.gui.lightning_simulation.tier.high_voltage")), LIGHTNING_TEXT_Y, 0x404040);
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

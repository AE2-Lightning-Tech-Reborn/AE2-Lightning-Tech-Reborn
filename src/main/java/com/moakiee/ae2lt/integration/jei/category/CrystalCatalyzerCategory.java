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
import com.moakiee.ae2lt.blockentity.CrystalCatalyzerBlockEntity;
import com.moakiee.ae2lt.client.gui.LargeStackCountRenderer;
import com.moakiee.ae2lt.integration.jei.LargeStackJeiItemRenderer;
import com.moakiee.ae2lt.machine.crystalcatalyzer.recipe.CrystalCatalyzerRecipe;
import com.moakiee.ae2lt.machine.crystalcatalyzer.recipe.Mode;
import com.moakiee.ae2lt.me.key.LightningKey;
import com.moakiee.ae2lt.registry.ModBlocks;

import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.neoforge.NeoForgeTypes;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;

public class CrystalCatalyzerCategory implements IRecipeCategory<CrystalCatalyzerRecipe> {
    public static final RecipeType<CrystalCatalyzerRecipe> TYPE =
            RecipeType.create(AE2LightningTech.MODID, "crystal_catalyzer", CrystalCatalyzerRecipe.class);

    private static final ResourceLocation BACKGROUND_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(AE2LightningTech.MODID, "textures/guis/crystal_catalyzer.png");

    // 机器 GUI 坐标:流体腔 (26,18)+16×53,催化剂槽 (56,30),矩阵槽 (84,54),
    // 进度条 (74,33)+22×16,产物槽 (117,30),能量条 (140,30)+6×18。
    // 从 (22,14) 起裁剪 128×62,保留水箱/电池外框的 1~2px 装饰边。
    private static final int BACKGROUND_U = 22;
    private static final int BACKGROUND_V = 14;
    private static final int BACKGROUND_WIDTH = 128;
    private static final int BACKGROUND_HEIGHT = 62;
    private static final int WIDTH = BACKGROUND_WIDTH;


    // category 坐标 = GUI 坐标 − 背景偏移
    private static final int FLUID_X = 26 - BACKGROUND_U;   // 4
    private static final int FLUID_Y = 18 - BACKGROUND_V;   // 4
    private static final int FLUID_WIDTH = 16;
    private static final int FLUID_HEIGHT = 53;

    private static final int CATALYST_X = 56 - BACKGROUND_U; // 34
    private static final int CATALYST_Y = 30 - BACKGROUND_V; // 16
    private static final int OUTPUT_X = 117 - BACKGROUND_U;  // 95
    private static final int OUTPUT_Y = 30 - BACKGROUND_V;   // 16

    private static final int PROCESS_X = 74 - BACKGROUND_U;  // 52
    private static final int PROCESS_Y = 33 - BACKGROUND_V;  // 19
    private static final int PROCESS_OVERLAY_U = 176;
    private static final int PROCESS_OVERLAY_V = 18;
    private static final int PROCESS_OVERLAY_WIDTH = 35;
    private static final int PROCESS_OVERLAY_HEIGHT = 10;
    private static final long PROCESS_CYCLE_MS_CRYSTAL = 1_000L;
    private static final long PROCESS_CYCLE_MS_DUST = 2_000L;

    private static final int ENERGY_TEXT_Y = BACKGROUND_HEIGHT + 2;       // 64
    private static final int TIME_TEXT_Y = BACKGROUND_HEIGHT + 12;       // 74
    private static final int LIGHTNING_TEXT_Y = BACKGROUND_HEIGHT + 22;  // 84
    private static final int MATRIX_TEXT_Y = BACKGROUND_HEIGHT + 32;    // 94
    private static final int HEIGHT = MATRIX_TEXT_Y + 10;               // 104

    private final IDrawable icon;
    private final JeiRecipeDecor<CrystalCatalyzerRecipe> decor = new JeiRecipeDecor<>(CrystalCatalyzerCategory::decor);

    public CrystalCatalyzerCategory(mezz.jei.api.helpers.IGuiHelper guiHelper) {
        this.icon = guiHelper.createDrawableItemStack(ModBlocks.CRYSTAL_CATALYZER.toStack());
    }

    @Override
    public RecipeType<CrystalCatalyzerRecipe> getRecipeType() {
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
        return Component.translatable("jei.ae2lt.crystal_catalyzer.title");
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, CrystalCatalyzerRecipe recipe, IFocusGroup focuses) {
        var fluid = recipe.fluidInput();
        int fluidDisplayCapacity = Math.max(1, fluid.getAmount());
        builder.addSlot(RecipeIngredientRole.INPUT, FLUID_X, FLUID_Y)
                .setFluidRenderer(fluidDisplayCapacity, false, FLUID_WIDTH, FLUID_HEIGHT)
                .addIngredient(NeoForgeTypes.FLUID_STACK, fluid)
                .addRichTooltipCallback((slotView, tooltip) -> {
                    tooltip.add(Component.translatable("jei.ae2lt.crystal_catalyzer.fluid_fixed", fluid.getAmount()));
                    if (!recipe.isWaterRecipe() || recipe.mode() != Mode.CRYSTAL) {
                        tooltip.add(Component.translatable("jei.ae2lt.crystal_catalyzer.normal_only"));
                    }
                });

        recipe.catalyst().ifPresent(catalyst -> {
            int perInstance = Math.max(1, recipe.catalystCount());
            builder.addSlot(RecipeIngredientRole.INPUT, CATALYST_X, CATALYST_Y)
                    .setCustomRenderer(VanillaTypes.ITEM_STACK, LargeStackJeiItemRenderer.INSTANCE)
                    .addItemStacks(expandIngredient(catalyst, perInstance))
                    .addRichTooltipCallback((recipeSlotView, tooltip) -> {
                        LargeStackCountRenderer.appendCountTooltip(tooltip, perInstance);
                        tooltip.add(Component.translatable("jei.ae2lt.crystal_catalyzer.catalyst_kept"));
                        tooltip.add(Component.translatable(
                                "jei.ae2lt.crystal_catalyzer.catalyst_parallel",
                                perInstance));
                    });
        });

        var baseOutput = recipe.getOutputTemplate();
        int baseCount = baseOutput.getCount();
        builder.addSlot(RecipeIngredientRole.OUTPUT, OUTPUT_X, OUTPUT_Y)
                .setCustomRenderer(VanillaTypes.ITEM_STACK, LargeStackJeiItemRenderer.INSTANCE)
                .addItemStack(baseOutput)
                .addRichTooltipCallback((recipeSlotView, tooltip) -> {
                    LargeStackCountRenderer.appendCountTooltip(tooltip, baseCount);
                    tooltip.add(Component.translatable(
                            "jei.ae2lt.crystal_catalyzer.output_parallel"));
                });
    }

    @Override
    public void draw(
            CrystalCatalyzerRecipe recipe,
            IRecipeSlotsView recipeSlotsView,
            GuiGraphics guiGraphics,
            double mouseX,
            double mouseY) {
        decor.draw(recipe, guiGraphics, mouseX, mouseY);
    }

    /** Machine art, looping process arrow and info text as an LDLib2 decoration. */
    private static RecipeDecor decor(CrystalCatalyzerRecipe recipe) {
        String timeStr = recipe.mode() == Mode.CRYSTAL ? "1s" : "2s";
        return RecipeDecor.of(WIDTH, HEIGHT)
                .texture(BACKGROUND_TEXTURE, 0, 0, BACKGROUND_WIDTH, BACKGROUND_HEIGHT,
                        BACKGROUND_U, BACKGROUND_V)
                .horizontalReveal(BACKGROUND_TEXTURE, PROCESS_X, PROCESS_Y,
                        PROCESS_OVERLAY_WIDTH, PROCESS_OVERLAY_HEIGHT, PROCESS_OVERLAY_U, PROCESS_OVERLAY_V,
                        recipe.mode() == Mode.CRYSTAL ? PROCESS_CYCLE_MS_CRYSTAL : PROCESS_CYCLE_MS_DUST)
                .centeredText(Component.translatable(
                        "jei.ae2lt.crystal_catalyzer.energy",
                        formatCompactEnergy(recipe.energyPerCycle())), ENERGY_TEXT_Y, 0x404040)
                .centeredText(Component.translatable(
                        "jei.ae2lt.crystal_catalyzer.time",
                        Component.translatable(recipe.mode().translationKey()), timeStr), TIME_TEXT_Y, 0x404040)
                .centeredText(Component.translatable(
                        "jei.ae2lt.crystal_catalyzer.lightning",
                        recipe.lightningCost(),
                        Component.translatable(recipe.lightningTier() == LightningKey.Tier.EXTREME_HIGH_VOLTAGE
                        ? "ae2lt.gui.lightning_simulation.tier.extreme_high_voltage"
                        : "ae2lt.gui.lightning_simulation.tier.high_voltage")), LIGHTNING_TEXT_Y, 0x404040)
                .centeredText(Component.translatable(
                        "jei.ae2lt.crystal_catalyzer.matrix_note",
                        CrystalCatalyzerBlockEntity.MATRIX_OUTPUT_MULTIPLIER), MATRIX_TEXT_Y, 0x404040);
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

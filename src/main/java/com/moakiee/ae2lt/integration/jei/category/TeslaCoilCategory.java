package com.moakiee.ae2lt.integration.jei.category;

import com.moakiee.ae2lt.integration.jei.JeiRecipeDecor;
import com.moakiee.ae2lt.integration.recipeviewer.RecipeDecor;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.client.gui.LargeStackCountRenderer;
import com.moakiee.ae2lt.integration.jei.LargeStackJeiItemRenderer;
import com.moakiee.ae2lt.machine.teslacoil.TeslaCoilMode;
import com.moakiee.ae2lt.registry.ModBlocks;
import com.moakiee.ae2lt.registry.ModItems;

import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;

public class TeslaCoilCategory implements IRecipeCategory<TeslaCoilCategory.Page> {
    public static final RecipeType<Page> TYPE =
            RecipeType.create(AE2LightningTech.MODID, "tesla_coil", Page.class);

    private static final int WIDTH = 150;
    private static final int HEIGHT = 90;

    private static final int ROW_Y = 8;
    private static final int INPUT_X = 12;
    private static final int ARROW_X = 54;
    private static final int ARROW_Y = ROW_Y + 3;
    private static final int ARROW_W = 35;
    private static final int ARROW_H = 10;
    private static final int OUTPUT_X = 118;
    private static final int OUTPUT_Y = ROW_Y;
    private static final int ICON_SIZE = 16;
    private static final int ICON_FRAMES = 6;
    private static final long ICON_FRAME_MS = 100L;
    private static final long PROCESS_CYCLE_MS = 1500L;

    private static final int TEXT_COLOR = 0x404040;
    private static final int[] TEXT_LINES = {34, 46, 58, 70};

    private static final ResourceLocation ARROW_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(AE2LightningTech.MODID, "textures/guis/crystal_catalyzer.png");
    private static final int ARROW_U = 176;
    private static final int ARROW_V = 18;

    private static final ResourceLocation HV_LIGHTNING_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(AE2LightningTech.MODID, "textures/item/high_voltage_lightning.png");
    private static final ResourceLocation EHV_LIGHTNING_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(AE2LightningTech.MODID, "textures/item/extreme_high_voltage_lightning.png");

    private final IDrawable icon;
    private final JeiRecipeDecor<Page> decor = new JeiRecipeDecor<>(TeslaCoilCategory::decor);

    public TeslaCoilCategory(mezz.jei.api.helpers.IGuiHelper guiHelper) {
        this.icon = guiHelper.createDrawableItemStack(ModBlocks.TESLA_COIL.toStack());
    }

    @Override
    public RecipeType<Page> getRecipeType() {
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
        return Component.translatable("jei.ae2lt.tesla_coil.title");
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, Page page, IFocusGroup focuses) {
        TeslaCoilMode mode = page.mode;
        if (mode == TeslaCoilMode.HIGH_VOLTAGE) {
            int dustCount = Math.max(1, mode.requiredDust());
            builder.addSlot(RecipeIngredientRole.INPUT, INPUT_X, ROW_Y)
                    .setCustomRenderer(VanillaTypes.ITEM_STACK, LargeStackJeiItemRenderer.INSTANCE)
                    .addItemStack(new ItemStack(ModItems.OVERLOAD_CRYSTAL_DUST.get(), dustCount))
                    .addRichTooltipCallback((slotView, tooltip) ->
                            LargeStackCountRenderer.appendCountTooltip(tooltip, dustCount));
        }
    }

    @Override
    public void draw(
            Page page,
            IRecipeSlotsView recipeSlotsView,
            GuiGraphics guiGraphics,
            double mouseX,
            double mouseY) {
        decor.draw(page, guiGraphics, mouseX, mouseY);
    }

    /** Looping arrow, animated lightning output icon and info text as an LDLib2 decoration. */
    private static RecipeDecor decor(Page page) {
        TeslaCoilMode mode = page.mode;
        return RecipeDecor.of(WIDTH, HEIGHT)
                .horizontalReveal(ARROW_TEXTURE, ARROW_X, ARROW_Y, ARROW_W, ARROW_H, ARROW_U, ARROW_V, PROCESS_CYCLE_MS)
                .frames(mode == TeslaCoilMode.EXTREME_HIGH_VOLTAGE ? EHV_LIGHTNING_TEXTURE : HV_LIGHTNING_TEXTURE,
                        OUTPUT_X, OUTPUT_Y, ICON_SIZE, ICON_FRAMES, ICON_FRAME_MS)
                .centeredText(Component.translatable(
                        "jei.ae2lt.tesla_coil.mode_label",
                        Component.translatable(mode.translationKey())), TEXT_LINES[0], TEXT_COLOR)
                .centeredText(() -> Component.translatable(
                        "jei.ae2lt.tesla_coil.energy",
                        formatCompactEnergy(mode.totalEnergy())), TEXT_LINES[1], TEXT_COLOR)
                .centeredText(() -> mode == TeslaCoilMode.HIGH_VOLTAGE
                                ? Component.translatable("jei.ae2lt.tesla_coil.consume_dust", mode.requiredDust())
                                : Component.translatable("jei.ae2lt.tesla_coil.consume_hv", mode.requiredHighVoltage()),
                        TEXT_LINES[2], TEXT_COLOR)
                .centeredText(Component.translatable(
                        mode == TeslaCoilMode.EXTREME_HIGH_VOLTAGE
                                ? "jei.ae2lt.tesla_coil.output_ehv"
                                : "jei.ae2lt.tesla_coil.output_hv"), TEXT_LINES[3], TEXT_COLOR);
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

    public enum Page {
        HIGH_VOLTAGE(TeslaCoilMode.HIGH_VOLTAGE),
        EXTREME_HIGH_VOLTAGE(TeslaCoilMode.EXTREME_HIGH_VOLTAGE);

        public final TeslaCoilMode mode;

        Page(TeslaCoilMode mode) {
            this.mode = mode;
        }
    }
}

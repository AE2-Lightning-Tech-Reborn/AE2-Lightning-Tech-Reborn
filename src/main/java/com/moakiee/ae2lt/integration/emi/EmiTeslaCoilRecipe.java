package com.moakiee.ae2lt.integration.emi;

import com.moakiee.ae2lt.machine.teslacoil.TeslaCoilMode;
import com.moakiee.ae2lt.me.key.LightningKey;
import com.moakiee.ae2lt.registry.ModItems;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.BasicEmiRecipe;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

final class EmiTeslaCoilRecipe extends BasicEmiRecipe {
    private static final ResourceLocation ARROW_TEXTURE =
            EmiRecipeWidgets.texture("guis/crystal_catalyzer.png");
    private static final int WIDTH = 150;

    private final TeslaCoilMode mode;

    private EmiTeslaCoilRecipe(TeslaCoilMode mode) {
        super(
                AE2LTEmiCategories.TESLA_COIL,
                EmiRecipeWidgets.syntheticId("tesla_coil/" + mode.getSerializedName()),
                WIDTH,
                90);
        this.mode = mode;
        if (mode == TeslaCoilMode.HIGH_VOLTAGE) {
            inputs.add(EmiStack.of(
                    ModItems.OVERLOAD_CRYSTAL_DUST.get(),
                    Math.max(1, mode.requiredDust())));
        }
    }

    static void registerAll(EmiRegistry registry) {
        for (TeslaCoilMode mode : TeslaCoilMode.values()) {
            registry.addRecipe(new EmiTeslaCoilRecipe(mode));
        }
    }

    @Override
    public void addWidgets(WidgetHolder widgets) {
        if (!inputs.isEmpty()) {
            EmiRecipeWidgets.addLargeStackSlot(widgets, inputs.getFirst(), 12, 8);
        }
        EmiRecipeWidgets.addDecor(widgets, EmiRecipeWidgets.decor(widgets)
                .horizontalRevealWallClock(ARROW_TEXTURE, 54, 11, 35, 10, 176, 18, 1_500)
                .lightningIcon(118, 8, mode == TeslaCoilMode.EXTREME_HIGH_VOLTAGE
                        ? LightningKey.EXTREME_HIGH_VOLTAGE : LightningKey.HIGH_VOLTAGE)
                .centeredText(Component.translatable(
                        "jei.ae2lt.tesla_coil.mode_label",
                        Component.translatable(mode.translationKey())), 34, EmiRecipeWidgets.TEXT_COLOR)
                .centeredText(Component.translatable(
                        "jei.ae2lt.tesla_coil.energy",
                        EmiRecipeWidgets.compactEnergy(mode.totalEnergy())), 46, EmiRecipeWidgets.TEXT_COLOR)
                .centeredText(Component.translatable(
                        mode == TeslaCoilMode.HIGH_VOLTAGE
                                ? "jei.ae2lt.tesla_coil.consume_dust"
                                : "jei.ae2lt.tesla_coil.consume_hv",
                        mode == TeslaCoilMode.HIGH_VOLTAGE
                                ? mode.requiredDust()
                                : mode.requiredHighVoltage()), 58, EmiRecipeWidgets.TEXT_COLOR)
                .centeredText(Component.translatable(mode == TeslaCoilMode.EXTREME_HIGH_VOLTAGE
                        ? "jei.ae2lt.tesla_coil.output_ehv"
                        : "jei.ae2lt.tesla_coil.output_hv"), 70, EmiRecipeWidgets.TEXT_COLOR));
    }

    @Override
    public boolean supportsRecipeTree() {
        return false;
    }
}

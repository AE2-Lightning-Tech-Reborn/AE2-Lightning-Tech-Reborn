package com.moakiee.ae2lt.integration.emi;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.integration.recipeviewer.RecipeDecor;
import com.moakiee.ae2lt.me.key.LightningKey;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.SlotWidget;
import dev.emi.emi.api.widget.WidgetHolder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.fluids.FluidStack;

final class EmiRecipeWidgets {
    static final int TEXT_COLOR = 0x404040;

    private EmiRecipeWidgets() {
    }

    static ResourceLocation texture(String path) {
        return ResourceLocation.fromNamespaceAndPath(AE2LightningTech.MODID, "textures/" + path);
    }

    static ResourceLocation syntheticId(String path) {
        return ResourceLocation.fromNamespaceAndPath(AE2LightningTech.MODID, "emi/" + path);
    }

    static EmiIngredient ingredient(Ingredient ingredient, long count) {
        return EmiIngredient.of(ingredient, count);
    }

    static EmiStack fluid(FluidStack stack) {
        return EmiStack.of(stack.getFluid(), stack.getComponentsPatch(), stack.getAmount());
    }

    /** Page decoration (machine art, process overlay, captions) sized to the widget holder. */
    static RecipeDecor decor(WidgetHolder widgets) {
        return RecipeDecor.forEmi(widgets.getWidth(), widgets.getHeight());
    }

    /** Paints LDLib2 textures at EMI's native widget and buffer boundaries. */
    static void addDecor(WidgetHolder widgets, RecipeDecor decor) {
        for (var drawable : decor.drawables()) {
            widgets.addDrawable(drawable.x(), drawable.y(), drawable.width(), drawable.height(),
                    (graphics, mouseX, mouseY, delta) -> drawable.texture().draw(
                            graphics, mouseX, mouseY, 0, 0, drawable.width(), drawable.height(), delta));
        }
    }

    static void addFillingArrow(WidgetHolder widgets, int x, int y, int millis) {
        widgets.addFillingArrow(x, y, millis);
    }

    static SlotWidget addLargeStackSlot(
            WidgetHolder widgets, EmiIngredient ingredient, int x, int y) {
        return widgets.add(new EmiLargeStackSlotWidget(ingredient, x - 1, y - 1));
    }

    static SlotWidget addSlot(
            WidgetHolder widgets, EmiIngredient ingredient, int x, int y) {
        return widgets.addSlot(ingredient, x - 1, y - 1);
    }

    static Component tierName(LightningKey.Tier tier) {
        return Component.translatable(tier == LightningKey.Tier.EXTREME_HIGH_VOLTAGE
                ? "ae2lt.gui.lightning_simulation.tier.extreme_high_voltage"
                : "ae2lt.gui.lightning_simulation.tier.high_voltage");
    }

    static String compactEnergy(long energy) {
        if (energy >= 1_000_000L) {
            return compactValue(energy / 1_000_000D, "m");
        }
        if (energy >= 1_000L) {
            return compactValue(energy / 1_000D, "k");
        }
        return Long.toString(energy);
    }

    static String processTime(int ticks) {
        double seconds = ticks / 20.0D;
        if (seconds == Math.floor(seconds)) {
            return (int) seconds + "s";
        }
        return String.format(java.util.Locale.ROOT, "%.1fs", seconds);
    }

    private static String compactValue(double value, String suffix) {
        double rounded = Math.round(value * 10.0D) / 10.0D;
        if (Math.abs(rounded - Math.rint(rounded)) < 0.0001D) {
            return Long.toString(Math.round(rounded)) + suffix;
        }
        return rounded + suffix;
    }
}

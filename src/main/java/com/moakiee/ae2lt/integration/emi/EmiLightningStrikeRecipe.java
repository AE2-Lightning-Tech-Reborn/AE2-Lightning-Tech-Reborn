package com.moakiee.ae2lt.integration.emi;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import com.moakiee.ae2lt.integration.recipeviewer.multiblock.RotatingStructurePreview;
import com.moakiee.ae2lt.lightning.strike.LightningStrikeRecipe;
import com.moakiee.ae2lt.lightning.strike.StructureRequirement;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.Block;

final class EmiLightningStrikeRecipe extends EmiBackedRecipe<LightningStrikeRecipe> {
    private static final int WIDTH = 178;
    private static final int MATERIALS_X = 104;
    private static final int MATERIALS_Y = 50;

    private final Map<Block, Integer> blockCounts = new LinkedHashMap<>();
    private final Map<Block, Boolean> blockConsumes = new HashMap<>();

    EmiLightningStrikeRecipe(RecipeHolder<LightningStrikeRecipe> holder) {
        super(AE2LTEmiCategories.LIGHTNING_STRIKE, holder, WIDTH, 110);
        inputs.add(EmiStack.of(recipe.centerInput()));
        outputs.add(EmiStack.of(recipe.centerOutput()));
        for (StructureRequirement requirement : recipe.requirements()) {
            blockCounts.merge(requirement.block(), 1, Integer::sum);
            blockConsumes.merge(requirement.block(), requirement.consume(), (a, b) -> a || b);
        }
        blockCounts.forEach((block, count) -> {
            if (blockConsumes.getOrDefault(block, false)) {
                inputs.add(EmiStack.of(block, count));
            } else {
                catalysts.add(EmiStack.of(block, count));
            }
        });
    }

    @Override
    public void addWidgets(WidgetHolder widgets) {
        widgets.addText(
                Component.translatable(recipe.requiresNaturalLightning()
                                ? "jei.ae2lt.lightning_strike.natural_only"
                                : "jei.ae2lt.lightning_strike.any_lightning")
                        .withStyle(recipe.requiresNaturalLightning()
                                ? ChatFormatting.DARK_PURPLE
                                : ChatFormatting.DARK_AQUA),
                4,
                2,
                EmiRecipeWidgets.TEXT_COLOR,
                false);
        widgets.addText(
                Component.translatable("jei.ae2lt.lightning_strike.materials"),
                MATERIALS_X,
                38,
                EmiRecipeWidgets.TEXT_COLOR,
                false);

        var preview = RotatingStructurePreview.lightningStrike(recipe, 96, 92);
        widgets.add(new dev.emi.emi.api.widget.Widget() {
            private final dev.emi.emi.api.widget.Bounds bounds =
                    new dev.emi.emi.api.widget.Bounds(4, 14, 96, 92);

            @Override
            public dev.emi.emi.api.widget.Bounds getBounds() {
                return bounds;
            }

            @Override
            public void render(net.minecraft.client.gui.GuiGraphics graphics, int mouseX, int mouseY, float delta) {
                float rotation = (net.minecraft.Util.getMillis() * 0.024F) % 360F;
                preview.draw(graphics, bounds.x(), bounds.y(), rotation);
            }
        });

        EmiRecipeWidgets.addSlot(widgets, EmiStack.of(recipe.centerInput()), 104, 14);
        EmiRecipeWidgets.addFillingArrow(widgets, 124, 15, 2_000);
        EmiRecipeWidgets.addSlot(widgets, outputs.getFirst(), 156, 14).recipeContext(this);

        int index = 0;
        for (Map.Entry<Block, Integer> entry : blockCounts.entrySet()) {
            int x = MATERIALS_X + index % 4 * 18;
            int y = MATERIALS_Y + index / 4 * 18;
            var stack = EmiStack.of(entry.getKey(), entry.getValue());
            var slot = EmiRecipeWidgets.addSlot(widgets, stack, x, y);
            if (!blockConsumes.getOrDefault(entry.getKey(), false)) {
                slot.catalyst(true);
            }
            index++;
        }
    }
}

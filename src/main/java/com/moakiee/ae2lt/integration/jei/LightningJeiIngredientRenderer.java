package com.moakiee.ae2lt.integration.jei;

import java.util.ArrayList;
import java.util.List;

import com.moakiee.ae2lt.me.key.LightningKey;

import appeng.api.client.AEStackRendering;
import appeng.util.Platform;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.ingredients.IIngredientRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import com.mojang.blaze3d.vertex.PoseStack;
import com.moakiee.ae2lt.client.compat.LegacyGui;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.TooltipFlag;

public class LightningJeiIngredientRenderer implements IIngredientRenderer<LightningKey> {
    @Override
    public void render(PoseStack guiGraphics, LightningKey ingredient) {
        render(guiGraphics, ingredient, 0, 0);
    }

    @Override
    public void render(PoseStack guiGraphics, LightningKey ingredient, int posX, int posY) {
        if (ingredient == null) {
            return;
        }

        AEStackRendering.drawInGui(Minecraft.getInstance(), guiGraphics, posX, posY, 0, ingredient);
    }

    @SuppressWarnings("removal") // JEI 15.20 still declares this deprecated method abstract.
    @Override
    public List<Component> getTooltip(LightningKey ingredient, TooltipFlag tooltipFlag) {
        return AEStackRendering.getTooltip(ingredient);
    }

    @Override
    public void getTooltip(ITooltipBuilder tooltip, LightningKey ingredient, TooltipFlag tooltipFlag) {
        tooltip.addAll(getJeiTooltip(ingredient));
    }

    @Override
    public Font getFontRenderer(Minecraft minecraft, LightningKey ingredient) {
        return minecraft.font;
    }

    private static List<Component> getJeiTooltip(LightningKey ingredient) {
        var tooltip = new ArrayList<>(AEStackRendering.getTooltip(ingredient));
        if (tooltip.isEmpty()) {
            return tooltip;
        }

        var modName = Platform.formatModName(ingredient.getModId());
        var lastLine = tooltip.get(tooltip.size() - 1).getString();
        if (lastLine.equals(modName)) {
            tooltip.remove(tooltip.size() - 1);
        }

        return tooltip;
    }
}

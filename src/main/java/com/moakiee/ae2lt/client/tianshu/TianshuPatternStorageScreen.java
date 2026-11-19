package com.moakiee.ae2lt.client.tianshu;

import com.moakiee.ae2lt.menu.TianshuPatternStorageMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public final class TianshuPatternStorageScreen extends AbstractContainerScreen<TianshuPatternStorageMenu> {
    private static final ResourceLocation BACKGROUND = ResourceLocation.withDefaultNamespace("textures/gui/container/generic_54.png");

    public TianshuPatternStorageScreen(TianshuPatternStorageMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageHeight = TianshuPatternStorageMenu.ROWS * 18 + 114;
        inventoryLabelY = imageHeight - 94;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int patternHeight = TianshuPatternStorageMenu.ROWS * 18 + 17;
        graphics.blit(BACKGROUND, leftPos, topPos, 0, 0, imageWidth, patternHeight);
        graphics.blit(BACKGROUND, leftPos, topPos + patternHeight, 0, 126, imageWidth, 96);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }
}

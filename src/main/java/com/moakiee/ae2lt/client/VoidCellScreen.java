package com.moakiee.ae2lt.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.moakiee.ae2lt.client.compat.LegacyGui;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.style.PaletteColor;
import appeng.client.gui.style.ScreenStyle;

import com.moakiee.ae2lt.me.cell.VoidCellMode;
import com.moakiee.ae2lt.menu.VoidCellMenu;

public final class VoidCellScreen extends AEBaseScreen<VoidCellMenu> {
    private final VoidCellModeButton trash;
    private final VoidCellModeButton matterBalls;
    private final VoidCellModeButton singularity;

    public VoidCellScreen(VoidCellMenu menu, Inventory playerInventory,
                          Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);
        this.trash = new VoidCellModeButton(
                VoidCellMode.TRASH, button -> menu.selectMode(VoidCellMode.TRASH));
        this.matterBalls = new VoidCellModeButton(
                VoidCellMode.MATTER_BALLS, button -> menu.selectMode(VoidCellMode.MATTER_BALLS));
        this.singularity = new VoidCellModeButton(
                VoidCellMode.SINGULARITY, button -> menu.selectMode(VoidCellMode.SINGULARITY));
    }

    @Override
    public void init() {
        super.init();
        com.moakiee.ae2lt.client.compat.LegacyWidgets.position(trash, leftPos + 22, topPos + 20);
        com.moakiee.ae2lt.client.compat.LegacyWidgets.position(matterBalls, leftPos + 54, topPos + 20);
        com.moakiee.ae2lt.client.compat.LegacyWidgets.position(singularity, leftPos + 84, topPos + 20);
        addRenderableWidget(trash);
        addRenderableWidget(matterBalls);
        addRenderableWidget(singularity);
    }

    @Override
    public void drawFG(PoseStack graphics, int offsetX, int offsetY, int mouseX, int mouseY) {
        int textColor = style.getColor(PaletteColor.DEFAULT_TEXT_COLOR).toARGB();
        LegacyGui.drawString(graphics,
                font,
                Component.translatable("gui.ae2lt.void_cell.mode." + menu.getMode().ordinal()),
                5,
                42,
                textColor,
                false);
    }
}

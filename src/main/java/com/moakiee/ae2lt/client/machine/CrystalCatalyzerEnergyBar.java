package com.moakiee.ae2lt.client.machine;

import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.moakiee.ae2lt.client.compat.LegacyGui;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;

import appeng.client.gui.style.Blitter;
import appeng.client.gui.widgets.ITooltip;

import com.moakiee.ae2lt.menu.CrystalCatalyzerMenu;

public class CrystalCatalyzerEnergyBar extends AbstractWidget implements ITooltip {
    private final CrystalCatalyzerMenu menu;
    private final Blitter fill;

    public CrystalCatalyzerEnergyBar(CrystalCatalyzerMenu menu, Blitter fill) {
        super(0, 0, fill.getSrcWidth(), fill.getSrcHeight(), Component.empty());
        this.menu = menu;
        this.fill = fill.copy();
    }

    @Override
    public void renderButton(PoseStack guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (menu.isPigmeeVariant()) {
            return;
        }
        fill.copy().opacity(0.2f).dest(x, y, width, height).blit(guiGraphics, 0);

        long capacity = Math.max(1L, menu.getEnergyCapacity());
        long stored = Math.min(Math.max(0L, menu.getStoredEnergy()), capacity);
        int filled = (int) Math.round(height * (double) stored / (double) capacity);
        if (filled <= 0) {
            return;
        }
        filled = Math.min(filled, height);

        int srcY = fill.getSrcY() + height - filled;
        int destY = y + height - filled;
        fill.copy()
                .src(fill.getSrcX(), srcY, width, filled)
                .dest(x, destY, width, filled)
                .blit(guiGraphics, 0);
    }

    @Override
    public List<Component> getTooltipMessage() {
        return List.of(Component.translatable(
                "ae2lt.gui.crystal_catalyzer.energy.tooltip",
                menu.getStoredEnergy(),
                menu.getEnergyCapacity()));
    }

    @Override
    public Rect2i getTooltipArea() {
        return new Rect2i(x - 2, y - 2, width + 4, height + 4);
    }

    @Override
    public boolean isTooltipAreaVisible() {
        return !menu.isPigmeeVariant();
    }

    @Override
    public void updateNarration(NarrationElementOutput narrationElementOutput) {
    }
}

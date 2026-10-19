package com.moakiee.ae2lt.client.machine;

import java.util.List;
import java.util.function.LongSupplier;

import com.mojang.blaze3d.vertex.PoseStack;
import com.moakiee.ae2lt.client.compat.LegacyGui;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;

import appeng.client.gui.style.Blitter;
import appeng.client.gui.widgets.ITooltip;

import com.moakiee.ae2lt.menu.OverloadProcessingFactoryMenu;

public class OverloadProcessingFactoryEnergyBar extends AbstractWidget implements ITooltip {
    private final LongSupplier storedEnergy;
    private final LongSupplier energyCapacity;
    private final Blitter fill;
    private final String tooltipKey;

    public OverloadProcessingFactoryEnergyBar(OverloadProcessingFactoryMenu menu, Blitter fill) {
        this(menu::getStoredEnergy, menu::getEnergyCapacity, fill);
    }

    public OverloadProcessingFactoryEnergyBar(LongSupplier storedEnergy, LongSupplier energyCapacity, Blitter fill) {
        this(storedEnergy, energyCapacity, fill, "ae2lt.gui.overload_factory.energy.tooltip");
    }

    protected OverloadProcessingFactoryEnergyBar(LongSupplier storedEnergy, LongSupplier energyCapacity,
            Blitter fill, String tooltipKey) {
        super(0, 0, fill.getSrcWidth(), fill.getSrcHeight(), Component.empty());
        this.storedEnergy = storedEnergy;
        this.energyCapacity = energyCapacity;
        this.fill = fill.copy();
        this.tooltipKey = tooltipKey;
    }

    @Override
    public void renderButton(PoseStack guiGraphics, int mouseX, int mouseY, float partialTick) {
        fill.copy().opacity(0.2f).dest(x, y, width, height).blit(guiGraphics, 0);

        long capacity = Math.max(1L, energyCapacity.getAsLong());
        long stored = Math.min(storedEnergy.getAsLong(), capacity);
        int filled = (int) Math.round(height * (double) stored / (double) capacity);
        if (filled <= 0) {
            return;
        }

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
                tooltipKey,
                storedEnergy.getAsLong(),
                energyCapacity.getAsLong()));
    }

    @Override
    public Rect2i getTooltipArea() {
        return new Rect2i(x - 2, y - 2, width + 4, height + 4);
    }

    @Override
    public boolean isTooltipAreaVisible() {
        return true;
    }

    @Override
    public void updateNarration(NarrationElementOutput narrationElementOutput) {
    }
}

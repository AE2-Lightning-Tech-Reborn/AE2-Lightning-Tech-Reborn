package com.moakiee.ae2lt.client.machine;

import com.mojang.blaze3d.vertex.PoseStack;
import com.moakiee.ae2lt.client.compat.LegacyGui;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import appeng.client.gui.style.Blitter;

import com.moakiee.ae2lt.menu.LightningAssemblyChamberMenu;

public class LightningAssemblyProcessWidget extends AbstractWidget {
    private final LightningAssemblyChamberMenu menu;
    private final Blitter overlay;

    public LightningAssemblyProcessWidget(LightningAssemblyChamberMenu menu, Blitter overlay) {
        super(0, 0, overlay.getSrcWidth(), overlay.getSrcHeight(), Component.empty());
        this.menu = menu;
        this.overlay = overlay.copy();
    }

    @Override
    public void renderButton(PoseStack guiGraphics, int mouseX, int mouseY, float partialTick) {
        int filled = Mth.clamp((int) Math.ceil(width * menu.getProgress()), 0, width);
        if (filled <= 0) {
            return;
        }

        overlay.copy()
                .src(overlay.getSrcX(), overlay.getSrcY(), filled, height)
                .dest(x, y, filled, height)
                .blit(guiGraphics, 0);
    }

    @Override
    public void updateNarration(NarrationElementOutput narrationElementOutput) {
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return false;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return false;
    }
}

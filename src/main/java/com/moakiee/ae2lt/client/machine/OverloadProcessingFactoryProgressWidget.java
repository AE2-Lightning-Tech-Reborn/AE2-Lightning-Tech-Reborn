package com.moakiee.ae2lt.client.machine;

import java.util.function.DoubleSupplier;

import com.mojang.blaze3d.vertex.PoseStack;
import com.moakiee.ae2lt.client.compat.LegacyGui;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import appeng.client.gui.style.Blitter;

import com.moakiee.ae2lt.menu.OverloadProcessingFactoryMenu;

public class OverloadProcessingFactoryProgressWidget extends AbstractWidget {
    private final DoubleSupplier progress;
    private final Blitter overlay;

    public OverloadProcessingFactoryProgressWidget(OverloadProcessingFactoryMenu menu, Blitter overlay) {
        this(menu::getProgress, overlay);
    }

    public OverloadProcessingFactoryProgressWidget(DoubleSupplier progress, Blitter overlay) {
        super(0, 0, overlay.getSrcWidth(), overlay.getSrcHeight(), Component.empty());
        this.progress = progress;
        this.overlay = overlay.copy();
    }

    @Override
    public void renderButton(PoseStack guiGraphics, int mouseX, int mouseY, float partialTick) {
        int filled = Mth.clamp((int) Math.ceil(width * progress.getAsDouble()), 0, width);
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

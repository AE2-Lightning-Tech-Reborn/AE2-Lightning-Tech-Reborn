package com.moakiee.ae2lt.client.ldlib;

import com.lowdragmc.lowdraglib2.gui.hud.ModularHudLayer;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.math.Size;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;

/** Hosts a non-interactive LDLib HUD at an anchor supplied by the surrounding Minecraft layer. */
public final class LdlibHud implements ModularHudLayer {
    private final Size size;
    private final ModularUI ui;

    public LdlibHud(UIElement root, int width, int height) {
        size = Size.of(width, height);
        root.setAllowHitTest(false);
        root.layout(layout -> layout.width(width).height(height));
        ui = ModularUI.of(UI.of(root));
    }

    @Override
    public Size getScreenSize() {
        return size;
    }

    @Override
    public ModularUI getModularUI() {
        return ui;
    }

    public void renderAt(GuiGraphics graphics, DeltaTracker delta, int x, int y) {
        renderAt(graphics, delta, x, y, size.width, size.height);
    }

    public void renderAt(GuiGraphics graphics, DeltaTracker delta, int x, int y, int width, int height) {
        var pose = graphics.pose();
        pose.pushPose();
        try {
            pose.translate(x, y, 0);
            pose.scale(width / (float) size.width, height / (float) size.height, 1);
            render(graphics, delta);
        } finally {
            pose.popPose();
        }
    }
}

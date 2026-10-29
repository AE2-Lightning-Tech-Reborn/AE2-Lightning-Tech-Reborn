package com.moakiee.ae2lt.client.widgets;

import appeng.client.gui.widgets.Scrollbar;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/** Shares AE2's processing-panel track geometry across standalone list screens. */
public final class SmallScrollbarTrack {
    private static final ResourceLocation PANEL = new ResourceLocation(
            "ae2lt", "textures/gui/wireless_overloaded_home.png");
    private static final ResourceLocation TRACK = new ResourceLocation(
            "ae2", "textures/guis/pattern_modes.png");

    private SmallScrollbarTrack() {}

    public static void draw(GuiGraphics graphics, Scrollbar scrollbar, int offsetX, int offsetY) {
        var bounds = scrollbar.getBounds();
        int x = offsetX + bounds.getX();
        int y = offsetY + bounds.getY();
        int height = bounds.getHeight();

        // Replace the baked track with the chassis before drawing the shared native track.
        graphics.blit(PANEL, x, y - 3, bounds.getWidth(), height + 6,
                10, 18, 1, 1, 256, 256);
        // AE2 centers its seven-pixel handle over a five-pixel track, with one-pixel end rims.
        graphics.blit(TRACK, x + 1, y - 1, 8, 76, 5, 2, 256, 256);
        graphics.blit(TRACK, x + 1, y + 1, 5, height - 1,
                8, 78, 5, 1, 256, 256);
        graphics.blit(TRACK, x + 1, y + height, 8, 129, 5, 1, 256, 256);
    }
}

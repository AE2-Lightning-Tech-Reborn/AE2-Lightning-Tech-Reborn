package com.moakiee.ae2lt.client.widgets;

import appeng.client.gui.widgets.Scrollbar;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import com.moakiee.ae2lt.client.ldlib.LdlibStyleSprite;
import net.minecraft.resources.ResourceLocation;

/** Shares AE2's processing-panel track geometry across standalone list screens. */
public final class SmallScrollbarTrack {
    private static final ResourceLocation PANEL = ResourceLocation.fromNamespaceAndPath(
            "ae2lt", "textures/gui/wireless_overloaded_home.png");
    private static final ResourceLocation TRACK = ResourceLocation.fromNamespaceAndPath(
            "ae2", "textures/guis/pattern_modes.png");

    private static final LdlibStyleSprite PANEL_ATLAS = new LdlibStyleSprite(PANEL, 256, 256);
    private static final LdlibStyleSprite TRACK_ATLAS = new LdlibStyleSprite(TRACK, 256, 256);

    private SmallScrollbarTrack() {}

    public static void draw(GuiGraphics graphics, Scrollbar scrollbar, int offsetX, int offsetY) {
        draw(graphics, scrollbar.getBounds(), offsetX, offsetY);
    }

    public static void draw(GuiGraphics graphics, Rect2i bounds, int offsetX, int offsetY) {
        int x = offsetX + bounds.getX();
        int y = offsetY + bounds.getY();
        int height = bounds.getHeight();

        // Replace the baked track with the chassis before drawing the shared native track.
        PANEL_ATLAS.draw(graphics, x, y - 3, bounds.getWidth(), height + 6,
                10, 18, 1, 1);
        // AE2 centers its seven-pixel handle over a five-pixel track, with one-pixel end rims.
        TRACK_ATLAS.draw(graphics, x + 1, y - 1, 8, 76, 5, 2);
        TRACK_ATLAS.draw(graphics, x + 1, y + 1, 5, height - 1,
                8, 78, 5, 1);
        TRACK_ATLAS.draw(graphics, x + 1, y + height, 8, 129, 5, 1);
    }
}

package com.moakiee.ae2lt.client.tianshu;

import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture;
import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.client.ldlib.BackgroundLayer;
import com.moakiee.ae2lt.client.ldlib.LdlibStyleSprite;
import net.minecraft.resources.ResourceLocation;

/** Shared pixel-atlas layout for the advanced, overload, and closed-loop pattern popups. */
final class TianshuPatternConfigLayout {
    static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            AE2LightningTech.MODID, "textures/gui/tianshu_pattern_config.png");
    static final int GUI_WIDTH = 190;
    static final int HEADER_HEIGHT = 32;
    static final int ROW_HEIGHT = 25;
    static final int VISIBLE_ROWS = 5;
    static final int FOOTER_HEIGHT = 30;
    static final int GUI_HEIGHT = HEADER_HEIGHT + VISIBLE_ROWS * ROW_HEIGHT + FOOTER_HEIGHT;
    static final int ROW_LEFT = 8;
    static final int ROW_TEXTURE_X = 9;
    static final int ROW_TEXTURE_Y = 190;
    static final int ROW_TEXTURE_WIDTH = 158;
    static final int ROW_CONTENT_X_OFFSET = 4;
    static final int SCROLLBAR_HEIGHT = 129;
    private static final LdlibStyleSprite ATLAS = new LdlibStyleSprite(TEXTURE, 256, 256);
    private static final SpriteTexture BACKGROUND = ATLAS.slice(0, 0, GUI_WIDTH, GUI_HEIGHT);

    private TianshuPatternConfigLayout() {
    }

    static int rowY(int visible) {
        return HEADER_HEIGHT + visible * ROW_HEIGHT;
    }

    static BackgroundLayer createBackground() {
        var layer = new BackgroundLayer(GUI_WIDTH, GUI_HEIGHT).add(BACKGROUND, 0, 0, GUI_WIDTH, GUI_HEIGHT);
        for (int row = 0; row < VISIBLE_ROWS; row++) {
            layer.add(ATLAS.slice(ROW_TEXTURE_X, ROW_TEXTURE_Y, ROW_TEXTURE_WIDTH, ROW_HEIGHT),
                    ROW_TEXTURE_X, HEADER_HEIGHT + row * ROW_HEIGHT, ROW_TEXTURE_WIDTH, ROW_HEIGHT);
        }
        return layer;
    }
}

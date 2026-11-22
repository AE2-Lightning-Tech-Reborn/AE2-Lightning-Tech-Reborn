package com.moakiee.ae2lt.client.ldlib;

import org.jetbrains.annotations.Nullable;

import appeng.client.gui.style.ScreenStyle;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.math.Size;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/** Noninteractive LDLib elements rendered in the screen's background pass. */
public final class BackgroundLayer {
    private static final int PANEL_BORDER = 4;
    private static final int PANEL_ATLAS_SIZE = 256;
    private static final int PANEL_TILE = PANEL_ATLAS_SIZE - 2 * PANEL_BORDER;
    private static final LdlibStyleSprite PANEL_ATLAS = new LdlibStyleSprite(
            ResourceLocation.fromNamespaceAndPath("ae2", "textures/guis/background.png"),
            Size.of(PANEL_ATLAS_SIZE, PANEL_ATLAS_SIZE), 0, 0, PANEL_ATLAS_SIZE, PANEL_ATLAS_SIZE);

    private final UIElement root = new UIElement();
    private final int width;
    private final int height;
    private ModularUI ui;

    public BackgroundLayer(int width, int height) {
        this.width = width;
        this.height = height;
        root.setAllowHitTest(false);
        root.layout(layout -> layout.width(width).height(height));
    }

    /** Builds the background and optional overlay described by the AE2 screen style. */
    public static @Nullable BackgroundLayer fromStyle(ScreenStyle style) {
        var image = style.getBackground();
        var panel = style.getGeneratedBackground();
        if (image == null && panel == null) {
            return null;
        }
        var sprite = image == null ? null : LdlibStyleSprite.from(image);
        int panelWidth = panel == null ? 0 : panel.getWidth();
        int panelHeight = panel == null ? 0 : panel.getHeight();
        var layer = new BackgroundLayer(
                Math.max(panelWidth, sprite == null ? 0 : sprite.width()),
                Math.max(panelHeight, sprite == null ? 0 : sprite.height()));
        if (panelWidth >= 2 * PANEL_BORDER && panelHeight >= 2 * PANEL_BORDER) {
            layer.addPanel(panelWidth, panelHeight);
        }
        if (sprite != null) {
            layer.add(sprite.full(), 0, 0, sprite.width(), sprite.height());
        }
        return layer;
    }

    private void addPanel(int width, int height) {
        int edge = PANEL_BORDER;
        int atlasEnd = PANEL_ATLAS_SIZE - edge;
        int right = width - edge;
        int bottom = height - edge;
        int innerWidth = right - edge;
        int innerHeight = bottom - edge;
        add(PANEL_ATLAS.slice(0, 0, edge, edge), 0, 0, edge, edge);
        add(PANEL_ATLAS.slice(atlasEnd, 0, edge, edge), right, 0, edge, edge);
        add(PANEL_ATLAS.slice(0, atlasEnd, edge, edge), 0, bottom, edge, edge);
        add(PANEL_ATLAS.slice(atlasEnd, atlasEnd, edge, edge), right, bottom, edge, edge);

        // Match the upstream cropped tiles without SpriteTexture.REPEAT's immediate shader path.
        for (int offsetX = 0; offsetX < innerWidth; offsetX += PANEL_TILE) {
            int tileWidth = Math.min(PANEL_TILE, innerWidth - offsetX);
            int x = edge + offsetX;
            add(PANEL_ATLAS.slice(edge, 0, tileWidth, edge), x, 0, tileWidth, edge);
            add(PANEL_ATLAS.slice(edge, atlasEnd, tileWidth, edge), x, bottom, tileWidth, edge);
            for (int offsetY = 0; offsetY < innerHeight; offsetY += PANEL_TILE) {
                int tileHeight = Math.min(PANEL_TILE, innerHeight - offsetY);
                add(PANEL_ATLAS.slice(edge, edge, tileWidth, tileHeight),
                        x, edge + offsetY, tileWidth, tileHeight);
            }
        }
        for (int offsetY = 0; offsetY < innerHeight; offsetY += PANEL_TILE) {
            int tileHeight = Math.min(PANEL_TILE, innerHeight - offsetY);
            int y = edge + offsetY;
            add(PANEL_ATLAS.slice(0, edge, edge, tileHeight), 0, y, edge, tileHeight);
            add(PANEL_ATLAS.slice(atlasEnd, edge, edge, tileHeight), right, y, edge, tileHeight);
        }
    }

    public BackgroundLayer add(IGuiTexture texture, int x, int y, int width, int height) {
        var element = LdlibScreens.at(new UIElement(), x, y, width, height);
        element.setAllowHitTest(false);
        element.style(style -> style.background(texture));
        root.addChild(element);
        return this;
    }

    /** Releases the cached UI; a later render binds the retained elements to a new UI. */
    public void onRemoved() {
        var previousUI = ui;
        ui = null;
        if (previousUI != null) {
            previousUI.onRemoved();
        }
    }

    public void render(GuiGraphics graphics, int x, int y, int mouseX, int mouseY, float ticks) {
        if (ui == null) {
            ui = ModularUI.of(UI.of(root));
            ui.init(width, height);
        }
        var pose = graphics.pose();
        pose.pushPose();
        try {
            pose.translate(x, y, 0);
            ui.getWidget().render(graphics, mouseX, mouseY, ticks);
        } finally {
            pose.popPose();
        }
    }
}

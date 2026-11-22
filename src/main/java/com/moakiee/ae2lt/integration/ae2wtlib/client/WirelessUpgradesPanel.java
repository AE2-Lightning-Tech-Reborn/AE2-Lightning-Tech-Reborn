package com.moakiee.ae2lt.integration.ae2wtlib.client;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import appeng.client.Point;
import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.WidgetContainer;
import appeng.menu.AEBaseMenu;
import appeng.menu.SlotSemantics;
import com.moakiee.ae2lt.client.ldlib.BackgroundLayer;
import de.mari_023.ae2wtlib.api.gui.AE2wtlibSlotSemantics;
import de.mari_023.ae2wtlib.api.gui.Icon;
import de.mari_023.ae2wtlib.api.gui.ScrollingUpgradesPanel;
import de.mari_023.ae2wtlib.api.gui.UpgradeBackground;
import de.mari_023.ae2wtlib.api.terminal.WTMenuHost;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.inventory.Slot;

/** Draws the WT upgrade panel's atlas strips through LDLib2. */
public final class WirelessUpgradesPanel extends ScrollingUpgradesPanel {
    private static final int SLOT_SIZE = 18;
    private static final int PADDING = 5;
    private static final int PANEL_WIDTH = SLOT_SIZE + 2 * PADDING;

    private Point origin = Point.ZERO;
    private int renderedWidth;
    private int renderedHeight;
    private BackgroundLayer background;

    private WirelessUpgradesPanel(List<Slot> slots, WTMenuHost host, WidgetContainer widgets) {
        super(slots, host, widgets, host::getUpgrades);
    }

    public static WirelessUpgradesPanel attach(WidgetContainer widgets, AEBaseMenu menu, WTMenuHost host) {
        var slots = new ArrayList<>(menu.getSlots(AE2wtlibSlotSemantics.SINGULARITY));
        slots.addAll(menu.getSlots(SlotSemantics.UPGRADE));
        var panel = new WirelessUpgradesPanel(slots, host, widgets);
        widgets.add("scrollingUpgrades", panel);
        return panel;
    }

    @Override
    public void populateScreen(Consumer<AbstractWidget> widgets, Rect2i screen, AEBaseScreen<?> owner) {
        super.populateScreen(widgets, screen, owner);
        onRemoved();
        origin = Point.fromTopLeft(screen);
    }

    public void onRemoved() {
        var previous = background;
        background = null;
        if (previous != null) {
            previous.onRemoved();
        }
    }

    @Override
    public void drawBackgroundLayer(GuiGraphics graphics, Rect2i screen, Point mouse) {
        var bounds = getBounds();
        int rows = (bounds.getHeight() - 2 * PADDING) / SLOT_SIZE;
        if (rows <= 0) {
            onRemoved();
            return;
        }
        if (background == null || renderedWidth != bounds.getWidth() || renderedHeight != bounds.getHeight()) {
            var next = buildBackground(bounds.getWidth(), bounds.getHeight(), rows);
            onRemoved();
            renderedWidth = bounds.getWidth();
            renderedHeight = bounds.getHeight();
            background = next;
        }
        background.render(graphics, origin.getX() + bounds.getX(), origin.getY() + bounds.getY(),
                mouse.getX(), mouse.getY(), 0);
    }

    private static BackgroundLayer buildBackground(int width, int height, int rows) {
        var sprites = UpgradeBackground.get(width > PANEL_WIDTH);
        var layer = new BackgroundLayer(width, height);
        addStrip(layer, sprites.top(), 0);
        if (rows > 2) {
            var middle = sprites.middle();
            for (int row = 0; row < rows - 2; row++) {
                layer.add(WirelessIconButton.sprite(middle),
                        0, PADDING + SLOT_SIZE + row * SLOT_SIZE, middle.width(), middle.height());
            }
        }
        addStrip(layer, sprites.bottom(), PADDING + (rows - 1) * SLOT_SIZE);
        return layer;
    }

    private static void addStrip(BackgroundLayer layer, Icon icon, int y) {
        layer.add(WirelessIconButton.sprite(icon), 0, y, icon.width(), icon.height());
    }
}

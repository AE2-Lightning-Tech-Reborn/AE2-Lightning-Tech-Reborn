package com.moakiee.ae2lt.client.ldlib;

import appeng.client.Point;
import appeng.client.gui.WidgetContainer;
import appeng.client.gui.widgets.Scrollbar;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;

/** Exposes an LDLib scroller through the AE2 scrollbar API used by terminals and WT. */
public final class LdlibTerminalScrollbar extends Scrollbar {
    private final LdlibRowScroller view;

    private LdlibTerminalScrollbar(Style style) {
        super(style);
        view = new LdlibRowScroller(style);
    }

    public static LdlibTerminalScrollbar add(WidgetContainer widgets, String id, Style style) {
        var bar = new LdlibTerminalScrollbar(style);
        widgets.add(id, bar);
        return bar;
    }

    @Override
    public void setPosition(Point position) {
        super.setPosition(position);
        view.setPosition(position);
    }

    @Override
    public void setSize(int width, int height) {
        super.setSize(width, height);
        view.setSize(width, height);
    }

    @Override
    public Scrollbar setHeight(int height) {
        super.setHeight(height);
        view.setHeight(height);
        return this;
    }

    @Override
    public void setRange(int min, int max, int pageSize) {
        view.setRange(min, max, pageSize);
    }

    @Override
    public int getCurrentScroll() {
        return view.getCurrentScroll();
    }

    @Override
    public void setCurrentScroll(int row) {
        view.setCurrentScroll(row);
    }

    @Override
    public boolean onMouseDown(Point mouse, int button) {
        return view.onMouseDown(mouse, button);
    }

    @Override
    public boolean onMouseDrag(Point mouse, int button) {
        return view.onMouseDrag(mouse, button);
    }

    @Override
    public boolean onMouseUp(Point mouse, int button) {
        view.onMouseUp(mouse, button);
        return false;
    }

    @Override
    public boolean wantsAllMouseUpEvents() {
        return view.wantsAllMouseUpEvents();
    }

    @Override
    public boolean onMouseWheel(Point mouse, double delta) {
        return view.onMouseWheel(mouse, delta);
    }

    @Override
    public void tick() {
        view.tick();
    }

    @Override
    public void setVisible(boolean visible) {
        super.setVisible(visible);
        view.setVisible(visible);
    }

    @Override
    public void drawForegroundLayer(GuiGraphics graphics, Rect2i bounds, Point mouse) {
        view.drawForegroundLayer(graphics, bounds, mouse);
    }
}

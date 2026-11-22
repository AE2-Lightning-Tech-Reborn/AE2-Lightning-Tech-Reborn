package com.moakiee.ae2lt.client.ldlib;

import java.time.Duration;

import appeng.client.Point;
import appeng.client.gui.ICompositeWidget;
import appeng.client.gui.WidgetContainer;
import appeng.client.gui.style.Blitter;
import appeng.client.gui.widgets.EventRepeater;
import appeng.client.gui.widgets.Scrollbar;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.util.Mth;

/** Draws an LDLib thumb texture with native row input in an AE2 style JSON position. */
public final class LdlibRowScroller implements ICompositeWidget {
    private final Scrollbar.Style style;
    private final IGuiTexture thumb;
    private final IGuiTexture disabledThumb;
    private final EventRepeater pageRepeater = new EventRepeater(Duration.ofMillis(250), Duration.ofMillis(150));

    private Point position = Point.ZERO;
    private int height = 16;
    private int min;
    private int max;
    private int row;
    private int pageSize = 1;
    private boolean captureMouseWheel = true;
    private boolean visible = true;
    private int dragOffset = -1;
    private Runnable onScrollChanged = () -> {};

    public LdlibRowScroller() {
        this(Scrollbar.SMALL);
    }

    public LdlibRowScroller(Scrollbar.Style style) {
        this.style = style;
        this.thumb = (graphics, mouseX, mouseY, x, y, width, height, ticks) ->
                Blitter.guiSprite(style.enabledSprite()).dest(Math.round(x), Math.round(y)).blit(graphics);
        this.disabledThumb = (graphics, mouseX, mouseY, x, y, width, height, ticks) ->
                Blitter.guiSprite(style.disabledSprite()).dest(Math.round(x), Math.round(y)).blit(graphics);
    }

    public static LdlibRowScroller add(WidgetContainer widgets, String id) {
        var result = new LdlibRowScroller(Scrollbar.SMALL);
        widgets.add(id, result);
        return result;
    }

    @Override
    public void setPosition(Point position) {
        this.position = position;
    }

    @Override
    public void setSize(int width, int height) {
        if (height != 0) {
            setHeight(height);
        }
    }

    public void setHeight(int height) {
        this.height = height;
    }

    @Override
    public Rect2i getBounds() {
        return new Rect2i(position.getX(), position.getY(), style.handleWidth(), height);
    }

    public void setRange(int min, int max, int pageSize) {
        this.min = min;
        this.max = Math.max(min, max);
        this.pageSize = pageSize;
        setCurrentScroll(row);
    }

    public int getCurrentScroll() {
        return row;
    }

    public void setCurrentScroll(int row) {
        updateRow(Mth.clamp(row, min, max));
    }

    public void setOnScrollChanged(Runnable listener) {
        onScrollChanged = listener;
    }

    private void updateRow(int row) {
        if (this.row != row) {
            this.row = row;
            onScrollChanged.run();
        }
    }

    private int thumbTravel() {
        return height - style.handleHeight();
    }

    private int thumbOffset() {
        return max == min ? 0 : (row - min) * thumbTravel() / (max - min);
    }

    @Override
    public void drawForegroundLayer(GuiGraphics graphics, Rect2i bounds, Point mouse) {
        var texture = max > min ? thumb : disabledThumb;
        texture.draw(graphics, mouse.getX(), mouse.getY(), position.getX(), position.getY() + thumbOffset(),
                style.handleWidth(), style.handleHeight(), 0);
    }

    @Override
    public boolean onMouseDown(Point mouse, int button) {
        if (button != 0) {
            return false;
        }
        dragOffset = -1;
        if (max == min) {
            return true;
        }
        int y = mouse.getY() - position.getY();
        int thumbY = thumbOffset();
        if (y < thumbY || y >= thumbY + style.handleHeight()) {
            int direction = y < thumbY ? -1 : 1;
            pageBy(direction);
            pageRepeater.repeat(() -> pageBy(direction));
            return true;
        }
        dragOffset = y - thumbY;
        return true;
    }

    private void pageBy(int direction) {
        setCurrentScroll(row + direction * pageSize);
    }

    @Override
    public void tick() {
        pageRepeater.tick();
    }

    @Override
    public boolean onMouseDrag(Point mouse, int button) {
        int travel = thumbTravel();
        if (dragOffset < 0 || max == min || pageRepeater.isRepeating()) {
            return false;
        }
        double offset = mouse.getY() - position.getY() - dragOffset;
        double progress = Mth.clamp(offset / travel, 0.0, 1.0);
        setCurrentScroll(min + (int) Math.round(progress * (max - min)));
        return true;
    }

    @Override
    public boolean onMouseUp(Point mouse, int button) {
        if (button == 0) {
            pageRepeater.stop();
            dragOffset = -1;
        }
        return false;
    }

    @Override
    public boolean wantsAllMouseUpEvents() {
        return true;
    }

    @Override
    public boolean onMouseWheel(Point mouse, double delta) {
        if (max == min) {
            return false;
        }
        setCurrentScroll((int) (row - Mth.clamp(delta, -1, 1) * pageSize));
        return true;
    }

    @Override
    public boolean wantsAllMouseWheelEvents() {
        return captureMouseWheel;
    }

    public void setCaptureMouseWheel(boolean captureMouseWheel) {
        this.captureMouseWheel = captureMouseWheel;
    }

    @Override
    public boolean isVisible() {
        return visible;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }
}

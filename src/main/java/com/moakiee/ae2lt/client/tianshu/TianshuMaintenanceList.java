package com.moakiee.ae2lt.client.tianshu;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import org.lwjgl.glfw.GLFW;

import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.VirtualScrollerView;
import com.moakiee.ae2lt.client.ldlib.LdlibRowScroller;
import com.moakiee.ae2lt.client.ldlib.LdlibScreens;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** A virtual maintenance table hosted inside an AE2 screen. */
final class TianshuMaintenanceList<T> {
    private static final IGuiTexture EVEN_ROW = LdlibScreens.solid(() -> 0x1EFFFFFF);
    private static final IGuiTexture ODD_ROW = LdlibScreens.solid(() -> 0x12000000);
    private static final IGuiTexture HOVER_ROW = LdlibScreens.solid(() -> 0x553B719F);

    private final VirtualScrollerView<IndexedEntry<T>> list;
    private final ModularUI ui;
    private final LdlibRowScroller scrollbar;
    private final Rect2i bounds;
    private final int rowHeight;
    private final Consumer<T> openRow;
    private final Function<T, List<Component>> rowTooltips;
    private final int visibleRows;
    private List<T> entries;
    private int maxScroll;

    TianshuMaintenanceList(int width, int height, Rect2i bounds, int rowHeight, LdlibRowScroller scrollbar,
            Function<T, Row> createRow, Consumer<T> openRow, Function<T, List<Component>> tooltips) {
        this.scrollbar = scrollbar;
        this.bounds = bounds;
        this.rowHeight = rowHeight;
        this.openRow = openRow;
        this.rowTooltips = tooltips;
        visibleRows = bounds.getHeight() / rowHeight;
        list = LdlibScreens.virtualRows(rowHeight, entry -> {
            var row = createRow.apply(entry.value());
            row.stripe.style(style -> style.background(LdlibScreens.either(row.stripe::isHover, HOVER_ROW,
                    LdlibScreens.either(() -> ((entry.index() - scrollbar.getCurrentScroll()) & 1) == 0,
                            EVEN_ROW, ODD_ROW))));
            return row;
        });
        var root = new UIElement();
        root.addChild(LdlibScreens.at(list, bounds.getX(), bounds.getY(), bounds.getWidth(), bounds.getHeight()));
        ui = LdlibScreens.localLayer(root, width, height);
    }

    void setEntries(List<T> values) {
        if (values.equals(entries)) {
            return;
        }
        entries = List.copyOf(values);
        maxScroll = Math.max(0, entries.size() - visibleRows);
        scrollbar.setRange(0, maxScroll, Math.max(1, visibleRows - 1));
        var indexed = new ArrayList<IndexedEntry<T>>(entries.size());
        for (int index = 0; index < entries.size(); index++) {
            indexed.add(new IndexedEntry<>(index, entries.get(index)));
        }
        list.setItems(indexed);
    }

    boolean isEmpty() {
        return list.getItemCount() == 0;
    }

    List<Component> tooltipTextsAt(double x, double y) {
        int index = entryIndexAt(x, y);
        return index < 0 ? List.of() : rowTooltips.apply(entries.get(index));
    }

    boolean mouseClicked(double x, double y, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT && button != GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
            return false;
        }
        int index = entryIndexAt(x, y);
        if (index < 0) {
            return false;
        }
        openRow.accept(entries.get(index));
        return true;
    }

    private int entryIndexAt(double x, double y) {
        if (entries == null || !isWithinList(x, y)) {
            return -1;
        }
        // A wheel event can advance the first row before LDLib lays out the next virtual window.
        int index = scrollbar.getCurrentScroll() + (int) ((y - bounds.getY()) / rowHeight);
        return index < entries.size() ? index : -1;
    }

    boolean mouseScrolled(double x, double y, double scrollY) {
        if (!isWithinList(x, y)) {
            return false;
        }
        scrollbar.setCurrentScroll(scrollbar.getCurrentScroll() - (int) Math.signum(scrollY));
        return true;
    }

    private boolean isWithinList(double x, double y) {
        return x >= bounds.getX() && x < bounds.getX() + bounds.getWidth()
                && y >= bounds.getY() && y < bounds.getY() + bounds.getHeight();
    }

    void render(GuiGraphics graphics, int x, int y) {
        LdlibScreens.scrollVirtualRows(list, scrollbar.getCurrentScroll(), maxScroll);
        ui.getWidget().render(graphics, x, y, 0);
    }

    private record IndexedEntry<T>(int index, T value) {
    }

    static class Row extends UIElement {
        private final UIElement stripe = new UIElement();

        Row(int width, int height) {
            layout(l -> l.width(width).height(height));
            addChild(LdlibScreens.at(stripe, 0, 0, width, height - 1));
        }

        <E extends UIElement> E add(E element, int x, int y, int width, int height) {
            element.setAllowHitTest(false);
            addChild(LdlibScreens.at(element, x, y, width, height));
            return element;
        }

        Label label(int x, int y, int width, Horizontal alignment) {
            var label = LdlibScreens.label(Component.empty(), 0xFFFFFFFF);
            label.textStyle(style -> style.textWrap(TextWrap.NONE).textAlignHorizontal(alignment));
            return add(label, x, y, width, 9);
        }

        static void text(Label label, Component value, int color) {
            label.setText(value);
            label.getTextStyle().textColor(color);
        }

        void item(ItemStack stack, int x, int y) {
            add(LdlibScreens.unadornedItemIcon(stack), x, y, 16, 16);
        }
    }
}

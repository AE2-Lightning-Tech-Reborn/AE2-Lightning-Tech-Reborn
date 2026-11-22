package com.moakiee.ae2lt.client.ldlib;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.jetbrains.annotations.Nullable;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.data.Tooltips;
import com.lowdragmc.lowdraglib2.gui.ui.data.Vertical;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.util.DrawerHelper;

import dev.vfyjxf.taffy.style.TaffyPosition;

import net.minecraft.network.chat.Component;

/**
 * Immediate-mode text overlay backed by retained LDLib2 elements.
 *
 * <p>Screens whose text depends on a lot of frame-local state describe it between
 * {@link #begin()} and {@link #end()} each frame; the layer reuses a pool of
 * {@link Label}s (only touching the ones whose content changed) and draws the
 * requested flat rectangles underneath them. Truncated labels can carry the full
 * text as an LDLib hover tooltip.</p>
 */
public final class TextLayer {
    private final List<Label> pool = new ArrayList<>();
    private final List<Item> shown = new ArrayList<>();
    private final List<Item> pending = new ArrayList<>();
    private final List<Fill> shownFills = new ArrayList<>();
    private final List<Fill> pendingFills = new ArrayList<>();
    private UIElement labels;

    /** Creates (or recreates) the layer root, sized to the GUI. */
    public UIElement root(int width, int height) {
        pool.clear();
        shown.clear();
        shownFills.clear();
        var root = new UIElement();
        root.layout(l -> l.width(width).height(height));
        root.setAllowHitTest(false);

        root.style(style -> style.background((graphics, mouseX, mouseY, x, y, w, h, ticks) -> {
            for (var fill : shownFills) {
                DrawerHelper.drawSolidRect(graphics, x + fill.x(), y + fill.y(), fill.width(), fill.height(),
                        fill.argb());
            }
        }));

        labels = new UIElement().setAllowHitTest(false);
        labels.layout(l -> l.positionType(TaffyPosition.ABSOLUTE).left(0).top(0).width(width).height(height));
        root.addChild(labels);
        return root;
    }

    public void begin() {
        pending.clear();
        pendingFills.clear();
    }

    /** Flat (shadowless) text at GUI-local {@code x, y}; {@code tooltip} is shown on hover when non-null. */
    public void text(Component text, int x, int y, int width, int color, @Nullable Component tooltip) {
        // Match Font#drawString: a colour without alpha is treated as opaque.
        int argb = (color & 0xFC000000) == 0 ? color | 0xFF000000 : color;
        pending.add(new Item(text, x, y, Math.max(1, width + 1), argb, tooltip));
    }

    public void fill(int x0, int y0, int x1, int y1, int argb) {
        if (x0 != x1 && y0 != y1) {
            pendingFills.add(new Fill(Math.min(x0, x1), Math.min(y0, y1),
                    Math.abs(x1 - x0), Math.abs(y1 - y0), argb));
        }
    }

    public void end() {
        if (labels == null) {
            return;
        }
        shownFills.clear();
        shownFills.addAll(pendingFills);

        for (int i = 0; i < pending.size(); i++) {
            Item item = pending.get(i);
            Label label;
            if (i < pool.size()) {
                label = pool.get(i);
            } else {
                label = LdlibScreens.label(Component.empty(), 0xFFFFFFFF);
                label.textStyle(t -> t.textShadow(false).textWrap(TextWrap.NONE).textAlignVertical(Vertical.TOP));
                pool.add(label);
                shown.add(null);
                labels.addChild(label);
            }
            Item previous = shown.get(i);
            if (item.equals(previous)) {
                continue;
            }
            shown.set(i, item);
            label.setDisplay(true);
            if (previous == null || !previous.text().equals(item.text())) {
                label.setText(item.text());
            }
            if (previous == null || previous.color() != item.color()) {
                label.textStyle(t -> t.textColor(item.color()));
            }
            if (previous == null || previous.x() != item.x() || previous.y() != item.y()
                    || previous.width() != item.width()) {
                label.layout(l -> l.positionType(TaffyPosition.ABSOLUTE)
                        .left(item.x()).top(item.y()).width(item.width()).height(9));
            }
            if (previous == null || !Objects.equals(previous.tooltip(), item.tooltip())) {
                label.style(s -> s.tooltips(item.tooltip() == null ? Tooltips.empty() : Tooltips.of(item.tooltip())));
            }
        }
        for (int i = pending.size(); i < pool.size(); i++) {
            if (shown.get(i) != null) {
                shown.set(i, null);
                pool.get(i).setDisplay(false);
            }
        }
    }

    private record Fill(int x, int y, int width, int height, int argb) {
    }

    private record Item(Component text, int x, int y, int width, int color, @Nullable Component tooltip) {
    }
}

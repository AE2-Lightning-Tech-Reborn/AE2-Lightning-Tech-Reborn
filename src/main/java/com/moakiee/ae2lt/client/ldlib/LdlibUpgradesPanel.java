package com.moakiee.ae2lt.client.ldlib;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import appeng.client.Point;
import appeng.api.upgrades.Upgrades;
import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.ICompositeWidget;
import appeng.client.gui.Tooltip;
import appeng.client.gui.widgets.UpgradesPanel;
import appeng.core.localization.GuiText;
import appeng.menu.slot.AppEngSlot;
import com.lowdragmc.lowdraglib2.gui.texture.ColorBorderTexture;
import com.lowdragmc.lowdraglib2.math.Size;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.level.ItemLike;
import org.jetbrains.annotations.Nullable;

/** LDLib rendering for the AE2 upgrade panel, using its existing menu slots. */
public final class LdlibUpgradesPanel implements ICompositeWidget {
    private static final int SLOT_SIZE = 18;
    private static final int PADDING = 5;
    private static final int MAX_ROWS = 8;
    private static final ColorBorderTexture OUTLINE = new ColorBorderTexture(-1, 0xFFF2F2F2);
    private static final LdlibStyleSprite ATLAS = new LdlibStyleSprite(
            ResourceLocation.fromNamespaceAndPath("ae2", "textures/guis/extra_panels.png"),
            Size.of(128, 128), 0, 0, 128, 128);

    private final UpgradesPanel panel;
    private final List<Slot> slots;
    private Point origin = Point.ZERO;
    private int renderedSlots;
    private @Nullable BackgroundLayer background;

    public LdlibUpgradesPanel(List<Slot> slots, Supplier<List<Component>> tooltip) {
        this.slots = slots;
        panel = new UpgradesPanel(slots, tooltip);
    }

    public static LdlibUpgradesPanel forMachine(List<Slot> slots, Supplier<ItemLike> machine) {
        return new LdlibUpgradesPanel(slots, () -> {
            var lines = new ArrayList<Component>();
            lines.add(GuiText.CompatibleUpgrades.text());
            lines.addAll(Upgrades.getTooltipLinesForMachine(machine.get()));
            return lines;
        });
    }

    @Override
    public void setPosition(Point position) {
        panel.setPosition(position);
    }

    @Override
    public void setSize(int width, int height) {
        panel.setSize(width, height);
    }

    @Override
    public Rect2i getBounds() {
        return panel.getBounds();
    }

    @Override
    public void populateScreen(Consumer<AbstractWidget> widgets, Rect2i screen, AEBaseScreen<?> owner) {
        onRemoved();
        origin = Point.fromTopLeft(screen);
    }

    @Override
    public void updateBeforeRender() {
        panel.updateBeforeRender();
    }

    @Override
    public void addExclusionZones(List<Rect2i> zones, Rect2i screen) {
        panel.addExclusionZones(zones, screen);
    }

    @Override
    public @Nullable Tooltip getTooltip(int x, int y) {
        return panel.getTooltip(x, y);
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
        int count = 0;
        for (Slot slot : slots) {
            if (slot instanceof AppEngSlot aeSlot && aeSlot.isSlotEnabled()) {
                count++;
            }
        }
        if (count == 0) {
            onRemoved();
            return;
        }
        if (background == null || renderedSlots != count) {
            var next = buildBackground(count);
            onRemoved();
            background = next;
            renderedSlots = count;
        }
        var bounds = getBounds();
        background.render(graphics, origin.getX() + bounds.getX(), origin.getY() + bounds.getY(),
                mouse.getX(), mouse.getY(), 0);
    }

    private BackgroundLayer buildBackground(int count) {
        var bounds = getBounds();
        int outlineHeight = count * SLOT_SIZE;
        var layer = new BackgroundLayer(bounds.getWidth(), Math.max(bounds.getHeight(), PADDING + outlineHeight + 2));
        int rightPaddingStart = count - MAX_ROWS;
        for (int column = 0; column * MAX_ROWS < count; column++) {
            int firstSlot = column * MAX_ROWS;
            int rows = Math.min(MAX_ROWS, count - firstSlot);
            int lastRow = rows - 1;
            addSlotRows(layer, column, rows, 0, 1, rightPaddingStart);
            if (rows > 1) {
                // The last MAX_ROWS slots include right padding, which can start inside a column.
                int split = Math.clamp(rightPaddingStart - firstSlot, 1, lastRow);
                addSlotRows(layer, column, rows, 1, split, rightPaddingStart);
                addSlotRows(layer, column, rows, split, lastRow, rightPaddingStart);
                addSlotRows(layer, column, rows, lastRow, rows, rightPaddingStart);
            }
            if (column > 0 && rows < MAX_ROWS) {
                int x = PADDING + column * SLOT_SIZE;
                int y = PADDING + rows * SLOT_SIZE;
                layer.add(ATLAS.slice(12, 33, SLOT_SIZE, SLOT_SIZE), x, y, SLOT_SIZE, SLOT_SIZE);
            }
        }
        return layer.add(OUTLINE, 0, PADDING, PADDING + 13, outlineHeight);
    }

    private static void addSlotRows(BackgroundLayer layer, int column, int rows, int start, int end,
            int rightPaddingStart) {
        if (start >= end) {
            return;
        }
        int left = column == 0 ? PADDING : 0;
        int top = start == 0 ? PADDING : 0;
        int bottom = end == rows ? 7 : 0;
        int right = column * MAX_ROWS + start >= rightPaddingStart ? PADDING : 0;
        int width = SLOT_SIZE + left + right;
        int tileHeight = SLOT_SIZE + top + bottom;
        int height = (end - start) * SLOT_SIZE + top + bottom;
        for (int offsetY = 0; offsetY < height; offsetY += tileHeight) {
            int blockHeight = Math.min(tileHeight, height - offsetY);
            var texture = ATLAS.slice(PADDING - left, PADDING - top, width, blockHeight);
            layer.add(texture, PADDING + column * SLOT_SIZE - left,
                    PADDING + start * SLOT_SIZE - top + offsetY, width, blockHeight);
        }
    }
}

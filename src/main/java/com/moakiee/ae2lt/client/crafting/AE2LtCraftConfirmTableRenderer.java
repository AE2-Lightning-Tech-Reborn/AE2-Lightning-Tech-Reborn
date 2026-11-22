package com.moakiee.ae2lt.client.crafting;

import java.util.ArrayList;
import java.util.List;

import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;

import dev.vfyjxf.taffy.style.TaffyDisplay;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import appeng.api.client.AEKeyRendering;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AmountFormat;
import appeng.api.stacks.GenericStack;
import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.StackWithBounds;
import appeng.client.gui.style.PaletteColor;
import appeng.core.localization.GuiText;
import appeng.menu.me.crafting.CraftingPlanSummaryEntry;

import com.moakiee.thunderbolt.ae2.crafting.ExactPlanPresentation;
import com.moakiee.ae2lt.client.ldlib.LdlibScreens;

/** AE2 plan cells fitted to the resizable AE2LT report texture. */
final class AE2LtCraftConfirmTableRenderer {
    private static final int COLUMNS = 3;
    private static final int CELL_WIDTH = 68;
    private static final int CELL_HEIGHT = 23;
    private static final int ENTRY_WIDTH = CELL_WIDTH - 1;
    private static final int ENTRY_HEIGHT = CELL_HEIGHT - 1;
    private static final int TABLE_X = 9;
    private static final int TABLE_Y = 27;
    private final AEBaseScreen<?> screen;
    private final int rows;
    private final List<PlanCell> cells = new ArrayList<>();
    private final ModularUI table;
    @Nullable
    private StackWithBounds hoveredStack;

    AE2LtCraftConfirmTableRenderer(AEBaseScreen<?> screen, int rows) {
        this.screen = screen;
        this.rows = rows;
        int width = COLUMNS * CELL_WIDTH;
        int height = rows * CELL_HEIGHT;
        var cellRoot = new UIElement().setAllowHitTest(false);
        cellRoot.layout(layout -> layout.display(TaffyDisplay.GRID)
                .gridTemplateColumns("repeat(" + COLUMNS + ", " + CELL_WIDTH + "px)")
                .gridTemplateRows("repeat(" + rows + ", " + CELL_HEIGHT + "px)"));
        int color = screen.getStyle().getColor(PaletteColor.DEFAULT_TEXT_COLOR).toARGB();
        for (int i = 0; i < rows * COLUMNS; i++) {
            var cell = new PlanCell(color);
            cells.add(cell);
            cellRoot.addChild(cell);
        }
        var root = new UIElement();
        root.setAllowHitTest(false);
        root.addChild(LdlibScreens.at(cellRoot, TABLE_X, TABLE_Y, width, height));
        table = LdlibScreens.localLayer(root, TABLE_X + width, TABLE_Y + height);
    }

    void onRemoved() {
        if (!table.isRemoved()) {
            table.onRemoved();
        }
    }

    int getScrollableRows(int size) {
        return Math.max(0, (size + COLUMNS - 1) / COLUMNS - rows);
    }

    @Nullable
    StackWithBounds getHoveredStack() {
        return hoveredStack;
    }

    @Nullable
    private StackWithBounds getStackAt(double mouseX, double mouseY,
            List<CraftingPlanSummaryEntry> entries, int scroll) {
        int cell = cellAt((int) mouseX - screen.getGuiLeft(), (int) mouseY - screen.getGuiTop());
        var entry = entryAt(entries, scroll, cell);
        if (entry == null) {
            return null;
        }
        return new StackWithBounds(new GenericStack(entry.getWhat(), 0),
                new Rect2i(screen.getGuiLeft() + TABLE_X + (cell % COLUMNS) * CELL_WIDTH,
                        screen.getGuiTop() + TABLE_Y + (cell / COLUMNS) * CELL_HEIGHT,
                        ENTRY_WIDTH, ENTRY_HEIGHT));
    }

    private int cellAt(int x, int y) {
        x -= TABLE_X;
        y -= TABLE_Y;
        if (x < 0 || y < 0 || x >= COLUMNS * CELL_WIDTH || y >= rows * CELL_HEIGHT
                || x % CELL_WIDTH > ENTRY_WIDTH || y % CELL_HEIGHT > ENTRY_HEIGHT) {
            return -1;
        }
        return (y / CELL_HEIGHT) * COLUMNS + x / CELL_WIDTH;
    }

    @Nullable
    private CraftingPlanSummaryEntry entryAt(List<CraftingPlanSummaryEntry> entries, int scroll, int cell) {
        int index = Math.clamp(scroll, 0, getScrollableRows(entries.size())) * COLUMNS + cell;
        return cell >= 0 && index < entries.size() ? entries.get(index) : null;
    }

    void render(GuiGraphics graphics, int mouseX, int mouseY, List<CraftingPlanSummaryEntry> entries, int scroll) {
        var font = Minecraft.getInstance().font;
        int color = screen.getStyle().getColor(PaletteColor.DEFAULT_TEXT_COLOR).toARGB();
        int localX = mouseX - screen.getGuiLeft();
        int localY = mouseY - screen.getGuiTop();
        scroll = Math.clamp(scroll, 0, getScrollableRows(entries.size()));
        int start = scroll * COLUMNS;
        for (int i = 0; i < cells.size(); i++) {
            int index = start + i;
            cells.get(i).update(font, index < entries.size() ? entries.get(index) : null, color);
        }
        table.getWidget().render(graphics, mouseX, mouseY, 0);
        hoveredStack = getStackAt(mouseX, mouseY, entries, scroll);
        var hovered = entryAt(entries, scroll, cellAt(localX, localY));
        if (hovered != null) {
            screen.drawTooltipWithHeader(graphics, localX, localY, getEntryTooltip(hovered));
        }
    }

    private static final class PlanCell extends UIElement {
        private final int textColor;
        private final UIElement description = new UIElement();
        private final List<Label> labels = new ArrayList<>();
        private CraftingPlanSummaryEntry entry;

        private PlanCell(int textColor) {
            this.textColor = textColor;
            layout(layout -> layout.width(CELL_WIDTH).height(CELL_HEIGHT));
            description.setAllowHitTest(false);
            // Lay the text out at double resolution and scale about the cell's top left.
            description.transform(transform -> transform.pivot(0.0F, 0.0F).scale(0.5F));
            setDisplay(false);
            var icon = new UIElement();
            icon.setAllowHitTest(false);
            icon.style(style -> style.background((graphics, mouseX, mouseY, left, top, width, height, ticks) -> {
                if (entry != null) {
                    drawPlanIcon(graphics, (int) left, (int) top, entry.getWhat());
                }
            }));
            var missing = new UIElement();
            missing.setAllowHitTest(false);
            missing.style(style -> style.background(LdlibScreens.solid(
                    () -> entry != null && entry.getMissingAmount() > 0 ? 0x1AFF0000 : 0)));
            addChildren(LdlibScreens.at(description, 0, 0, CELL_WIDTH * 2, CELL_HEIGHT * 2),
                    LdlibScreens.at(icon, 48, 3, 16, 16),
                    LdlibScreens.at(missing, 0, 0, ENTRY_WIDTH, ENTRY_HEIGHT));
        }

        private void update(Font font, CraftingPlanSummaryEntry entry, int color) {
            if ((this.entry == null) != (entry == null)) {
                setDisplay(entry != null);
            }
            this.entry = entry;
            if (entry == null) return;

            var lines = getEntryDescription(entry);
            int lineHeight = font.lineHeight + 2;
            int textY = 2 * Math.round((22 - (lines.size() * lineHeight - 2) / 2.0F) / 2);
            while (labels.size() < lines.size()) {
                var label = LdlibScreens.label(Component.empty(), textColor);
                label.setAllowHitTest(false);
                label.textStyle(style -> style.textWrap(TextWrap.NONE));
                labels.add(label);
                description.addChild(label);
            }
            for (int i = 0; i < labels.size(); i++) {
                var label = labels.get(i);
                label.setDisplay(i < lines.size());
                if (i < lines.size()) {
                    var line = lines.get(i);
                    int width = font.width(line);
                    label.setText(line);
                    label.textStyle(style -> style.textColor(color));
                    LdlibScreens.at(label, 46 * 2 - width, textY, width + 1, font.lineHeight);
                    textY += lineHeight;
                }
            }
        }
    }

    private static void drawPlanIcon(GuiGraphics graphics, int x, int y, AEKey key) {
        AEKeyRendering.drawInGui(Minecraft.getInstance(), graphics, x, y, key);
    }

    private static List<Component> getEntryDescription(CraftingPlanSummaryEntry entry) {
        var exact = ExactPlanPresentation.describe(entry, false);
        if (exact != null) return exact;
        List<Component> lines = new ArrayList<>(3);
        if (entry.getStoredAmount() > 0) {
            lines.add(GuiText.FromStorage.text(
                    entry.getWhat().formatAmount(entry.getStoredAmount(), AmountFormat.SLOT)));
        }
        if (entry.getMissingAmount() > 0) {
            lines.add(GuiText.Missing.text(
                    entry.getWhat().formatAmount(entry.getMissingAmount(), AmountFormat.SLOT)));
        }
        if (entry.getCraftAmount() > 0) {
            lines.add(GuiText.ToCraft.text(
                    entry.getWhat().formatAmount(entry.getCraftAmount(), AmountFormat.SLOT)));
        }
        return lines;
    }

    private List<Component> getEntryTooltip(CraftingPlanSummaryEntry entry) {
        var exact = ExactPlanPresentation.describe(entry, true);
        if (exact != null) return exact;
        List<Component> lines = AEKeyRendering.getTooltip(entry.getWhat());
        if (entry.getStoredAmount() > 0) {
            lines.add(GuiText.FromStorage.text(
                    entry.getWhat().formatAmount(entry.getStoredAmount(), AmountFormat.FULL)));
        }
        if (entry.getMissingAmount() > 0) {
            lines.add(GuiText.Missing.text(
                    entry.getWhat().formatAmount(entry.getMissingAmount(), AmountFormat.FULL)));
        }
        if (entry.getCraftAmount() > 0) {
            lines.add(GuiText.ToCraft.text(
                    entry.getWhat().formatAmount(entry.getCraftAmount(), AmountFormat.FULL)));
        }
        return lines;
    }

}

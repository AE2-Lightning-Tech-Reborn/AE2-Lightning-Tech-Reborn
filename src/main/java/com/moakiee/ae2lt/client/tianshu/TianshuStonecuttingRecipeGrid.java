package com.moakiee.ae2lt.client.tianshu;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Supplier;

import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.VirtualScrollerView;
import com.moakiee.ae2lt.client.ldlib.LdlibRowScroller;
import com.moakiee.ae2lt.client.ldlib.LdlibScreens;
import dev.vfyjxf.taffy.style.FlexDirection;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import org.jetbrains.annotations.Nullable;

final class TianshuStonecuttingRecipeGrid {
    static final int CELL_WIDTH = 20;
    static final int CELL_HEIGHT = 22;
    static final int VISIBLE_ROWS = 2;
    private static final SpriteTexture NORMAL = face(140);
    private static final SpriteTexture SELECTED = face(162);
    private static final SpriteTexture HOVER = face(184);

    private final VirtualScrollerView<List<Entry>> rows;
    private List<RecipeHolder<StonecutterRecipe>> recipes = List.of();
    private int columns;

    TianshuStonecuttingRecipeGrid(Function<Entry, Cell> createCell) {
        rows = LdlibScreens.virtualRows(CELL_HEIGHT, entries -> {
            var row = new UIElement().layout(l -> l.widthPercent(100).height(CELL_HEIGHT)
                    .flexDirection(FlexDirection.ROW));
            entries.forEach(entry -> row.addChild(createCell.apply(entry)));
            return row;
        });
    }

    UIElement element() {
        return rows;
    }

    static int cellAt(int x, int y, int columns) {
        if (x < 0 || x >= columns * CELL_WIDTH || y < 0 || y >= VISIBLE_ROWS * CELL_HEIGHT) {
            return -1;
        }
        return y / CELL_HEIGHT * columns + x / CELL_WIDTH;
    }

    void update(List<RecipeHolder<StonecutterRecipe>> values, int columns, LdlibRowScroller scrollbar) {
        if (columns != this.columns || !recipes.equals(values)) {
            this.columns = columns;
            recipes = List.copyOf(values);
            var entries = new ArrayList<Entry>(values.size());
            for (int index = 0; index < recipes.size(); index++) {
                entries.add(new Entry(index, recipes.get(index)));
            }
            var groups = new ArrayList<List<Entry>>();
            for (int start = 0; start < entries.size(); start += columns) {
                groups.add(entries.subList(start, Math.min(start + columns, entries.size())));
            }
            rows.setItems(groups);
        }
        int maxScroll = Math.max(0, rows.getItemCount() - VISIBLE_ROWS);
        scrollbar.setRange(0, maxScroll, VISIBLE_ROWS);
        LdlibScreens.scrollVirtualRows(rows, scrollbar.getCurrentScroll(), maxScroll);
    }

    private static SpriteTexture face(int y) {
        return TianshuEncodingModePanel.PATTERN_MODES_ATLAS.slice(124, y, CELL_WIDTH, CELL_HEIGHT);
    }

    record Entry(int index, RecipeHolder<StonecutterRecipe> recipe) {
    }

    static final class Cell extends Button {
        Cell(Supplier<ItemStack> result, BooleanSupplier selected, @Nullable BooleanSupplier hovered) {
            noText();
            layout(l -> l.width(CELL_WIDTH).height(CELL_HEIGHT).flexShrink(0).paddingAll(0));
            var isHovered = hovered == null ? (BooleanSupplier) this::isHover : hovered;
            var face = LdlibScreens.either(selected, SELECTED, LdlibScreens.either(isHovered, HOVER, NORMAL));
            buttonStyle(s -> s.baseTexture(face).hoverTexture(face).pressedTexture(face));
            var image = new UIElement().setAllowHitTest(false).style(s -> s.background(
                    (graphics, mouseX, mouseY, x, y, width, height, ticks) -> {
                        var stack = result.get();
                        int itemX = (int) x;
                        int itemY = (int) y + (selected.getAsBoolean() || isHovered.getAsBoolean() ? 1 : 0);
                        graphics.renderItem(stack, itemX, itemY);
                        graphics.renderItemDecorations(Minecraft.getInstance().font, stack, itemX, itemY);
                    }));
            addChild(LdlibScreens.at(image, 2, 2, 16, 16));
        }
    }
}

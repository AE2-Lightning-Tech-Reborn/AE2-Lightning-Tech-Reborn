/*
 * Derived from Applied Energistics 2's StonecuttingEncodingPanel.
 * Copyright (c) Applied Energistics 2 contributors.
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */

package com.moakiee.ae2lt.client.tianshu;

import java.util.Objects;

import appeng.client.Point;
import appeng.client.gui.Icon;
import appeng.client.gui.Tooltip;
import appeng.client.gui.WidgetContainer;
import appeng.core.localization.GuiText;
import appeng.menu.SlotSemantics;
import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture;
import com.lowdragmc.lowdraglib2.gui.util.UISoundUtils;
import com.moakiee.ae2lt.client.ldlib.LdlibRowScroller;
import com.moakiee.ae2lt.client.ldlib.LdlibScreens;
import net.minecraft.client.Minecraft;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import org.jetbrains.annotations.Nullable;

final class TianshuStonecuttingEncodingPanel extends TianshuEncodingModePanel {
    private static final SpriteTexture BG =
            PATTERN_MODES_ATLAS.slice(0, 140, 124, 66);
    private static final int COLUMNS = 4;
    private static final int GRID_X = 26;
    private static final int GRID_Y = 12;

    private final LdlibRowScroller scrollbar;
    private final TianshuStonecuttingRecipeGrid recipeGrid;

    TianshuStonecuttingEncodingPanel(
            TianshuPatternEncodingTermScreen<?> screen,
            WidgetContainer widgets) {
        super(screen, widgets, BG);
        scrollbar = LdlibRowScroller.add(widgets, "stonecuttingPatternModeScrollbar");
        scrollbar.setRange(0, 0, COLUMNS);
        scrollbar.setCaptureMouseWheel(false);
        recipeGrid = new TianshuStonecuttingRecipeGrid(this::recipeCell);
        panelRoot.addChild(LdlibScreens.at(recipeGrid.element(), GRID_X, GRID_Y,
                COLUMNS * TianshuStonecuttingRecipeGrid.CELL_WIDTH,
                TianshuStonecuttingRecipeGrid.VISIBLE_ROWS * TianshuStonecuttingRecipeGrid.CELL_HEIGHT));
    }

    @Override
    public void updateBeforeRender() {
        panelUi();
        recipeGrid.update(menu.getStonecuttingRecipes(), COLUMNS, scrollbar);
    }

    private RegistryAccess getRegistryAccess() {
        return Objects.requireNonNull(Minecraft.getInstance().level).registryAccess();
    }

    private TianshuStonecuttingRecipeGrid.Cell recipeCell(TianshuStonecuttingRecipeGrid.Entry entry) {
        var recipe = entry.recipe();
        return new TianshuStonecuttingRecipeGrid.Cell(() -> recipe.value().getResultItem(getRegistryAccess()),
                () -> recipe.id().equals(menu.getStonecuttingRecipeId()), null);
    }

    @Override
    public boolean onMouseDown(Point mousePosition, int button) {
        var recipe = recipeAt(mousePosition.getX(), mousePosition.getY());
        if (recipe == null) {
            return false;
        }
        menu.setStonecuttingRecipeId(recipe.id());
        UISoundUtils.playSound(SoundEvents.UI_STONECUTTER_SELECT_RECIPE);
        return true;
    }

    @Nullable
    @Override
    public Tooltip getTooltip(int mouseX, int mouseY) {
        var recipe = recipeAt(mouseX, mouseY);
        return recipe == null ? null : new Tooltip(screen.getTooltipFromContainerItem(
                recipe.value().getResultItem(getRegistryAccess())));
    }

    @Nullable
    private RecipeHolder<StonecutterRecipe> recipeAt(int mouseX, int mouseY) {
        int cell = TianshuStonecuttingRecipeGrid.cellAt(mouseX - x - GRID_X, mouseY - y - GRID_Y, COLUMNS);
        if (cell < 0) {
            return null;
        }
        var recipes = menu.getStonecuttingRecipes();
        int index = scrollbar.getCurrentScroll() * COLUMNS + cell;
        return index < recipes.size() ? recipes.get(index) : null;
    }

    @Override
    public boolean onMouseWheel(Point mousePosition, double delta) {
        return scrollbar.onMouseWheel(mousePosition, delta);
    }

    @Override
    Icon getIcon() {
        return Icon.TAB_STONECUTTING;
    }

    @Override
    Component getTabTooltip() {
        return GuiText.StonecuttingPattern.text();
    }

    @Override
    public void setVisible(boolean visible) {
        super.setVisible(visible);
        scrollbar.setVisible(visible);
        screen.setSlotsHidden(SlotSemantics.STONECUTTING_INPUT, !visible);
    }
}

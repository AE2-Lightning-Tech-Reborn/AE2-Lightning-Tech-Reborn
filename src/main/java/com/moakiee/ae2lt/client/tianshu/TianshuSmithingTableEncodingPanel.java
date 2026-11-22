/*
 * Derived from Applied Energistics 2's SmithingTableEncodingPanel.
 * Copyright (c) Applied Energistics 2 contributors.
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */

package com.moakiee.ae2lt.client.tianshu;

import com.moakiee.ae2lt.client.ldlib.LdlibAe2Button;
import appeng.client.gui.Icon;
import appeng.api.config.ActionItems;
import appeng.client.gui.WidgetContainer;
import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture;
import appeng.core.localization.GuiText;
import appeng.menu.SlotSemantics;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmithingRecipeInput;

final class TianshuSmithingTableEncodingPanel extends TianshuEncodingModePanel {
    private static final SpriteTexture BG =
            PATTERN_MODES_ATLAS.slice(128, 70, 124, 66);

    private final LdlibAe2Button clearButton;
    private final LdlibAe2Button substitutionsButton;
    private final Slot resultSlot;

    TianshuSmithingTableEncodingPanel(
            TianshuPatternEncodingTermScreen<?> screen,
            WidgetContainer widgets) {
        super(screen, widgets, BG);

        clearButton = LdlibAe2Button.small(Icon.S_CLEAR, ActionItems.S_CLOSE, menu::clear);
        widgets.add("smithingTableClearPattern", clearButton);

        substitutionsButton = createItemSubstitutionButton("smithingTableSubstitutions");
        resultSlot = new Slot(new SimpleContainer(1), 0, 0, 0);
        menu.addClientSideSlot(resultSlot, SlotSemantics.SMITHING_TABLE_RESULT);
    }

    @Override
    Icon getIcon() {
        return Icon.TAB_SMITHING;
    }

    @Override
    Component getTabTooltip() {
        return GuiText.SmithingTablePattern.text();
    }

    @Override
    public void updateBeforeRender() {
        var recipeInput = new SmithingRecipeInput(
                menu.getSmithingTableTemplateSlot().getItem(),
                menu.getSmithingTableBaseSlot().getItem(),
                menu.getSmithingTableAdditionSlot().getItem());
        var level = menu.getPlayer().level();
        var recipe = level.getRecipeManager()
                .getRecipeFor(RecipeType.SMITHING, recipeInput, level)
                .orElse(null);
        resultSlot.set(recipe == null
                ? ItemStack.EMPTY
                : recipe.value().assemble(recipeInput, level.registryAccess()));
    }

    @Override
    public void setVisible(boolean visible) {
        super.setVisible(visible);
        clearButton.setVisibility(visible);
        substitutionsButton.setVisibility(visible);
        screen.setSlotsHidden(SlotSemantics.SMITHING_TABLE_TEMPLATE, !visible);
        screen.setSlotsHidden(SlotSemantics.SMITHING_TABLE_BASE, !visible);
        screen.setSlotsHidden(SlotSemantics.SMITHING_TABLE_ADDITION, !visible);
        screen.setSlotsHidden(SlotSemantics.SMITHING_TABLE_RESULT, !visible);
    }
}

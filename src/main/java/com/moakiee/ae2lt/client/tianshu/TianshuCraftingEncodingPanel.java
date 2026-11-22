/*
 * Derived from Applied Energistics 2's CraftingEncodingPanel.
 * Copyright (c) Applied Energistics 2 contributors.
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */

package com.moakiee.ae2lt.client.tianshu;

import com.moakiee.ae2lt.client.ldlib.LdlibAe2Button;
import appeng.client.Point;
import appeng.client.gui.Icon;
import appeng.api.config.ActionItems;
import appeng.client.gui.WidgetContainer;
import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture;
import com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.moakiee.ae2lt.client.ldlib.LdlibScreens;
import appeng.core.localization.ButtonToolTips;
import appeng.core.localization.GuiText;
import appeng.menu.SlotSemantics;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;

final class TianshuCraftingEncodingPanel extends TianshuEncodingModePanel {
    private static final SpriteTexture BG =
            PATTERN_MODES_ATLAS.slice(0, 0, 124, 66);
    private static final ColorRectTexture FLUID_SUBSTITUTION_HIGHLIGHT = new ColorRectTexture(0xFF7AC25F);

    private final LdlibAe2Button clearButton;
    private final LdlibAe2Button substitutionsButton;
    private final LdlibAe2Button fluidSubstitutionsButton;
    private final UIElement[] fluidHighlights;

    TianshuCraftingEncodingPanel(
            TianshuPatternEncodingTermScreen<?> screen,
            WidgetContainer widgets) {
        super(screen, widgets, BG);
        fluidHighlights = new UIElement[menu.getCraftingGridSlots().length];
        for (int i = 0; i < fluidHighlights.length; i++) {
            var highlight = new UIElement().setAllowHitTest(false).setVisible(false)
                    .style(style -> style.background(FLUID_SUBSTITUTION_HIGHLIGHT));
            fluidHighlights[i] = highlight;
            panelRoot.addChild(LdlibScreens.at(highlight, 0, 0, 16, 16));
        }

        clearButton = LdlibAe2Button.small(Icon.S_CLEAR, ActionItems.S_CLOSE, menu::clear);
        widgets.add("craftingClearPattern", clearButton);

        substitutionsButton = createItemSubstitutionButton("craftingSubstitutions");
        fluidSubstitutionsButton = createCraftingFluidSubstitutionButton();
    }

    @Override
    Icon getIcon() {
        return Icon.TAB_CRAFTING;
    }

    @Override
    Component getTabTooltip() {
        return GuiText.CraftingPattern.text();
    }

    private LdlibAe2Button createCraftingFluidSubstitutionButton() {
        var label = ButtonToolTips.FluidSubstitutions.text();
        var enabled = List.<Component>of(label, ButtonToolTips.FluidSubstitutionsDescEnabled.text());
        var disabled = List.<Component>of(label, ButtonToolTips.FluidSubstitutionsDescDisabled.text());
        var button = LdlibAe2Button.bareIcon(8,
                () -> menu.substituteFluids ? Icon.S_FLUID_SUBSTITUTION_ENABLED : Icon.S_FLUID_SUBSTITUTION_DISABLED,
                label, () -> menu.setSubstituteFluids(!menu.substituteFluids),
                () -> menu.substituteFluids ? enabled : disabled);
        widgets.add("craftingFluidSubstitutions", button);
        return button;
    }

    @Override
    public void drawBackgroundLayer(GuiGraphics graphics, Rect2i bounds, Point mouse) {
        var absoluteMouseX = bounds.getX() + mouse.getX();
        var absoluteMouseY = bounds.getY() + mouse.getY();
        boolean showHighlights = menu.substituteFluids
                && fluidSubstitutionsButton.isMouseOver(absoluteMouseX, absoluteMouseY);
        var slots = menu.getCraftingGridSlots();
        for (int i = 0; i < fluidHighlights.length; i++) {
            var highlight = fluidHighlights[i];
            boolean show = showHighlights && menu.slotsSupportingFluidSubstitution.contains(i);
            highlight.setVisible(show);
            if (show) {
                var slot = slots[i];
                highlight.layout(layout -> layout.left(slot.x - x).top(slot.y - y));
            }
        }
        super.drawBackgroundLayer(graphics, bounds, mouse);
    }

    @Override
    public void setVisible(boolean visible) {
        super.setVisible(visible);
        clearButton.setVisibility(visible);
        substitutionsButton.setVisibility(visible);
        fluidSubstitutionsButton.setVisibility(visible);
        screen.setSlotsHidden(SlotSemantics.CRAFTING_GRID, !visible);
        screen.setSlotsHidden(SlotSemantics.CRAFTING_RESULT, !visible);
    }
}

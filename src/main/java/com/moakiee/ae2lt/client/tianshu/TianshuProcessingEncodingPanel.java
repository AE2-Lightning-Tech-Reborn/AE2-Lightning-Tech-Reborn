/*
 * Derived from Applied Energistics 2's ProcessingEncodingPanel.
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
import com.moakiee.ae2lt.client.ldlib.LdlibRowScroller;
import appeng.core.localization.GuiText;
import appeng.menu.SlotSemantics;
import net.minecraft.network.chat.Component;

final class TianshuProcessingEncodingPanel extends TianshuEncodingModePanel {
    private static final SpriteTexture BG =
            PATTERN_MODES_ATLAS.slice(0, 70, 124, 66);

    private final LdlibAe2Button clearButton;
    private final LdlibAe2Button cycleOutputButton;
    private final LdlibRowScroller scrollbar;

    TianshuProcessingEncodingPanel(
            TianshuPatternEncodingTermScreen<?> screen,
            WidgetContainer widgets) {
        super(screen, widgets, BG);

        clearButton = LdlibAe2Button.small(Icon.S_CLEAR, ActionItems.S_CLOSE, menu::clear);
        widgets.add("processingClearPattern", clearButton);

        cycleOutputButton = LdlibAe2Button.small(Icon.S_CYCLE,
                ActionItems.S_CYCLE_PROCESSING_OUTPUT, menu::cycleProcessingOutput);
        widgets.add("processingCycleOutput", cycleOutputButton);

        scrollbar = LdlibRowScroller.add(widgets, "processingPatternModeScrollbar");
        scrollbar.setRange(0, menu.getProcessingInputSlots().length / 3 - 3, 3);
        scrollbar.setCaptureMouseWheel(false);
    }

    @Override
    public void updateBeforeRender() {
        screen.repositionSlots(SlotSemantics.PROCESSING_INPUTS);
        screen.repositionSlots(SlotSemantics.PROCESSING_OUTPUTS);

        for (int i = 0; i < menu.getProcessingInputSlots().length; i++) {
            var slot = menu.getProcessingInputSlots()[i];
            var effectiveRow = i / 3 - scrollbar.getCurrentScroll();
            slot.setActive(effectiveRow >= 0 && effectiveRow < 3);
            slot.y -= scrollbar.getCurrentScroll() * 18;
        }
        for (int i = 0; i < menu.getProcessingOutputSlots().length; i++) {
            var slot = menu.getProcessingOutputSlots()[i];
            var effectiveRow = i - scrollbar.getCurrentScroll();
            slot.setActive(effectiveRow >= 0 && effectiveRow < 3);
            slot.y -= scrollbar.getCurrentScroll() * 18;
        }

        updateTooltipVisibility();
    }

    @Override
    public boolean onMouseWheel(Point mousePosition, double delta) {
        return scrollbar.onMouseWheel(mousePosition, delta);
    }

    private void updateTooltipVisibility() {
        widgets.setTooltipAreaEnabled(
                "processing-primary-output",
                visible && scrollbar.getCurrentScroll() == 0);
        widgets.setTooltipAreaEnabled(
                "processing-optional-output1",
                visible && scrollbar.getCurrentScroll() > 0);
        widgets.setTooltipAreaEnabled("processing-optional-output2", visible);
        widgets.setTooltipAreaEnabled("processing-optional-output3", visible);
    }

    @Override
    Icon getIcon() {
        return Icon.TAB_PROCESSING;
    }

    @Override
    Component getTabTooltip() {
        return GuiText.ProcessingPattern.text();
    }

    @Override
    public void setVisible(boolean visible) {
        super.setVisible(visible);
        scrollbar.setVisible(visible);
        clearButton.setVisibility(visible);
        cycleOutputButton.setVisibility(visible && menu.canCycleProcessingOutputs());
        screen.setSlotsHidden(SlotSemantics.PROCESSING_INPUTS, !visible);
        screen.setSlotsHidden(SlotSemantics.PROCESSING_OUTPUTS, !visible);
        updateTooltipVisibility();
    }
}

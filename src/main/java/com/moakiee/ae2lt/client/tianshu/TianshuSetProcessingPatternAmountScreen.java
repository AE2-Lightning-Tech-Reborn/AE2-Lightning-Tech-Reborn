/*
 * Derived from Applied Energistics 2's SetProcessingPatternAmountScreen.
 * Copyright (c) 2013-2014 AlgorithmX2 and Applied Energistics 2 contributors.
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */

package com.moakiee.ae2lt.client.tianshu;

import appeng.api.stacks.GenericStack;
import com.moakiee.ae2lt.client.ldlib.LdlibAeSubScreen;
import appeng.client.gui.NumberEntryType;
import appeng.client.gui.me.common.ClientDisplaySlot;
import appeng.core.localization.GuiText;
import appeng.menu.SlotSemantics;
import com.google.common.primitives.Longs;
import com.moakiee.ae2lt.client.ldlib.LdlibAe2Button;
import com.moakiee.ae2lt.client.ldlib.LdlibNumberEntryWidget;
import com.moakiee.ae2lt.menu.TianshuPatternEncodingTermMenu;
import guideme.PageAnchor;
import java.util.function.Consumer;

final class TianshuSetProcessingPatternAmountScreen<M extends TianshuPatternEncodingTermMenu>
        extends LdlibAeSubScreen<M, TianshuPatternEncodingTermScreen<M>> {
    private final LdlibNumberEntryWidget amount;
    private final GenericStack currentStack;
    private final Consumer<GenericStack> setter;

    TianshuSetProcessingPatternAmountScreen(
            TianshuPatternEncodingTermScreen<M> parentScreen,
            GenericStack currentStack,
            Consumer<GenericStack> setter) {
        super(parentScreen, "/screens/set_processing_pattern_amount.json");
        this.currentStack = currentStack;
        this.setter = setter;

        LdlibAe2Button.addText(widgets, style, "save", GuiText.Set.text(), this::confirm);

        var icon = getMenu().getHost().getMainMenuIcon();
        var button = LdlibAe2Button.back(icon.getHoverName(), this::returnToParent);
        widgets.add("back", button);

        amount = new LdlibNumberEntryWidget(style, NumberEntryType.of(currentStack.what()), false);
        widgets.add("amountToStock", amount);
        amount.setLongValue(currentStack.amount());
        amount.setMaxValue(getMaxAmount());
        amount.setTextFieldStyle(style.getWidget("amountToStockInput"));
        amount.setMinValue(0);
        amount.setOnConfirm(this::confirm);

        addClientSideSlot(new ClientDisplaySlot(currentStack), SlotSemantics.MACHINE_OUTPUT);
    }

    @Override
    protected PageAnchor getHelpTopic() {
        return getParent().getHelpTopic();
    }

    @Override
    protected void onReturnToParent() {
        try {
            amount.releaseUnitLayer();
        } finally {
            super.onReturnToParent();
        }
    }

    @Override
    public void removed() {
        try {
            amount.releaseUnitLayer();
        } finally {
            super.removed();
        }
    }

    @Override
    protected void init() {
        super.init();
        setSlotsHidden(SlotSemantics.TOOLBOX, true);
    }

    private void confirm() {
        amount.getLongValue().ifPresent(newAmount -> {
            var constrainedAmount = Longs.constrainToRange(newAmount, 0, getMaxAmount());
            setter.accept(constrainedAmount <= 0
                    ? null
                    : new GenericStack(currentStack.what(), constrainedAmount));
            returnToParent();
        });
    }

    private long getMaxAmount() {
        return 999999L * currentStack.what().getAmountPerUnit();
    }
}

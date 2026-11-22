/*
 * Layout derived from Applied Energistics 2's ProcessingEncodingPanel.
 * Copyright (c) Applied Energistics 2 contributors.
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.moakiee.ae2lt.client.tianshu;

import com.moakiee.ae2lt.client.ldlib.LdlibAe2Button;
import appeng.client.gui.Icon;
import appeng.api.config.ActionItems;
import appeng.api.client.AEKeyRendering;
import appeng.api.stacks.AmountFormat;
import appeng.api.stacks.GenericStack;
import appeng.client.Point;
import appeng.client.gui.ICompositeWidget;
import appeng.client.gui.WidgetContainer;
import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.moakiee.ae2lt.client.ldlib.LdlibScreens;
import com.moakiee.ae2lt.client.ldlib.LdlibRowScroller;
import com.moakiee.ae2lt.integration.useless.UselessModCompat;
import com.moakiee.ae2lt.logic.tianshu.terminal.OmniversalPatternDraft;
import com.moakiee.ae2lt.menu.TianshuPatternEncodingTermMenu;
import com.moakiee.ae2lt.menu.Ae2ltSlotSemantics;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;

/** Editable inputs, outputs and mold filters, validated together when encoding. */
final class TianshuOmniversalEncodingPanel implements ICompositeWidget {
    private static final int ROWS = 3;
    private static final int WIDTH = 124;
    private static final int HEIGHT = 66;
    private static final SpriteTexture BG =
            TianshuEncodingModePanel.PATTERN_MODES_ATLAS.slice(0, 70, 124, 66);
    private static final SpriteTexture ARROW_COVER =
            TianshuEncodingModePanel.PATTERN_MODES_ATLAS.slice(73, 77, 23, 16);
    private static final SpriteTexture MOLD_SLOT =
            TianshuEncodingModePanel.PATTERN_MODES_ATLAS.slice(15, 76, 18, 18);
    private final TianshuPatternEncodingTermMenu menu;
    private final TianshuPatternEncodingTermScreen<?> screen;
    private final LdlibRowScroller scrollbar;
    private final ModularUI panelUi;
    private final LdlibAe2Button clearButton;
    private final LdlibAe2Button cycleOutputButton;
    private OmniversalPatternDraft observedDraft;
    private long observedGeneration = Long.MIN_VALUE;
    private UselessModCompat.Preview preview = UselessModCompat.Preview.EMPTY;
    private boolean visible;
    private int x;
    private int y;

    TianshuOmniversalEncodingPanel(TianshuPatternEncodingTermScreen<?> screen,
                                  WidgetContainer widgets) {
        this.screen = screen;
        menu = screen.getMenu();
        scrollbar = LdlibRowScroller.add(widgets, "omniversalScrollbar");
        scrollbar.setCaptureMouseWheel(false);
        scrollbar.setRange(0, menu.getOmniversalInputSlots().length / 3 - ROWS, 1);
        clearButton = LdlibAe2Button.small(Icon.S_CLEAR, ActionItems.S_CLOSE, menu::clearOmniversalDraft);
        widgets.add("omniversalClear", clearButton);
        cycleOutputButton = LdlibAe2Button.small(Icon.S_CYCLE,
                ActionItems.S_CYCLE_PROCESSING_OUTPUT, menu::cycleOmniversalOutput);
        widgets.add("omniversalCycleOutput", cycleOutputButton);
        panelUi = buildPanel();
    }

    @Override public void setPosition(Point position) { x = position.getX(); y = position.getY(); }
    @Override public void setSize(int width, int height) { }
    @Override public Rect2i getBounds() { return new Rect2i(x, y, WIDTH, HEIGHT); }
    @Override public boolean isVisible() { return visible; }

    void setVisible(boolean visible) {
        this.visible = visible;
        scrollbar.setVisible(visible);
        clearButton.setVisibility(visible);
        cycleOutputButton.setVisibility(visible && menu.canCycleOmniversalOutputs());
        screen.setSlotsHidden(Ae2ltSlotSemantics.TIANSHU_OMNIVERSAL_INPUTS, !visible);
        screen.setSlotsHidden(Ae2ltSlotSemantics.TIANSHU_OMNIVERSAL_OUTPUTS, !visible);
        screen.setSlotsHidden(Ae2ltSlotSemantics.TIANSHU_OMNIVERSAL_MOLDS, !visible);
    }

    @Override
    public void updateBeforeRender() {
        if (!visible) return;
        long generation = UselessModCompat.recipeGeneration();
        if (!menu.omniversalDraft.equals(observedDraft) || generation != observedGeneration) {
            observedDraft = menu.omniversalDraft;
            observedGeneration = generation;
            preview = UselessModCompat.preview(observedDraft, Minecraft.getInstance().level);
        }
        int scroll = scrollbar.getCurrentScroll();
        for (int i = 0; i < menu.getOmniversalInputSlots().length; i++) {
            var slot = menu.getOmniversalInputSlots()[i];
            int row = i / 3 - scroll;
            slot.setActive(row >= 0 && row < ROWS);
            slot.x = x + columnX(i % 3);
            slot.y = y + 8 + row * 18;
        }
        for (int i = 0; i < menu.getOmniversalOutputSlots().length; i++) {
            var slot = menu.getOmniversalOutputSlots()[i];
            int row = i - scroll;
            slot.setActive(row >= 0 && row < ROWS);
            slot.x = x + columnX(4);
            slot.y = y + 8 + row * 18;
        }
        for (int i = 0; i < menu.getOmniversalMoldSlots().length; i++) {
            var slot = menu.getOmniversalMoldSlots()[i];
            int row = i - scroll;
            slot.setActive(row >= 0 && row < ROWS);
            slot.x = x + columnX(3);
            slot.y = y + 8 + row * 18;
        }
    }

    private ModularUI buildPanel() {
        var root = new UIElement();
        root.addChildren(sprite(BG, -1, 1, WIDTH, HEIGHT), sprite(ARROW_COVER, 72, 26, 23, 16));
        for (int row = 0; row < ROWS; row++) {
            root.addChild(sprite(MOLD_SLOT, columnX(3) - 1, 7 + row * 18, 18, 18));
        }
        var ui = LdlibScreens.localLayer(root, WIDTH, HEIGHT);
        ui.setDrawTooltips(false);
        return ui;
    }

    private static UIElement sprite(SpriteTexture texture, int x, int y, int width, int height) {
        var element = new UIElement().setAllowHitTest(false).style(style -> style.background(texture));
        return LdlibScreens.at(element, x, y, width, height);
    }

    @Override
    public void drawBackgroundLayer(GuiGraphics graphics, Rect2i bounds, Point mouse) {
        var pose = graphics.pose();
        pose.pushPose();
        try {
            pose.translate(bounds.getX() + x, bounds.getY() + y, 0);
            panelUi.getWidget().render(graphics, mouse.getX() - x, mouse.getY() - y, 0);
        } finally {
            pose.popPose();
        }
    }

    List<Component> tooltipAt(int mouseX, int mouseY) {
        int localX = mouseX - x;
        int localY = mouseY - y;
        if (!visible || localX < 0 || localX >= WIDTH || localY < 0 || localY >= HEIGHT) {
            return List.of();
        }
        if (localY >= 8 && localY < 8 + ROWS * 18) {
            int row = (localY - 8) / 18;
            for (int column = 0; column < 5; column++) {
                int left = columnX(column);
                if (localX >= left && localX < left + 16) {
                    return column == 3 ? moldTooltip(row) : List.of();
                }
            }
        }
        return statusTooltip();
    }

    private List<Component> moldTooltip(int visibleRow) {
        var stack = moldAt(scrollbar.getCurrentScroll() + visibleRow);
        if (stack == null) {
            return statusTooltip();
        }
        var lines = new ArrayList<>(AEKeyRendering.getTooltip(stack.what()));
        lines.add(Component.literal(stack.what().formatAmount(stack.amount(), AmountFormat.FULL))
                .withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("ae2lt.tianshu.omniversal.molds.tooltip").withStyle(ChatFormatting.GOLD));
        return lines;
    }

    private List<Component> statusTooltip() {
        var lines = new ArrayList<Component>();
        lines.add(Component.translatable(menu.omniversalStatus));
        if (!preview.recipeId().isEmpty()) lines.add(Component.literal(preview.recipeId())
                .withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("ae2lt.tianshu.omniversal.encode_upload.tooltip")
                .withStyle(ChatFormatting.GRAY));
        return lines;
    }

    private GenericStack moldAt(int row) {
        return row < menu.getOmniversalMoldSlots().length
                ? GenericStack.fromItemStack(menu.getOmniversalMoldSlots()[row].getItem()) : null;
    }

    private static int columnX(int column) { return column < 3 ? 15 + column * 18 : column == 3 ? 77 : 100; }

    @Override
    public boolean onMouseWheel(Point mousePosition, double delta) {
        return scrollbar.onMouseWheel(mousePosition, delta);
    }
}

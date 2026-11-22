/*
 * Derived from Applied Energistics 2's encoding mode panels.
 * Copyright (c) Applied Energistics 2 contributors.
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */

package com.moakiee.ae2lt.client.tianshu;

import java.util.List;

import appeng.client.Point;
import appeng.client.gui.ICompositeWidget;
import appeng.client.gui.Icon;
import appeng.client.gui.WidgetContainer;
import appeng.core.localization.ButtonToolTips;
import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.moakiee.ae2lt.client.ldlib.LdlibAe2Button;
import com.moakiee.ae2lt.client.ldlib.LdlibScreens;
import com.moakiee.ae2lt.client.ldlib.LdlibStyleSprite;
import com.moakiee.ae2lt.menu.TianshuPatternEncodingTermMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

abstract class TianshuEncodingModePanel implements ICompositeWidget {
    static final LdlibStyleSprite PATTERN_MODES_ATLAS = new LdlibStyleSprite(
            ResourceLocation.parse("ae2:textures/guis/pattern_modes.png"), 256, 256);
    private static final int WIDTH = 124;
    private static final int HEIGHT = 66;

    protected final TianshuPatternEncodingTermScreen<?> screen;
    protected final TianshuPatternEncodingTermMenu menu;
    protected final WidgetContainer widgets;
    protected final UIElement panelRoot = new UIElement();
    private final UIElement background;
    private ModularUI panelUi;
    protected boolean visible;
    protected int x;
    protected int y;

    TianshuEncodingModePanel(
            TianshuPatternEncodingTermScreen<?> screen,
            WidgetContainer widgets,
            SpriteTexture texture) {
        this.screen = screen;
        this.menu = screen.getMenu();
        this.widgets = widgets;
        background = new UIElement().setAllowHitTest(false).style(style -> style.background(texture));
        panelRoot.addChild(LdlibScreens.at(background, 0, 0, WIDTH, HEIGHT));
    }

    protected final ModularUI panelUi() {
        if (panelUi == null) {
            panelUi = LdlibScreens.localLayer(panelRoot, WIDTH, HEIGHT);
            panelUi.setDrawTooltips(false);
        }
        return panelUi;
    }
    abstract Icon getIcon();

    abstract Component getTabTooltip();

    protected final LdlibAe2Button createItemSubstitutionButton(String widgetId) {
        var enabled = List.<Component>of(ButtonToolTips.SubstitutionsOn.text(),
                ButtonToolTips.SubstitutionsDescEnabled.text());
        var disabled = List.<Component>of(ButtonToolTips.SubstitutionsOff.text(),
                ButtonToolTips.SubstitutionsDescDisabled.text());
        var button = LdlibAe2Button.bareIcon(8,
                () -> menu.substitute ? Icon.S_SUBSTITUTION_ENABLED : Icon.S_SUBSTITUTION_DISABLED,
                enabled.getFirst(), () -> menu.setSubstitute(!menu.substitute),
                () -> menu.substitute ? enabled : disabled);
        widgets.add(widgetId, button);
        return button;
    }

    @Override
    public void setPosition(Point position) {
        x = position.getX();
        y = position.getY();
    }

    @Override
    public void setSize(int width, int height) {
    }

    @Override
    public Rect2i getBounds() {
        return new Rect2i(x, y, WIDTH, HEIGHT);
    }

    @Override
    public void drawBackgroundLayer(GuiGraphics graphics, Rect2i bounds, Point mouse) {
        // The sprite is anchored to the terminal, while panel content follows its widget position.
        background.layout(layout -> layout.left(8 - x).top(bounds.getHeight() - 165 - y));
        var pose = graphics.pose();
        pose.pushPose();
        try {
            pose.translate(bounds.getX() + x, bounds.getY() + y, 0);
            panelUi().getWidget().render(graphics, bounds.getX() + mouse.getX(), bounds.getY() + mouse.getY(), 0);
        } finally {
            pose.popPose();
        }
    }

    @Override
    public final boolean isVisible() {
        return visible;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }
}

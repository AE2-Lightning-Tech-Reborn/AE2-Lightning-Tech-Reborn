package com.moakiee.ae2lt.client.ldlib;

import org.jetbrains.annotations.Nullable;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.menu.AEBaseMenu;
import com.moakiee.ae2lt.mixin.client.WidgetContainerAccessor;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** AE2 screen with an LDLib background sourced from its screen style. */
public abstract class LdlibAeScreen<M extends AEBaseMenu> extends AEBaseScreen<M> implements LdlibToolbarHost {
    private final @Nullable BackgroundLayer background;

    protected LdlibAeScreen(M menu, Inventory inventory, Component title, ScreenStyle style) {
        super(menu, inventory, title, style);
        background = BackgroundLayer.fromStyle(style);
    }

    protected final void releaseBackground() {
        if (background != null) {
            background.onRemoved();
        }
    }

    @Override
    protected void init() {
        releaseBackground();
        super.init();
    }

    protected final void releaseUpgradePanels() {
        for (var widget : ((WidgetContainerAccessor) widgets).ae2lt$getCompositeWidgets().values()) {
            if (widget instanceof LdlibUpgradesPanel panel) {
                panel.onRemoved();
            }
        }
    }

    @Override
    public void removed() {
        try {
            releaseUpgradePanels();
        } finally {
            try {
                releaseBackground();
            } finally {
                super.removed();
            }
        }
    }

    @Override
    public void drawBG(GuiGraphics graphics, int x, int y, int mouseX, int mouseY, float ticks) {
        if (background == null) {
            super.drawBG(graphics, x, y, mouseX, mouseY, ticks);
        } else {
            background.render(graphics, x, y, mouseX, mouseY, ticks);
        }
    }
}

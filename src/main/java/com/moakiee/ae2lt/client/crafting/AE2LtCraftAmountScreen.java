package com.moakiee.ae2lt.client.crafting;

import appeng.client.gui.me.crafting.CraftAmountScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.menu.me.crafting.CraftAmountMenu;
import com.moakiee.ae2lt.client.ldlib.BackgroundLayer;
import com.moakiee.ae2lt.client.ldlib.LdlibNumberEntryWidget;
import com.moakiee.ae2lt.client.ldlib.LdlibToolbarHost;
import com.moakiee.ae2lt.mixin.client.WidgetContainerAccessor;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Keeps the native crafting amount screen contract while opting into LDLib controls. */
public final class AE2LtCraftAmountScreen extends CraftAmountScreen implements LdlibToolbarHost {
    private final BackgroundLayer background;

    public AE2LtCraftAmountScreen(CraftAmountMenu menu, Component title, ScreenStyle style) {
        super(menu, menu.getPlayerInventory(), title, style);
        background = BackgroundLayer.fromStyle(style);
    }

    private void releaseAmountLayers() {
        try {
            var amount = ((WidgetContainerAccessor) widgets).ae2lt$getCompositeWidgets().get("amountToCraft");
            if (amount instanceof LdlibNumberEntryWidget ldlib) {
                ldlib.releaseUnitLayer();
            }
        } finally {
            if (background != null) {
                background.onRemoved();
            }
        }
    }

    @Override
    protected void init() {
        releaseAmountLayers();
        super.init();
    }

    @Override
    public void removed() {
        try {
            releaseAmountLayers();
        } finally {
            super.removed();
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

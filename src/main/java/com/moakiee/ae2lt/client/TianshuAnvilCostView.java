package com.moakiee.ae2lt.client;

import com.moakiee.ae2lt.menu.TianshuCraftingTermMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Embeds the native anvil cost renderer, including modifications to AnvilScreen. */
final class TianshuAnvilCostView extends AnvilScreen {
    private final TianshuCraftingTermMenu terminalMenu;

    TianshuAnvilCostView(TianshuCraftingTermMenu terminalMenu, Inventory inventory) {
        super(terminalMenu.getAnvil(), inventory, Component.empty());
        this.terminalMenu = terminalMenu;
        // The terminal owns labels, widgets, slot clicks and rename packets. This view is never
        // opened or initialized as a screen, so it adds no listeners or native rename widget.
        titleLabelY = inventoryLabelY = -10000;
    }

    void renderCost(GuiGraphicsExtractor graphics, int x, int y, int availableWidth, int mouseX, int mouseY) {
        // Slot synchronization can recalculate the client-side engine after the cost arrives.
        // Restore the authoritative cost before the native renderer consults this same engine.
        menu.setData(0, terminalMenu.anvilCost);
        // Measure both standard labels only to fit the narrow WT pane; the parent still chooses what to display.
        int labelWidth = Math.max(font.width(Component.translatable("container.repair.cost", terminalMenu.anvilCost)),
                font.width(Component.translatable("container.repair.expensive")));
        float scale = Math.min(1.0f, (availableWidth - 4.0f) / Math.max(1, labelWidth));
        imageWidth = (int) Math.floor(availableWidth / scale) + 8;
        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y);
        graphics.pose().scale(scale, scale);
        graphics.pose().translate(0, -69);
        try {
            super.extractLabels(graphics, mouseX, mouseY);
        } finally {
            graphics.pose().popMatrix();
        }
    }
}

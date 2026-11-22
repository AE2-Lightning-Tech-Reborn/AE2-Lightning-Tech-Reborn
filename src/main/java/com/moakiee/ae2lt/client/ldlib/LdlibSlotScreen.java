package com.moakiee.ae2lt.client.ldlib;

import com.lowdragmc.lowdraglib2.gui.holder.IModularUIHolderMenu;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ItemSlot;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;

/** Keeps Minecraft's slot interactions aligned with the LDLib views embedded in the menu. */
public abstract class LdlibSlotScreen<M extends AbstractContainerMenu>
        extends AbstractContainerScreen<M> implements LdlibSlotHost {
    protected LdlibSlotScreen(M menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        refreshSlotHover(mouseX, mouseY);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        refreshSlotHover(mouseX, mouseY);
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        refreshSlotHover(mouseX, mouseY);
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        refreshSlotHover(mouseX, mouseY);
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        refreshSlotHover(mouseX, mouseY);
        return super.mouseScrolled(mouseX, mouseY, deltaX, deltaY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        var window = minecraft.getWindow();
        double mouseX = minecraft.mouseHandler.xpos() * window.getGuiScaledWidth() / window.getScreenWidth();
        double mouseY = minecraft.mouseHandler.ypos() * window.getGuiScaledHeight() / window.getScreenHeight();
        refreshSlotHover(mouseX, mouseY);
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void refreshSlotHover(double mouseX, double mouseY) {
        // LDLib's vanilla slot hit test reads the cached hover. Input may arrive before the next draw.
        var ui = ((IModularUIHolderMenu) menu).getModularUI();
        if (ui != null) {
            // This UI is bound to the full screen; its root already includes the container origin.
            ui.refreshHoveredElement((float) mouseX, (float) mouseY);
            // Hotbar swaps and drop shortcuts read vanilla's cached slot directly.
            hoveredSlot = ui.getLastHoveredElement() instanceof ItemSlot view ? view.getSlot() : null;
        }
    }
}

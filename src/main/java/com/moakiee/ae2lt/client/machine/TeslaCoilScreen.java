package com.moakiee.ae2lt.client.machine;

import com.moakiee.ae2lt.client.frequency.FrequencyBindingClient;

import java.util.List;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import appeng.client.gui.style.ScreenStyle;

import com.moakiee.ae2lt.client.gui.LargeStackCountRenderer;
import com.moakiee.ae2lt.client.ldlib.MachineGauges;
import com.moakiee.ae2lt.client.gui.LightningStatusLines;
import com.moakiee.ae2lt.menu.TeslaCoilMenu;

public class TeslaCoilScreen extends RedstoneMachineScreen<TeslaCoilMenu> {
    private TeslaCoilModeButton modeButton;

    public TeslaCoilScreen(TeslaCoilMenu menu, Inventory playerInventory, Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);

        addToLeftToolbar(FrequencyBindingClient.createToolbarButton(menu));

        widgets.add("energyBar", MachineGauges.energyBar(style.getImage("energyBar"),
                menu::getStoredEnergy, menu::getEnergyCapacity,
                "ae2lt.gui.tesla_coil.energy.tooltip"));

        widgets.add("lightningStatus", MachineGauges.statusIcon(() -> List.of(
                LightningStatusLines.title(),
                menu.getStatusMessage(),
                LightningStatusLines.progress(menu.getProgress()),
                LightningStatusLines.energy(menu.getStoredEnergy(), menu.getEnergyCapacity()),
                LightningStatusLines.highVoltage(menu.getHighVoltageAvailable()),
                LightningStatusLines.extremeHighVoltage(menu.getExtremeHighVoltageAvailable()),
                menu.getMatrixMessage())));

        this.modeButton = new TeslaCoilModeButton(menu::clientCycleMode);
        addToLeftToolbar(this.modeButton);
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();

        if (modeButton != null) {
            modeButton.setMode(menu.getMode());
        }
    }

    @Override
    public void renderSlot(GuiGraphics guiGraphics, Slot slot) {
        super.renderSlot(guiGraphics, slot);
        LargeStackCountRenderer.renderSlotCount(guiGraphics, font, slot);
    }

    @Override
    protected List<Component> getTooltipFromContainerItem(ItemStack stack) {
        var lines = super.getTooltipFromContainerItem(stack);
        LargeStackCountRenderer.appendCountTooltip(lines, hoveredSlot);
        return lines;
    }
}

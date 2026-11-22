package com.moakiee.ae2lt.client.machine;

import com.moakiee.ae2lt.client.ldlib.LdlibAeScreen;
import com.moakiee.ae2lt.client.frequency.FrequencyBindingClient;

import java.util.List;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import appeng.client.gui.Icon;
import appeng.client.gui.style.ScreenStyle;
import com.moakiee.ae2lt.client.ldlib.LdlibAe2Button;
import com.moakiee.ae2lt.client.ldlib.LdlibToolboxPanel;
import com.moakiee.ae2lt.client.ldlib.LdlibUpgradesPanel;
import appeng.menu.SlotSemantics;

import com.moakiee.ae2lt.client.gui.LargeStackCountRenderer;
import com.moakiee.ae2lt.client.ldlib.MachineGauges;
import com.moakiee.ae2lt.client.gui.LightningStatusLines;
import com.moakiee.ae2lt.menu.LightningAssemblyChamberMenu;

public class LightningAssemblyChamberScreen extends LdlibAeScreen<LightningAssemblyChamberMenu> {

    private final LdlibAe2Button configureOutputButton;

    public LightningAssemblyChamberScreen(
            LightningAssemblyChamberMenu menu, Inventory playerInventory, Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);
        this.imageWidth = 176;
        this.imageHeight = 198;

        widgets.add("upgrades", LdlibUpgradesPanel.forMachine(
                menu.getSlots(SlotSemantics.UPGRADE),
                () -> menu.getHost().getUpgrades().getUpgradableItem()));

        // 网络工具适配：玩家手持 NetworkTool 打开 GUI 时显示 toolbox 面板
        if (menu.getToolbox().isPresent()) {
            this.widgets.add("toolbox", new LdlibToolboxPanel(style, menu.getToolbox().getName()));
        }

        addToLeftToolbar(FrequencyBindingClient.createToolbarButton(menu));

        widgets.add("processArea", MachineGauges.horizontalProgress(
                style.getImage("processOverlay"), menu::getProgress, true));

        widgets.add("energyBar", MachineGauges.energyBar(style.getImage("energyBar"),
                menu::getStoredEnergy, menu::getEnergyCapacity,
                "ae2lt.gui.lightning_assembly.energy.tooltip"));

        addToLeftToolbar(new InputTransferButton(menu));
        addToLeftToolbar(new MachineAutoExportButton("ae2lt.gui.lightning_assembly.auto_export",
                menu::isAutoExportEnabled, menu::clientToggleAutoExport));

        this.configureOutputButton = LdlibAe2Button.toolbar(
                Icon.COG, Component.translatable("ae2lt.gui.lightning_assembly.configure_output"),
                () -> {
                    var next = new LightningAssemblyOutputConfigScreen(this);
                    releaseUpgradePanels();
                    releaseBackground();
                    switchToScreen(next);
                });
        addToLeftToolbar(this.configureOutputButton);

        widgets.add("lightningStatus", MachineGauges.statusIcon(() -> List.of(
                LightningStatusLines.title(),
                LightningStatusLines.status(menu.isWorking()),
                LightningStatusLines.progress(menu.getProgress()),
                LightningStatusLines.energy(menu.getStoredEnergy(), menu.getEnergyCapacity()),
                LightningStatusLines.highVoltage(menu.getHighVoltageAvailable()),
                LightningStatusLines.extremeHighVoltage(menu.getExtremeHighVoltageAvailable()))));
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

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();

        this.configureOutputButton.setVisibility(menu.isAutoExportEnabled());
    }

}

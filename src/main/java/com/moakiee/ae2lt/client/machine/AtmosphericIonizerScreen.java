package com.moakiee.ae2lt.client.machine;

import com.moakiee.ae2lt.client.ldlib.LdlibAeScreen;
import com.moakiee.ae2lt.client.frequency.FrequencyBindingClient;

import java.util.List;

import appeng.client.gui.style.ScreenStyle;

import com.moakiee.ae2lt.client.ldlib.MachineGauges;
import com.moakiee.ae2lt.client.gui.LightningStatusLines;
import com.moakiee.ae2lt.menu.AtmosphericIonizerMenu;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class AtmosphericIonizerScreen extends LdlibAeScreen<AtmosphericIonizerMenu> {

    public AtmosphericIonizerScreen(
            AtmosphericIonizerMenu menu,
            Inventory playerInventory,
            Component title,
            ScreenStyle style) {
        super(menu, playerInventory, title, style);

        addToLeftToolbar(FrequencyBindingClient.createToolbarButton(menu));

        widgets.add("energyBar", MachineGauges.energyBar(style.getImage("energyBar"),
                menu::getConsumedEnergy, menu::getTotalEnergyRequired,
                "ae2lt.gui.atmospheric_ionizer.energy.tooltip"));

        widgets.add("lightningStatus", MachineGauges.statusIcon(() -> List.of(
                LightningStatusLines.title(),
                menu.getStatusMessage(),
                LightningStatusLines.progress(menu.getProgress()),
                menu.getTargetWeatherMessage(),
                menu.getEnergyDemandMessage())));
    }
}

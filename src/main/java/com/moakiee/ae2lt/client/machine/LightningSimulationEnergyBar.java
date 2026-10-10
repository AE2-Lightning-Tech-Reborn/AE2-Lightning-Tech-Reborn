package com.moakiee.ae2lt.client.machine;

import appeng.client.gui.style.Blitter;
import com.moakiee.ae2lt.menu.LightningSimulationChamberMenu;

public class LightningSimulationEnergyBar extends OverloadProcessingFactoryEnergyBar {
    public LightningSimulationEnergyBar(LightningSimulationChamberMenu menu, Blitter fill) {
        super(menu::getStoredEnergy, menu::getEnergyCapacity, fill, "ae2lt.gui.lightning_simulation.energy.tooltip");
    }
}

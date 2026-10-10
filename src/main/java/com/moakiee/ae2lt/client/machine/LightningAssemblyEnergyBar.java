package com.moakiee.ae2lt.client.machine;

import appeng.client.gui.style.Blitter;
import com.moakiee.ae2lt.menu.LightningAssemblyChamberMenu;

public class LightningAssemblyEnergyBar extends OverloadProcessingFactoryEnergyBar {
    public LightningAssemblyEnergyBar(LightningAssemblyChamberMenu menu, Blitter fill) {
        super(menu::getStoredEnergy, menu::getEnergyCapacity, fill, "ae2lt.gui.lightning_assembly.energy.tooltip");
    }
}

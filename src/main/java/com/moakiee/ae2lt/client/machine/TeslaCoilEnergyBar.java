package com.moakiee.ae2lt.client.machine;

import appeng.client.gui.style.Blitter;
import com.moakiee.ae2lt.menu.TeslaCoilMenu;

public class TeslaCoilEnergyBar extends OverloadProcessingFactoryEnergyBar {
    public TeslaCoilEnergyBar(TeslaCoilMenu menu, Blitter fill) {
        super(menu::getStoredEnergy, menu::getEnergyCapacity, fill, "ae2lt.gui.tesla_coil.energy.tooltip");
    }
}

package com.moakiee.ae2lt.client.machine;

import appeng.client.gui.style.Blitter;
import com.moakiee.ae2lt.menu.AtmosphericIonizerMenu;

public class AtmosphericIonizerEnergyBar extends OverloadProcessingFactoryEnergyBar {
    public AtmosphericIonizerEnergyBar(AtmosphericIonizerMenu menu, Blitter fill) {
        super(menu::getConsumedEnergy, menu::getTotalEnergyRequired, fill, "ae2lt.gui.atmospheric_ionizer.energy.tooltip");
    }
}

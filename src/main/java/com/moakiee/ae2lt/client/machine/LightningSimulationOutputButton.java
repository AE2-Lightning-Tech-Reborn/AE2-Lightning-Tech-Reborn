package com.moakiee.ae2lt.client.machine;

import net.minecraft.network.chat.Component;

public class LightningSimulationOutputButton extends OverloadProcessingFactoryOutputButton {
    public LightningSimulationOutputButton(Component sideLabel, OnPress onPress) {
        super(sideLabel, onPress, "ae2lt.gui.lightning_simulation.output_side.");
    }
}

package com.moakiee.ae2lt.client.machine;

import com.moakiee.ae2lt.menu.LightningSimulationChamberMenu;
import net.minecraft.network.chat.Component;

public class LightningSimulationOutputConfigScreen
        extends MachineOutputConfigScreen<LightningSimulationChamberMenu, LightningSimulationChamberScreen> {
    public LightningSimulationOutputConfigScreen(LightningSimulationChamberScreen parent) {
        super(parent, Component.translatable("block.ae2lt.lightning_simulation_room"),
                "/screens/lightning_simulation_output_config.json",
                "ae2lt.gui.lightning_simulation.output_side.clear", "ae2lt.gui.lightning_simulation.output_side.");
    }
}

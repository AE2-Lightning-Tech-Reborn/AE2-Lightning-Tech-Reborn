package com.moakiee.ae2lt.client.machine;

import com.moakiee.ae2lt.menu.CrystalCatalyzerMenu;
import net.minecraft.network.chat.Component;

public class CrystalCatalyzerOutputConfigScreen
        extends MachineOutputConfigScreen<CrystalCatalyzerMenu, CrystalCatalyzerScreen> {
    public CrystalCatalyzerOutputConfigScreen(CrystalCatalyzerScreen parent) {
        super(parent, Component.translatable("block.ae2lt.crystal_catalyzer"),
                "/screens/crystal_catalyzer_output_config.json",
                "ae2lt.gui.crystal_catalyzer.output_side.clear", "ae2lt.gui.lightning_simulation.output_side.");
    }
}

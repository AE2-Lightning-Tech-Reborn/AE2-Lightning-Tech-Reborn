package com.moakiee.ae2lt.client.machine;

import com.moakiee.ae2lt.menu.LightningAssemblyChamberMenu;
import net.minecraft.network.chat.Component;

public class LightningAssemblyOutputConfigScreen
        extends MachineOutputConfigScreen<LightningAssemblyChamberMenu, LightningAssemblyChamberScreen> {
    public LightningAssemblyOutputConfigScreen(LightningAssemblyChamberScreen parent) {
        super(parent, Component.translatable("block.ae2lt.lightning_assembly_chamber"),
                "/screens/lightning_assembly_output_config.json",
                "ae2lt.gui.lightning_assembly.output_side.clear", "ae2lt.gui.lightning_simulation.output_side.");
    }
}

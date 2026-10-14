package com.moakiee.ae2lt.integration.mest.client;

import com.lhy.mest.client.MESTScreen;
import com.lhy.mest.client.dock.PanelSideBar;
import com.moakiee.ae2lt.client.frequency.FrequencyBindingClient;
import com.moakiee.ae2lt.client.widgets.TextureToggleButton;
import com.moakiee.ae2lt.item.OverloadedFrequencyCardItem;

/** Reborn moved these client APIs; MEST 0.0.3.0 still links their former package. */
public final class MestFrequencyButtons {
    private final TextureToggleButton binding = FrequencyBindingClient.createCardToolbarButton();
    private final TextureToggleButton automatic = FrequencyBindingClient.createCardAutoConnectToolbarButton();
    public MestFrequencyButtons(PanelSideBar bar) {
        binding.visible = automatic.visible = false;
        bar.add(binding); bar.add(automatic);
    }
    public void update(MESTScreen screen) {
        binding.visible = automatic.visible = false;
        var upgrades = screen.getHost().getUpgrades();
        for (int i = 0; i < upgrades.size(); i++) {
            var stack = upgrades.getStackInSlot(i);
            if (stack.getItem() instanceof OverloadedFrequencyCardItem) {
                binding.visible = automatic.visible = true;
                automatic.setState(OverloadedFrequencyCardItem.getData(stack).autoConnect());
                break;
            }
        }
    }
}

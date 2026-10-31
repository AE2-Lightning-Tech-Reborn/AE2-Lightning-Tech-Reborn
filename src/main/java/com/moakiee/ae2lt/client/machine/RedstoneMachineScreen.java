package com.moakiee.ae2lt.client.machine;

import java.util.List;

import appeng.api.config.RedstoneMode;
import appeng.api.config.Settings;
import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.SettingToggleButton;
import appeng.client.gui.widgets.UpgradesPanel;
import appeng.core.definitions.AEItems;
import appeng.core.localization.GuiText;
import appeng.menu.SlotSemantics;
import com.moakiee.ae2lt.menu.RedstoneMachineMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public abstract class RedstoneMachineScreen<M extends RedstoneMachineMenu<?>> extends AEBaseScreen<M> {
    private final SettingToggleButton<RedstoneMode> redstoneButton;

    protected RedstoneMachineScreen(M menu, Inventory inventory, Component title, ScreenStyle style) {
        super(menu, inventory, title, style);
        if (!menu.getSlots(SlotSemantics.UPGRADE).isEmpty()) {
            widgets.add("upgrades", new UpgradesPanel(menu.getSlots(SlotSemantics.UPGRADE),
                    () -> List.of(GuiText.CompatibleUpgrades.text(), AEItems.REDSTONE_CARD.stack().getHoverName())));
        }
        redstoneButton = new SettingToggleButton<>(Settings.REDSTONE_CONTROLLED, RedstoneMode.IGNORE,
                mode -> mode != RedstoneMode.SIGNAL_PULSE,
                (button, backwards) -> menu.clientCycleRedstoneMode(backwards));
        addToLeftToolbar(redstoneButton);
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();
        redstoneButton.set(menu.redstoneMode);
        redstoneButton.setVisibility(menu.redstoneCardInstalled);
    }
}

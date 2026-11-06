package com.moakiee.ae2lt.machine.common;

import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.IUpgradeableObject;

public interface RedstoneControlledMachine extends IUpgradeableObject {
    MachineRedstoneControl getRedstoneControl();

    @Override
    default IUpgradeInventory getUpgrades() {
        return getRedstoneControl().getUpgrades();
    }
}

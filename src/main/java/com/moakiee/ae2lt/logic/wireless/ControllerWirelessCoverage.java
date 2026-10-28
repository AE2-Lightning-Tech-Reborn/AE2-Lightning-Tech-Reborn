package com.moakiee.ae2lt.logic.wireless;

import appeng.api.networking.IGrid;
import appeng.core.AEConfig;
import com.moakiee.ae2lt.blockentity.AdvancedWirelessOverloadedControllerBlockEntity;
import com.moakiee.ae2lt.blockentity.WirelessOverloadedControllerBlockEntity;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/** Coverage belongs to the linked ME grid, independently of transmitter frequencies. */
public final class ControllerWirelessCoverage {
    private ControllerWirelessCoverage() {}

    @Nullable
    public static WirelessOverloadedControllerBlockEntity find(IGrid grid, Level playerLevel) {
        if (playerLevel.isClientSide()) return null;
        // AE2 indexes machines by concrete class. Query both tiers, never the whole grid/world.
        for (var controller : grid.getMachines(AdvancedWirelessOverloadedControllerBlockEntity.class)) {
            if (controller.isActive()) return controller;
        }
        for (var controller : grid.getMachines(WirelessOverloadedControllerBlockEntity.class)) {
            if (controller.getLevel() == playerLevel && controller.isActive()) return controller;
        }
        return null;
    }

    /** A finite equivalent distance keeps AE2's configured battery drain without charging infinity. */
    public static double powerDistance() {
        return Math.max(1.0, AEConfig.instance().wireless_getMaxRange(0));
    }
}

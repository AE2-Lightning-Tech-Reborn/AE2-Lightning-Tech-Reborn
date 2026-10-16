package com.moakiee.ae2lt.logic;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class OverloadedInterfaceEnergyRoutingTest {
    @Test
    void wirelessModeDoesNotUseAnAdjacentOutputDirection() {
        assertFalse(OverloadedInterfaceTickDecider.hasServerEnergyWork(true, false, true, true, true));
        assertTrue(OverloadedInterfaceTickDecider.hasServerEnergyWork(true, true, false, true, true));
    }

    @Test
    void normalModeIgnoresWirelessConnections() {
        assertFalse(OverloadedInterfaceTickDecider.hasServerEnergyWork(false, true, false, true, true));
        assertTrue(OverloadedInterfaceTickDecider.hasServerEnergyWork(false, false, true, true, true));
    }

    @Test
    void energyTransferRequiresTheKeyAndInductionCard() {
        for (boolean wireless : new boolean[] {false, true}) {
            assertFalse(OverloadedInterfaceTickDecider.hasServerEnergyWork(wireless, true, true, false, true));
            assertFalse(OverloadedInterfaceTickDecider.hasServerEnergyWork(wireless, true, true, true, false));
        }
    }
}

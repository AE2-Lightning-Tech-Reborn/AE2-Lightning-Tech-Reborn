package com.moakiee.ae2lt.menu;

import com.moakiee.ae2lt.compat.orientation.RelativeSide;
import appeng.blockentity.AEBaseBlockEntity;

/** Shared controls for the existing six-face machine output screen. */
public interface MachineOutputConfigMenu {
    AEBaseBlockEntity getHost();
    boolean isOutputSideEnabled(RelativeSide side);
    void clientToggleOutputSide(RelativeSide side);
    void clientClearOutputSides();
}

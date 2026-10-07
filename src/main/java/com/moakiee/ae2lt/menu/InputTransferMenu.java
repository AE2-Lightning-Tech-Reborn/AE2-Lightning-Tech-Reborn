package com.moakiee.ae2lt.menu;

import com.moakiee.ae2lt.machine.common.ManualInputTransfer;

public interface InputTransferMenu {
    void clientTransferInputs();
    int getInputTransferRevision();
    ManualInputTransfer.Result getInputTransferResult();
}

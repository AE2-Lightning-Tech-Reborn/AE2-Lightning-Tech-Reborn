package com.moakiee.ae2lt.crafting.big;

import appeng.api.stacks.AEKey;

import java.math.BigInteger;

public interface BigConfirmMenu {
    boolean ae2lt$isBig();

    String ae2lt$failure();

    void ae2lt$planBig(AEKey key, BigInteger amount);
}

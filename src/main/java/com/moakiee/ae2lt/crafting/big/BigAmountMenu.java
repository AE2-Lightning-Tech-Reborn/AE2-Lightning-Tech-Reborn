package com.moakiee.ae2lt.crafting.big;

import java.math.BigInteger;

public interface BigAmountMenu {
    boolean ae2lt$bigAvailable();

    String ae2lt$initialAmount();

    void ae2lt$initialAmount(BigInteger amount);

    void ae2lt$confirm(BigInteger amount, boolean missing, boolean autoStart);
}

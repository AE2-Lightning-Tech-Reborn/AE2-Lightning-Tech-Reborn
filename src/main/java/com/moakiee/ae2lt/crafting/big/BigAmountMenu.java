package com.moakiee.ae2lt.crafting.big;

import java.math.BigInteger;

public interface BigAmountMenu extends com.moakiee.thunderbolt.api.crafting.CraftingAmountMenu {
    boolean ae2lt$longAvailable();

    boolean ae2lt$bigAvailable();

    String ae2lt$initialAmount();

    void ae2lt$initialAmount(BigInteger amount);

    @Override
    default void thunderbolt$setInitialAmount(BigInteger amount) {
        ae2lt$initialAmount(amount);
    }

    void ae2lt$confirm(BigInteger amount, boolean missing, boolean autoStart);
}

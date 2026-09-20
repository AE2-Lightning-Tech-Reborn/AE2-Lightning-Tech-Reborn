package com.moakiee.ae2lt.crafting.big;

import java.math.BigInteger;

public interface BigStatusEntry {
    record Amounts(BigInteger stored, BigInteger active, BigInteger pending) {}

    Amounts ae2lt$amounts();

    void ae2lt$amounts(Amounts amounts);
}

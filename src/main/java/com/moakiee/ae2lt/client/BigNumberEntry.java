package com.moakiee.ae2lt.client;

import java.math.BigInteger;
import java.util.Optional;

public interface BigNumberEntry {
    void ae2lt$enableBig();

    Optional<BigInteger> ae2lt$getBig();

    void ae2lt$setBig(BigInteger amount);
}

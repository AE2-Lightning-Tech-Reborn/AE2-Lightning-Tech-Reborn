package com.moakiee.ae2lt.crafting.big;

import appeng.api.stacks.AEKey;
import java.math.BigInteger;
import java.util.Map;
import org.jetbrains.annotations.Nullable;

/** Exact display quantities attached to the native terminal container. */
public interface BigTerminalMenu {
    @Nullable BigInteger ae2lt$getBigStock(AEKey key);
    void ae2lt$applyBigStock(Map<AEKey, BigInteger> changes);
}

package com.moakiee.ae2lt.compat;
import appeng.api.stacks.AEKeyType;
public interface LegacyTimeTracker {
    void ae2lt$decrementItems(long amount, AEKeyType keyType);
    void ae2lt$addMaxItems(long amount, AEKeyType keyType);
}

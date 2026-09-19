package com.moakiee.ae2lt.integration.gtlcore;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.moakiee.ae2lt.crafting.algorithm.ExclusiveCraftingLockSource;

/** Reflection-only lookup of a formed Transfinite controller lock. */
public final class TransfiniteExclusivePlanningAccess {
    private TransfiniteExclusivePlanningAccess() {
    }

    @Nullable
    public static ExclusiveCraftingLockSource formedController(@Nullable Object interfaceMachine) {
        if (interfaceMachine == null) {
            return null;
        }
        try {
            Object controllers = interfaceMachine.getClass()
                    .getMethod("getTransfiniteControllers")
                    .invoke(interfaceMachine);
            if (!(controllers instanceof List<?> list)) {
                return null;
            }
            for (Object controller : list) {
                if (controller instanceof ExclusiveCraftingLockSource lock
                        && lock.ae2lt$isExclusiveLockActive()) {
                    return lock;
                }
            }
        } catch (Throwable ignored) {
            return null;
        }
        return null;
    }
}

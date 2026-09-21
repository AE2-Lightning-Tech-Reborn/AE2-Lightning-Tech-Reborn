package com.moakiee.ae2lt.crafting.big;

import appeng.api.stacks.GenericStack;

import com.google.common.collect.MapMaker;

import java.math.BigInteger;
import java.util.concurrent.ConcurrentMap;

/** Identity keys keep simultaneous jobs for the same item independent. */
public final class BigDisplayAmounts {
    private static final ConcurrentMap<GenericStack, BigInteger> AMOUNTS =
            new MapMaker().weakKeys().makeMap();

    private BigDisplayAmounts() {}

    public static GenericStack attach(GenericStack stack, BigInteger n) {
        if (stack != null && n != null) AMOUNTS.put(stack, n);
        return stack;
    }

    public static BigInteger get(GenericStack stack) {
        return stack == null ? null : AMOUNTS.get(stack);
    }
}

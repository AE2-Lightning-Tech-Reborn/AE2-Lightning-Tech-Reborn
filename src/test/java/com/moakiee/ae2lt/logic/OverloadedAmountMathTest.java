package com.moakiee.ae2lt.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;

import it.unimi.dsi.fastutil.objects.Object2LongLinkedOpenHashMap;

import org.junit.jupiter.api.Test;

class OverloadedAmountMathTest {

    @Test
    void overlappingFuzzyConfigurationsDoNotDuplicatePhysicalStock() {
        var caps = new Object2LongLinkedOpenHashMap<String>();
        var amounts = new Object2LongLinkedOpenHashMap<String>();

        OverloadedAmountMath.mergeSharedExposure(caps, amounts, "variant", 64, 64);
        OverloadedAmountMath.mergeSharedExposure(caps, amounts, "variant", 64, 64);

        assertEquals(128L, caps.getLong("variant"));
        assertEquals(64L, amounts.getLong("variant"));
        assertEquals(64L, OverloadedAmountMath.capVisibleAmount(
                amounts.getLong("variant"), caps.getLong("variant")));
    }

    @Test
    void sharedCapacityAdditionSaturates() {
        assertEquals(
                Long.MAX_VALUE,
                OverloadedAmountMath.saturatingAdd(Long.MAX_VALUE - 4, 8));
    }
}

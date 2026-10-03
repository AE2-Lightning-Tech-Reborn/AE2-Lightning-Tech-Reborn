package com.moakiee.ae2lt.logic.railgun;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;
import com.moakiee.ae2lt.item.railgun.RailgunChargeTier;
import com.moakiee.ae2lt.item.railgun.RailgunExecutionMode;

class RailgunPercentageDamageTest {
    @Test
    void largeHealthTargetsScaleButInvalidValuesCannotPoisonDamage() {
        assertEquals(2_000, RailgunPercentageDamage.bonus(10_000, .20));
        assertEquals(20_000, RailgunPercentageDamage.bonus(100_000, .20));
        assertEquals(0, RailgunPercentageDamage.bonus(Double.NaN, .20));
        assertEquals(0, RailgunPercentageDamage.bonus(Double.POSITIVE_INFINITY, .20));
        assertEquals(0, RailgunPercentageDamage.bonus(100, Double.NaN));
        assertEquals(0, RailgunPercentageDamage.bonus(-100, .20));
        assertEquals(100, RailgunPercentageDamage.bonus(100, 2));
    }

    @Test
    void onlyChargedOverloadPercentageModeCanSupplyBonus() {
        for (var tier : RailgunChargeTier.values()) {
            for (var mode : RailgunExecutionMode.values()) {
                assertEquals(tier != RailgunChargeTier.HV && mode == RailgunExecutionMode.PERCENTAGE,
                        RailgunPercentageDamage.eligible(tier, mode, true, false, false));
                assertFalse(RailgunPercentageDamage.eligible(tier, mode, false, false, false));
                assertFalse(RailgunPercentageDamage.eligible(tier, mode, true, true, false));
                assertFalse(RailgunPercentageDamage.eligible(tier, mode, true, false, true));
            }
        }
    }
}

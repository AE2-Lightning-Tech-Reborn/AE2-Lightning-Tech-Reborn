package com.moakiee.ae2lt.logic.railgun;

import com.moakiee.ae2lt.config.AE2LTCommonConfig;
import com.moakiee.ae2lt.item.railgun.RailgunModuleEntries;
import com.moakiee.ae2lt.item.railgun.RailgunSettings;
import com.moakiee.ae2lt.me.key.LightningKey;

/** One snapshot supplies both beam payment and damage; execution mode is deliberately absent. */
public record RailgunBeamProfile(boolean ehv, double damage, long feCost, long lightningCost, int costInterval) {
    public static RailgunBeamProfile resolve(RailgunModuleEntries modules, RailgunSettings settings) {
        if (modules.hasEhvBeam() && settings.ehvBeamEnabled()) {
            return new RailgunBeamProfile(true, AE2LTCommonConfig.railgunEhvBeamDamagePerSettle(),
                    AE2LTCommonConfig.railgunEhvBeamFeCostPerSettle(), AE2LTCommonConfig.railgunEhvBeamCostPerSettle(), 1);
        }
        return new RailgunBeamProfile(false, AE2LTCommonConfig.railgunBeamDamagePerSettle(),
                AE2LTCommonConfig.railgunBeamFeCostPerSettle(), 1L, AE2LTCommonConfig.railgunBeamHvCostInterval());
    }

    public long lightningForSettle(int settleCount) {
        return settleCount % Math.max(1, costInterval) == 0 ? lightningCost : 0;
    }

    public LightningKey lightningKey() {
        return ehv ? LightningKey.EXTREME_HIGH_VOLTAGE : LightningKey.HIGH_VOLTAGE;
    }

    public String failureKey() {
        return ehv ? "ae2lt.railgun.fail.no_ehv" : "ae2lt.railgun.fail.no_hv";
    }
}

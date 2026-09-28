package com.moakiee.ae2lt.celestweave;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

import com.moakiee.ae2lt.celestweave.module.CelestweaveArmorSubmodule;
import com.moakiee.ae2lt.celestweave.module.OverloadProtectionSubmodule;
import com.moakiee.ae2lt.celestweave.module.ResistanceSubmodule;

/**
 * Bills only increases to each resource's high-water mark in a fixed 20-tick window.
 * Overload shielding and last-stand protection share the same paid state and caps.
 */
public final class ShieldChargeWindow {
    public static final int WINDOW_TICKS = 20;
    // New keys deliberately do not reuse the old phase window's cheaper EHV credit.
    private static final String TAG_UNTIL = "ProtectionChargeUntil";
    private static final String TAG_FE = "ProtectionChargeFe";
    private static final String TAG_EHV = "ProtectionChargeEhv";

    public enum Profile {
        PHASE("phase_shield", 1_024D, 20_480_000L, 2_048L),
        OVERLOAD("overload_protection", Float.MAX_VALUE, 20_000_000_000L, 16_384L);

        private final String stage;
        private final double maxDamage;
        private final long maxFe;
        private final long maxEhv;

        Profile(String stage, double maxDamage, long maxFe, long maxEhv) {
            this.stage = stage;
            this.maxDamage = maxDamage;
            this.maxFe = maxFe;
            this.maxEhv = maxEhv;
        }

        private CelestweaveArmorSubmodule submodule() {
            return this == PHASE ? ResistanceSubmodule.T2 : OverloadProtectionSubmodule.INSTANCE;
        }

        public static Profile forStage(String stage) {
            for (Profile profile : values()) {
                if (profile.stage.equals(stage)) {
                    return profile;
                }
            }
            throw new IllegalArgumentException("No paid shield profile for " + stage);
        }
    }

    private ShieldChargeWindow() {
    }

    public static Quote quote(ItemStack armor, Profile profile, long gameTime, float preventedDamage) {
        return quote(readState(armor, profile), profile, gameTime, preventedDamage);
    }

    static Quote quote(State state, Profile profile, long gameTime, double preventedDamage) {
        double damage = Double.isNaN(preventedDamage) ? 0D
                : Math.min(profile.maxDamage, Math.max(0D, preventedDamage));
        return quoteCosts(state, profile, gameTime,
                totalCost(damage, ArmorOverloadRules.PHASE_SHIELD_ACTIVE_COST_FE_PER_DAMAGE, profile.maxFe),
                totalCost(damage, ArmorOverloadRules.PHASE_SHIELD_COST_EHV_PER_DAMAGE, profile.maxEhv));
    }

    public static Quote quoteLastStand(ItemStack armor, long gameTime, long feCost, long ehvCost) {
        return quoteCosts(readState(armor, Profile.OVERLOAD), Profile.OVERLOAD, gameTime, feCost, ehvCost);
    }

    static Quote quoteCosts(State state, Profile profile, long gameTime, long feCost, long ehvCost) {
        State safe = state == null ? State.EMPTY : state;
        boolean active = safe.windowUntil() > gameTime;
        long previousFe = active ? Math.min(profile.maxFe, safe.paidFe()) : 0L;
        long previousEhv = active ? Math.min(profile.maxEhv, safe.paidEhv()) : 0L;
        long nextFe = Math.max(previousFe, Math.min(profile.maxFe, Math.max(0L, feCost)));
        long nextEhv = Math.max(previousEhv, Math.min(profile.maxEhv, Math.max(0L, ehvCost)));
        long until = active ? safe.windowUntil()
                : gameTime > Long.MAX_VALUE - WINDOW_TICKS ? Long.MAX_VALUE : gameTime + WINDOW_TICKS;
        return new Quote(profile, nextFe - previousFe, nextEhv - previousEhv,
                new State(until, nextFe, nextEhv));
    }

    public static void record(ItemStack armor, Quote quote) {
        CompoundTag data = CelestweaveArmorState.getSubmoduleData(armor, quote.profile().submodule());
        data.putLong(TAG_UNTIL, quote.nextState().windowUntil());
        data.putLong(TAG_FE, quote.nextState().paidFe());
        data.putLong(TAG_EHV, quote.nextState().paidEhv());
        CelestweaveArmorState.setSubmoduleData(armor, quote.profile().submodule(), data);
    }

    public static boolean remainsActiveWithoutPassivePower(ItemStack armor, String submoduleId, long gameTime) {
        if ("multidimensional_protection".equals(submoduleId)) {
            return true;
        }
        for (Profile profile : Profile.values()) {
            if (profile.stage.equals(submoduleId)) {
                State state = readState(armor, profile);
                return state.windowUntil() > gameTime && (state.paidFe() > 0 || state.paidEhv() > 0);
            }
        }
        return false;
    }

    private static State readState(ItemStack armor, Profile profile) {
        CompoundTag data = CelestweaveArmorState.getSubmoduleData(armor, profile.submodule());
        return data.contains(TAG_UNTIL)
                ? new State(data.getLong(TAG_UNTIL), data.getLong(TAG_FE), data.getLong(TAG_EHV))
                : State.EMPTY;
    }

    private static long totalCost(double damage, long rate, long cap) {
        double cost = damage * rate;
        return cost >= cap ? cap : Math.max(0L, (long) Math.ceil(cost));
    }

    record State(long windowUntil, long paidFe, long paidEhv) {
        static final State EMPTY = new State(Long.MIN_VALUE, 0L, 0L);

        State {
            paidFe = Math.max(0L, paidFe);
            paidEhv = Math.max(0L, paidEhv);
        }
    }

    public record Quote(Profile profile, long feCost, long ehvCost, State nextState) {
    }
}

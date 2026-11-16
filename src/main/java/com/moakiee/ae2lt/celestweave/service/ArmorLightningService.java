package com.moakiee.ae2lt.celestweave.service;

import net.minecraft.world.entity.LivingEntity;
import com.moakiee.ae2lt.celestweave.ArmorWearer;
import net.minecraft.world.item.ItemStack;

import appeng.api.config.Actionable;

import com.moakiee.ae2lt.device.energy.LightningCompensationPolicy;
import com.moakiee.ae2lt.device.network.ArmorNetworkBinding;
import com.moakiee.ae2lt.me.key.LightningKey;

public final class ArmorLightningService {
    private ArmorLightningService() {
    }

    public static boolean hasCost(LivingEntity player, ItemStack armor, LightningKey key, long amount) {
        if (amount <= 0L) {
            return true;
        }
        if (key == null) {
            return false;
        }
        return hasCost(
                player,
                armor,
                key == LightningKey.EXTREME_HIGH_VOLTAGE
                        ? LightningCost.ehv(amount)
                        : LightningCost.hv(amount));
    }

    public static boolean hasCost(LivingEntity player, ItemStack armor, LightningCost cost) {
        if (cost.isEmpty()) {
            return true;
        }
        return plan(player, armor, cost).canPay();
    }

    public static boolean consume(LivingEntity player, ItemStack armor, LightningKey key, long amount) {
        if (amount <= 0L) {
            return true;
        }
        if (key == null) {
            return false;
        }
        return consume(
                player,
                armor,
                key == LightningKey.EXTREME_HIGH_VOLTAGE
                        ? LightningCost.ehv(amount)
                        : LightningCost.hv(amount));
    }

    public static boolean consume(LivingEntity player, ItemStack armor, LightningCost cost) {
        if (cost.isEmpty()) {
            return true;
        }
        var plan = plan(player, armor, cost);
        if (!plan.canPay()) {
            return false;
        }
        long gotEhv = extract(
                player,
                armor,
                LightningKey.EXTREME_HIGH_VOLTAGE,
                plan.extremeHighVoltageToConsume(),
                Actionable.MODULATE);
        if (gotEhv < plan.extremeHighVoltageToConsume()) {
            refund(player, armor, LightningKey.EXTREME_HIGH_VOLTAGE, gotEhv);
            return false;
        }
        long gotHv = extract(
                player,
                armor,
                LightningKey.HIGH_VOLTAGE,
                plan.highVoltageToConsume(),
                Actionable.MODULATE);
        if (gotHv < plan.highVoltageToConsume()) {
            refund(player, armor, LightningKey.HIGH_VOLTAGE, gotHv);
            refund(player, armor, LightningKey.EXTREME_HIGH_VOLTAGE, gotEhv);
            return false;
        }
        return true;
    }

    private static LightningCompensationPolicy.Plan plan(
            LivingEntity player,
            ItemStack armor,
            LightningCost cost) {
        long availableEhv = cost.extremeHighVoltage() <= 0L
                ? 0L
                : extract(
                        player,
                        armor,
                        LightningKey.EXTREME_HIGH_VOLTAGE,
                        Long.MAX_VALUE,
                        Actionable.SIMULATE);
        boolean needsCompensation = availableEhv < cost.extremeHighVoltage();
        int compensationRatio = !needsCompensation || player == null
                ? 0
                : LightningCompensationPolicy.bestRatio(
                        ArmorCapabilityCollector.collectPerInstalledStack(player).stream()
                                .map(ArmorCapabilityCollector.ActiveCapability::capability)
                                .toList());
        boolean needsHighVoltage = cost.highVoltage() > 0L
                || (needsCompensation && compensationRatio > 0);
        long availableHv = needsHighVoltage
                ? extract(
                        player,
                        armor,
                        LightningKey.HIGH_VOLTAGE,
                        Long.MAX_VALUE,
                        Actionable.SIMULATE)
                : 0L;
        return LightningCompensationPolicy.plan(
                cost.highVoltage(),
                cost.extremeHighVoltage(),
                availableHv,
                availableEhv,
                compensationRatio);
    }

    private static long extract(LivingEntity player, ItemStack armor, LightningKey key, long amount, Actionable action) {
        if (!ArmorWearer.canRun(player) || armor == null || armor.isEmpty() || key == null || amount <= 0L) {
            return 0L;
        }
        var bound = ArmorNetworkBinding.INSTANCE.resolve(armor, player);
        if (!bound.success() || bound.grid() == null) {
            return 0L;
        }
        return bound.grid().getStorageService().getInventory().extract(
                key,
                amount,
                action,
                ArmorWearer.actionSource(player));
    }

    private static void refund(LivingEntity player, ItemStack armor, LightningKey key, long amount) {
        if (!ArmorWearer.canRun(player) || armor == null || armor.isEmpty() || key == null || amount <= 0L) {
            return;
        }
        var bound = ArmorNetworkBinding.INSTANCE.resolve(armor, player);
        if (!bound.success() || bound.grid() == null) {
            return;
        }
        bound.grid().getStorageService().getInventory().insert(
                key,
                amount,
                Actionable.MODULATE,
                ArmorWearer.actionSource(player));
    }

    public record LightningCost(long highVoltage, long extremeHighVoltage) {
        public static final LightningCost NONE = new LightningCost(0L, 0L);

        public LightningCost {
            highVoltage = Math.max(0L, highVoltage);
            extremeHighVoltage = Math.max(0L, extremeHighVoltage);
        }

        public static LightningCost hv(long amount) {
            return new LightningCost(amount, 0L);
        }

        public static LightningCost ehv(long amount) {
            return new LightningCost(0L, amount);
        }

        public LightningCost plus(LightningCost other) {
            if (other == null || other.isEmpty()) {
                return this;
            }
            return new LightningCost(
                    saturatingAdd(highVoltage, other.highVoltage),
                    saturatingAdd(extremeHighVoltage, other.extremeHighVoltage));
        }

        public LightningCost times(long multiplier) {
            if (multiplier <= 0L || isEmpty()) {
                return NONE;
            }
            return new LightningCost(
                    saturatingMultiply(highVoltage, multiplier),
                    saturatingMultiply(extremeHighVoltage, multiplier));
        }

        public boolean isEmpty() {
            return highVoltage <= 0L && extremeHighVoltage <= 0L;
        }

        private static long saturatingAdd(long left, long right) {
            if (left > Long.MAX_VALUE - right) {
                return Long.MAX_VALUE;
            }
            return left + right;
        }

        private static long saturatingMultiply(long value, long multiplier) {
            if (value <= 0L || multiplier <= 0L) {
                return 0L;
            }
            if (value > Long.MAX_VALUE / multiplier) {
                return Long.MAX_VALUE;
            }
            return value * multiplier;
        }
    }
}

package com.moakiee.ae2lt.celestweave.module;

import org.jetbrains.annotations.Nullable;
import com.moakiee.ae2lt.config.AE2LTCommonConfig;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;

public final class WaterBreathingSubmodule extends AbstractCelestweaveArmorSubmodule {

    public static final WaterBreathingSubmodule INSTANCE = new WaterBreathingSubmodule();

    // Short duration with periodic refresh so effect recovers automatically after
    // death/respawn or being cleared by milk / other mods. Duration kept above
    // refresh interval with enough headroom.
    private static final int EFFECT_DURATION_TICKS = 300;
    private static final int REFRESH_INTERVAL_TICKS = 60;

    private WaterBreathingSubmodule() {}

    @Override
    public String id() {
        return "water_breathing";
    }

    @Override
    public String nameKey() {
        return "ae2lt.celestweave.feature.water_breathing.name";
    }

    @Override
    public String descriptionKey() {
        return "ae2lt.celestweave.feature.water_breathing.desc";
    }

    @Override
    public boolean defaultEnabled() {
        return true;
    }

    @Override
    public int getMaxInstallAmount() {
        return 1;
    }

    @Override
    public void onActivated(@Nullable LivingEntity player, Dist dist, ItemStack armor) {
        if (player != null && dist == Dist.DEDICATED_SERVER) {
            applyEffect(player);
        }
    }

    @Override
    public void onDeactivated(@Nullable LivingEntity player, Dist dist, ItemStack armor) {
        if (player != null && dist == Dist.DEDICATED_SERVER) {
            player.removeEffect(MobEffects.WATER_BREATHING);
        }
    }

    @Override
    public int tickActive(@Nullable LivingEntity player, Dist dist, ItemStack armor) {
        if (player != null && dist == Dist.DEDICATED_SERVER) {
            if (player.tickCount % REFRESH_INTERVAL_TICKS == 0) applyEffect(player);
            int period = AE2LTCommonConfig.lifeSupportHealingIntervalTicks();
            if (period > 0 && player.tickCount % period == 0 && player.getHealth() < player.getMaxHealth()) {
                player.heal(1.0F);
            }
        }
        return 0;
    }

    @Override
    public void onActivated(@Nullable Player player, Dist dist, ItemStack armor) {
        onActivated((LivingEntity) player, dist, armor);
    }

    @Override
    public void onDeactivated(@Nullable Player player, Dist dist, ItemStack armor) {
        onDeactivated((LivingEntity) player, dist, armor);
    }

    @Override
    public int tickActive(@Nullable Player player, Dist dist, ItemStack armor) {
        return tickActive((LivingEntity) player, dist, armor);
    }

    private static void applyEffect(LivingEntity player) {
        player.addEffect(new MobEffectInstance(
                MobEffects.WATER_BREATHING,
                EFFECT_DURATION_TICKS,
                0,
                false,
                false,
                true));
    }
}
